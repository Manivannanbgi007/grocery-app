import { expect, test, type BrowserContext, type Locator, type Page } from '@playwright/test'
import { apiLogin, emptyCart, fillCart, freePartner, GATEWAY, logIn, logInAsPartner, PARTNER, skipSalePopup, VENDOR1 } from './helpers'

/*
 * Smoke flow 21: live delivery tracking (docs/delivery-tracking.md) against the real backend, with two browsers at once:
 * Arjun's phone (delivery1; Playwright plays the GPS) and Priya's tracking page (customer1). A position travels
 * phone -> PUT /location -> delivery-service (Redis) -> the live stream (SSE) -> the customer's map.
 * The flow delivers a real order of customer1 (2 x bananas, cash on delivery), like the delivery flow.
 * Without a routing service (TRACKING_ROUTING_URL, off locally) the page shows the straight-line distance, not an arrival time.
 */

interface Point {
  latitude: number
  longitude: number
}

/** A point `north` / `east` degrees away (0.001 degree is about 110 m here). */
const near = (point: Point, north: number, east: number) => ({ latitude: point.latitude + north, longitude: point.longitude + east })

/**
 * The public OpenStreetMap tile servers are not for automated tests (tile usage policy): the map loads no tiles here,
 * only its markers and lines are checked.
 */
async function withoutMapTiles(context: BrowserContext) {
  await context.route(/tile\.openstreetmap\.org/, (route) => route.abort())
}

/** The partner's phone is somewhere else now (the page's GPS watch gets the new position). */
async function partnerMovesTo(context: BrowserContext, point: Point) {
  // The phone sends at most one position every 4 s (TRACKING_CONFIG.sendAtMostEveryMs): wait, so this one is sent
  await new Promise((resolve) => setTimeout(resolve, 4_500))
  await context.setGeolocation(point)
}

/**
 * The markers are inside the visible part of the map. (Leaflet keeps markers that are out of view in the page, where
 * toBeVisible() still finds them.)
 */
async function expectInView(map: Locator, markers: Locator[]) {
  for (const marker of markers) {
    await expect
      .poll(async () => {
        const [area, box] = [await map.boundingBox(), await marker.boundingBox()]
        if (!area || !box) return false
        const [x, y] = [box.x + box.width / 2, box.y + box.height / 2]
        return x > area.x && x < area.x + area.width && y > area.y && y < area.y + area.height
      }, { message: `${await marker.getAttribute('title')} is in view on the map` })
      .toBe(true)
  }
}

/** The line under the headline: the arrival time with a routing service, otherwise the straight-line distance. */
const wayToGo = (page: Page) => page.getByText(/(away from (the store|you) \(straight line\)|^At .+ around|^Arrives around)/)

let orderId: number | undefined

test.beforeEach(async ({ request }) => {
  orderId = undefined
  await emptyCart(request)
  await freePartner(request)
})

test.afterEach(async ({ request }) => {
  if (orderId) await request.post(`${GATEWAY}/api/v1/orders/${orderId}/cancel`, { headers: await apiLogin(request), data: { reason: 'Playwright clean-up' } })
  await freePartner(request)
  await emptyCart(request)
})

test('the customer follows the partner live on the map, from the store to the door', async ({ browser, request }) => {
  test.slow() // two browsers, several services, and the phone's 4 s sending interval

  // customer1 orders from Fresh Mart (cash); Arjun is online, so the delivery is his as soon as Fresh Mart hands it over
  await fillCart(request, [[4, 2]])
  const customer = await apiLogin(request)
  const placed = await request.post(`${GATEWAY}/api/v1/orders`, { headers: customer, data: { addressId: 1, paymentMethod: 'COD', note: 'Playwright tracking test' } })
  expect(placed.ok(), `the order can be placed: ${await placed.text()}`).toBeTruthy()
  orderId = (await placed.json()).data.id as number
  await expect.poll(async () => (await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers: customer })).json()).data.status, { timeout: 15_000 }).toBe('CONFIRMED')
  const partner = await apiLogin(request, PARTNER)
  expect((await request.post(`${GATEWAY}/api/v1/delivery/availability/online`, { headers: partner })).ok()).toBeTruthy()
  const vendor = await apiLogin(request, VENDOR1)
  for (const step of ['accept', 'pack', 'ready-for-pickup']) expect((await request.post(`${GATEWAY}/api/v1/vendor/orders/${orderId}/${step}`, { headers: vendor })).ok()).toBeTruthy()

  const arjunsTask = async () => {
    const active = (await (await request.get(`${GATEWAY}/api/v1/delivery/deliveries?active=true`, { headers: partner })).json()).data.content as { id: number; orderId: number }[]
    return active.find((task) => task.orderId === orderId)?.id ?? null
  }
  await expect.poll(arjunsTask, { timeout: 15_000, message: 'Arjun gets the delivery (is delivery-service running? another partner online?)' }).not.toBeNull()
  const deliveryId = (await arjunsTask())!
  const delivery = (await (await request.get(`${GATEWAY}/api/v1/delivery/deliveries/${deliveryId}`, { headers: partner })).json()).data as { storeLocation: Point | null; customerLocation: Point | null }
  expect(delivery.storeLocation, 'Fresh Mart has map coordinates (seed data)').not.toBeNull()
  expect(delivery.customerLocation, "customer1's home address has map coordinates (seed data)").not.toBeNull()
  const store = delivery.storeLocation!
  const home = delivery.customerLocation!

  // Priya opens the tracking page (desktop)
  const customerContext = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await withoutMapTiles(customerContext)
  const priya = await customerContext.newPage()
  await skipSalePopup(priya)
  await priya.goto(`/login?next=${encodeURIComponent(`/orders/${orderId}/track`)}`)
  await logIn(priya)
  await expect(priya.getByRole('heading', { name: 'Arjun will bring your order' })).toBeVisible()
  await expect(priya.getByText('Live', { exact: true })).toBeVisible()
  await expect(priya.getByRole('link', { name: 'OpenStreetMap' })).toBeVisible()
  const map = priya.getByRole('region', { name: /^Delivery map/ })
  const [storeMarker, homeMarker, partnerMarker] = [priya.getByTitle('Fresh Mart (pickup)'), priya.getByTitle('Your address'), priya.locator('[title^="Arjun Singh"]')]
  await expectInView(map, [storeMarker, homeMarker])

  // Arjun's phone (location allowed, about 1 km north-west of the store) accepts: sharing starts by itself
  const phoneContext = await browser.newContext({ viewport: { width: 390, height: 844 }, permissions: ['geolocation'], geolocation: near(store, 0.0065, -0.0055) })
  await withoutMapTiles(phoneContext)
  const arjun = await phoneContext.newPage()
  await logInAsPartner(arjun)
  await expect(arjun.getByRole('heading', { name: 'Hello, Arjun' })).toBeVisible()
  await arjun.goto(`/delivery/deliveries/${deliveryId}`)
  await arjun.getByRole('button', { name: 'Accept' }).click()
  const livePanel = arjun.getByRole('region', { name: 'Live location' })
  await expect(livePanel.getByText('Sharing', { exact: true })).toBeVisible()
  await expect(livePanel.getByText(/^The customer sees where you are/)).toBeVisible()

  // Priya sees him on the map, on his way to the store
  await expect(priya.getByRole('heading', { name: 'Arjun is on the way to Fresh Mart' })).toBeVisible()
  await expectInView(map, [partnerMarker, storeMarker, homeMarker])
  await expect(priya.getByText(/^Location updated/)).toBeVisible()
  await expect(wayToGo(priya)).toBeVisible()
  const farAway = await wayToGo(priya).innerText()
  await priya.screenshot({ path: test.info().outputPath('1-customer-partner-to-the-store.png') })

  // He comes closer: Priya's page moves along without a reload
  await partnerMovesTo(phoneContext, near(store, 0.002, -0.0015))
  await expect(wayToGo(priya)).not.toHaveText(farAway)

  // At the store: picked up, then on the way to Priya
  await partnerMovesTo(phoneContext, store)
  await arjun.getByRole('button', { name: 'I picked up the order' }).click()
  await expect(priya.getByRole('heading', { name: 'Arjun has picked up your order' })).toBeVisible()
  await arjun.getByRole('button', { name: 'I am on my way' }).click()
  await expect(priya.getByRole('heading', { name: 'Arjun is on the way to you' })).toBeVisible()
  await expect(priya.getByRole('list', { name: 'Order progress' }).locator('[aria-current="step"]')).toHaveText('On the way')
  await partnerMovesTo(phoneContext, { latitude: (store.latitude + home.latitude) / 2, longitude: (store.longitude + home.longitude) / 2 })
  await expect(priya.getByText(/away from you \(straight line\)|^Arrives around/)).toBeVisible()
  await expectInView(map, [partnerMarker, storeMarker, homeMarker])
  await priya.screenshot({ path: test.info().outputPath('2-customer-partner-on-the-way.png') })
  await arjun.screenshot({ path: test.info().outputPath('3-partner-phone-sharing.png'), fullPage: true })
  await priya.setViewportSize({ width: 390, height: 844 })
  await expectInView(map, [partnerMarker, storeMarker, homeMarker])
  await priya.screenshot({ path: test.info().outputPath('4-customer-on-a-phone.png'), fullPage: true })

  // Delivered: sharing stops on the phone, Priya's page shows the end and stops following
  await arjun.getByRole('button', { name: 'Delivered' }).click()
  await arjun.getByRole('alertdialog', { name: /^Collected/ }).getByRole('button', { name: 'Yes, delivered' }).click()
  await expect(arjun.getByText('Delivered. Well done!')).toBeVisible()
  await expect(livePanel).toBeHidden()
  await expect(priya.getByRole('heading', { name: 'Delivered' })).toBeVisible()
  await expect(priya.getByText('Finished', { exact: true })).toBeVisible()
  await expect(partnerMarker).toBeHidden()
  orderId = undefined

  await customerContext.close()
  await phoneContext.close()
})

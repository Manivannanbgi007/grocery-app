import { expect, test } from '@playwright/test'
import { ADMIN, apiLogin, choose, emptyCart, fillCart, freePartner, GATEWAY, logInAsPartner, skipSalePopup, VENDOR1 } from './helpers'

/*
 * Smoke flows 18 to 20: the delivery partner panel (delivery1 = Arjun Singh, zone Indiranagar = Fresh Mart's zone)
 * against the real backend. The main flow delivers a real order of customer1 (cash on delivery, ₹138): it stays as a
 * delivered order and as ₹40 of Arjun's earnings. Arjun is offline again afterwards, and a delivery a failed run left
 * behind is cancelled or brought back to the store, so he is free for the next run.
 */

const PNG_PIXEL = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAAABJRU5ErkJggg==', 'base64')

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

test('a partner goes online, takes the waiting delivery and delivers it with a photo', async ({ page, request }) => {
  // customer1 orders from Fresh Mart (cash), Fresh Mart hands it over: the job waits for a partner
  await fillCart(request, [[1, 3]])
  const customer = await apiLogin(request)
  const placed = await request.post(`${GATEWAY}/api/v1/orders`, { headers: customer, data: { addressId: 1, paymentMethod: 'COD', note: 'Playwright delivery test' } })
  expect(placed.ok(), `the order can be placed: ${await placed.text()}`).toBeTruthy()
  orderId = (await placed.json()).data.id as number
  await expect.poll(async () => (await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers: customer })).json()).data.status, { timeout: 15_000 }).toBe('CONFIRMED')
  const vendor = await apiLogin(request, VENDOR1)
  for (const step of ['accept', 'pack', 'ready-for-pickup']) expect((await request.post(`${GATEWAY}/api/v1/vendor/orders/${orderId}/${step}`, { headers: vendor })).ok()).toBeTruthy()
  const admin = await apiLogin(request, ADMIN)
  const deliveryStatus = async () => {
    const all = (await (await request.get(`${GATEWAY}/api/v1/admin/deliveries?size=50`, { headers: admin })).json()).data.content as { orderId: number; status: string }[]
    return all.find((delivery) => delivery.orderId === orderId)?.status ?? 'none'
  }
  await expect.poll(deliveryStatus, { timeout: 15_000, message: 'is delivery-service running?' }).not.toBe('none')
  test.skip((await deliveryStatus()) !== 'WAITING_FOR_PARTNER', 'Another partner is online and took the delivery')

  await logInAsPartner(page)
  await expect(page.getByRole('heading', { name: 'Hello, Arjun' })).toBeVisible()
  await page.getByRole('button', { name: 'Go online' }).click()
  await expect(page.getByRole('switch', { name: 'Online' })).toBeChecked()
  // Going online offers the waiting job of the zone
  await expect(page.getByRole('heading', { name: 'A new delivery for you' })).toBeVisible()
  await expect(page.getByText(`Order #${orderId}`, { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Accept' }).click()
  await page.getByRole('button', { name: 'I picked up the order' }).click()
  await expect(page.getByRole('heading', { name: 'Take it to the customer' })).toBeVisible()
  await page.getByRole('button', { name: 'I am on my way' }).click()
  await expect(page.getByRole('heading', { name: 'Bring it to the customer' })).toBeVisible()

  // The proof photo is saved, and shown again (it is loaded with the login token)
  await page.getByLabel('Add a photo').setInputFiles({ name: 'door.png', mimeType: 'image/png', buffer: PNG_PIXEL })
  await expect(page.getByText('Photo saved')).toBeVisible()
  await expect(page.getByRole('img', { name: `Proof of delivery of order #${orderId}` })).toBeVisible()

  await page.getByRole('button', { name: 'Delivered' }).click()
  await page.getByRole('alertdialog', { name: 'Collected ₹138?' }).getByRole('button', { name: 'Yes, delivered' }).click()
  await expect(page.getByText('Delivered. Well done!')).toBeVisible()

  // order-service hears it (DeliveryCompleted): the customer's order is delivered
  await expect.poll(async () => (await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers: customer })).json()).data.status, { timeout: 15_000 }).toBe('DELIVERED')
  orderId = undefined
})

test('a new partner signs up and sends the KYC', async ({ page, request }) => {
  const run = `${Date.now()}${Math.floor(Math.random() * 10)}`
  const email = `e2e.rider.${run}@example.com`
  await skipSalePopup(page)
  await page.goto('/register?role=DELIVERY_PARTNER')
  await page.getByLabel('Full name').fill('Playwright Rider')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Mobile number').fill(`7${run.slice(-9)}`)
  await page.getByLabel('Password', { exact: true }).fill('Rider1234')
  await page.getByRole('button', { name: 'Create partner account' }).click()

  // delivery-service makes the profile a moment after the registration
  await expect(page.getByRole('heading', { name: 'Send your KYC' })).toBeVisible({ timeout: 15_000 })
  await page.getByText('Bicycle', { exact: true }).click()
  await page.getByLabel('Vehicle number').fill('KA03PW1234')
  await page.getByLabel('Driving licence number').fill(`KA03${run.slice(-9)}`)
  await choose(page.getByLabel('Zone'), 'Indiranagar, Bengaluru')
  await page.getByRole('button', { name: 'Send for review' }).click()
  await expect(page.getByRole('heading', { name: 'Waiting for review' })).toBeVisible()

  // The admin's KYC queue stays clean: the test KYC is rejected again
  const admin = await apiLogin(request, ADMIN)
  const partners = (await (await request.get(`${GATEWAY}/api/v1/admin/delivery-partners?kycStatus=PENDING&size=100`, { headers: admin })).json()).data.content as { userId: number; fullName: string }[]
  for (const partner of partners.filter((candidate) => candidate.fullName === 'Playwright Rider')) {
    await request.post(`${GATEWAY}/api/v1/admin/delivery-partners/${partner.userId}/reject`, { headers: admin, data: { reason: 'Playwright clean-up' } })
  }
})

test("a partner's history, earnings and profile come from the backend", async ({ page }) => {
  await logInAsPartner(page)
  await expect(page.getByRole('heading', { name: 'Hello, Arjun' })).toBeVisible()
  const menu = page.getByRole('navigation', { name: 'Delivery panel' })

  await menu.getByRole('link', { name: 'History' }).click()
  await expect(page.getByRole('list', { name: 'Finished deliveries' }).getByRole('listitem').first()).toBeVisible()
  await menu.getByRole('link', { name: 'Earnings' }).click()
  await expect(page.getByRole('list', { name: 'Earned per day' }).getByRole('listitem')).toHaveCount(14)
  await menu.getByRole('link', { name: 'Profile' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Arjun Singh' })).toBeVisible()
  await expect(page.getByRole('main').getByText('Indiranagar', { exact: true })).toBeVisible()
})

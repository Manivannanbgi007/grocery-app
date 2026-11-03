import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { apiLogin, choose, emptyCart, fillCart, GATEWAY, skipSalePopup } from './helpers'

/*
 * Smoke flows 7 and 8: the vendor panel.
 * 7: customer1 orders from Fresh Mart (through the API, cash on delivery); vendor1 accepts, packs and hands the order
 *    over in the panel. The order is cancelled again at the end (stock and cart back as before).
 * 8: vendor1 adds a product (a new code every run), gives it stock and a photo, and deletes it again.
 */

const VENDOR = { email: 'vendor1@grocery.com', password: 'Password@123', firstName: 'Ravi' }

/** A 1×1 green PNG, for the photo upload. */
const PNG_PIXEL = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64')

async function logInAsVendor(page: Page, path: string) {
  await skipSalePopup(page)
  await page.goto(path)
  await page.getByLabel('Email').fill(VENDOR.email)
  await page.getByLabel('Password', { exact: true }).fill(VENDOR.password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

/** Places a cash-on-delivery order of customer1 to the home address and waits until it is CONFIRMED. */
async function placeConfirmedOrder(request: APIRequestContext): Promise<number> {
  // 3 × Tomato (₹36) = ₹108: above the ₹99 minimum order
  await fillCart(request, [[1, 3]])
  const headers = await apiLogin(request)
  const placed = await request.post(`${GATEWAY}/api/v1/orders`, { headers, data: { addressId: 1, paymentMethod: 'COD', note: 'Playwright vendor test' } })
  expect(placed.ok(), `the order can be placed (are order-service and payment-service running?): ${await placed.text()}`).toBeTruthy()
  const orderId = (await placed.json()).data.id as number
  await expect
    .poll(async () => (await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers })).json()).data.status, { timeout: 15_000 })
    .toBe('CONFIRMED')
  return orderId
}

let orderId: number | undefined

test.beforeEach(async ({ request }) => {
  orderId = undefined
  await emptyCart(request)
})

test.afterEach(async ({ request }) => {
  if (orderId) {
    // Still cancellable while it waits for pickup; fails with 400 once a delivery partner picked it up (not in this test)
    await request.post(`${GATEWAY}/api/v1/orders/${orderId}/cancel`, { headers: await apiLogin(request), data: { reason: 'Playwright clean-up' } })
  }
  await emptyCart(request)
})

test('a vendor accepts, packs and hands over a new order', async ({ page, request }) => {
  orderId = await placeConfirmedOrder(request)

  await logInAsVendor(page, '/vendor')
  await expect(page).toHaveURL(/\/vendor$/)
  await expect(page.getByRole('heading', { name: `Hello, ${VENDOR.firstName}` })).toBeVisible()

  // The new order is in the queue (a list of order cards)
  await page.getByRole('navigation', { name: 'Vendor panel' }).getByRole('link', { name: /^Orders/ }).click()
  const queue = page.getByRole('list', { name: /New orders/ })
  await expect(queue.getByRole('link', { name: `Order #${orderId}` })).toBeVisible()
  await queue.getByRole('link', { name: `Order #${orderId}` }).click()

  await expect(page.getByRole('heading', { level: 1, name: `Order #${orderId}` })).toBeVisible()
  await expect(page.getByRole('list', { name: 'Products to pack' })).toContainText('Tomato')
  await expect(page.getByText('Note: “Playwright vendor test”')).toBeVisible()

  await page.getByRole('button', { name: `Accept order #${orderId}` }).click()
  await expect(page.getByRole('heading', { name: 'Pick and pack the items' })).toBeVisible()
  await page.getByRole('button', { name: `Mark order #${orderId} as packed` }).click()
  await page.getByRole('button', { name: `Mark order #${orderId} ready for pickup` }).click()
  await expect(page.getByRole('heading', { name: 'Waiting for pickup' })).toBeVisible()

  // The invoice exists once the store accepted the order
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Invoice' }).click()
  expect((await download).suggestedFilename()).toMatch(/^invoice-INV-\d+\.pdf$/)

  // The customer sees the store's steps
  const customerView = await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers: await apiLogin(request) })).json()
  expect(customerView.data.status).toBe('READY_FOR_PICKUP')
})

test('a vendor adds a product with stock and a photo, the shop sells it, and the vendor deletes it', async ({ page, request }) => {
  const run = String(Date.now()).slice(-8)
  const name = `Playwright Basil ${run}`
  await logInAsVendor(page, '/vendor/products/new')

  await page.getByLabel('Product name').fill(name)
  await choose(page.getByLabel('Category'), 'Vegetables')
  await page.getByLabel('Product code (SKU)').fill(`PW-${run}`)
  await page.getByLabel('Pack size').fill('100')
  await choose(page.getByLabel('Unit'), 'Gram (g)')
  await page.getByLabel('Price (MRP)').fill('25')
  await page.getByRole('button', { name: 'Save product' }).click()

  // The product page waits until the read side (Elasticsearch) has the new product
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible({ timeout: 15_000 })
  const productId = Number(page.url().split('/').pop())
  await expect(page.getByText(/No stock yet/)).toBeVisible({ timeout: 15_000 })
  await page.getByLabel('Units in the store').fill('12')
  await page.getByRole('button', { name: 'Save stock' }).click()
  await expect(page.getByText('Stock saved')).toBeVisible()

  await page.getByLabel('Add a photo').setInputFiles({ name: 'basil.png', mimeType: 'image/png', buffer: PNG_PIXEL })
  await expect(page.getByText('Photo added')).toBeVisible()
  await expect(page.getByRole('img', { name: `${name}, photo 1` })).toBeVisible()

  // Customers can buy it once the stock reached the shop's search (StockChanged event)
  await expect
    .poll(async () => (await (await request.get(`${GATEWAY}/api/v1/products/${productId}`)).json()).data?.inStock, { timeout: 15_000 })
    .toBe(true)

  await page.getByRole('button', { name: 'Delete product' }).click()
  await page.getByRole('alertdialog', { name: `Delete ${name}?` }).getByRole('button', { name: 'Delete' }).click()
  await expect(page.getByText(`${name} deleted`)).toBeVisible()
  await expect(page).toHaveURL(/\/vendor\/products$/)
})

test('a vendor pauses new orders, customers are refused, and the vendor resumes', async ({ page, request }) => {
  await fillCart(request, [[1, 3]])
  await logInAsVendor(page, '/vendor/store')
  await expect(page.getByRole('heading', { level: 1, name: 'Store settings' })).toBeVisible()
  await expect(page.getByRole('list', { name: 'PIN codes you deliver to' })).toContainText('560038')

  const intake = page.getByRole('main').getByRole('switch')
  await expect(intake).toHaveAccessibleName('Taking new orders')
  try {
    await intake.click()
    await expect(page.getByText('New orders are paused')).toBeVisible()
    const refused = await request.post(`${GATEWAY}/api/v1/orders`, { headers: await apiLogin(request), data: { addressId: 1, paymentMethod: 'COD', note: null } })
    expect(refused.status()).toBe(400)
    expect((await refused.json()).message).toBe('Fresh Mart is not taking orders right now')
  } finally {
    // Whatever happened above, Fresh Mart takes orders again afterwards
    await request.patch(`${GATEWAY}/api/v1/vendor/store/accepting-orders`, { headers: await apiLogin(request, VENDOR), data: { acceptingOrders: true } })
  }
})

test('a vendor sees the sales, the reviews and the coupons, and switches a coupon on and off', async ({ page, request }) => {
  await logInAsVendor(page, '/vendor')

  // Sales from report-service: the 14-day chart (one column per day) and the best sellers
  await expect(page.getByRole('heading', { name: 'Sales per day' })).toBeVisible()
  await expect(page.getByRole('list', { name: 'Sales per day' }).getByRole('listitem')).toHaveCount(14)
  await expect(page.getByRole('heading', { name: 'Best sellers' })).toBeVisible()

  const menu = page.getByRole('navigation', { name: 'Vendor panel' })
  await menu.getByRole('link', { name: 'Reviews' }).click()
  await expect(page.getByRole('list', { name: 'Reviews' }).getByRole('listitem').first()).toBeVisible()
  await page.getByRole('link', { name: '5 stars' }).click()
  await expect(page).toHaveURL(/\/vendor\/reviews\?rating=5$/)

  await menu.getByRole('link', { name: 'Coupons' }).click()
  const table = page.getByRole('table', { name: 'Your coupons' })
  await expect(table.getByRole('row').filter({ hasText: 'FRESH20' })).toContainText('Running')
  try {
    await table.getByRole('switch', { name: 'Coupon MANGO15 on' }).click()
    await expect(page.getByText('MANGO15 is on')).toBeVisible()
    await table.getByRole('switch', { name: 'Coupon MANGO15 on' }).click()
    await expect(page.getByText('MANGO15 is switched off')).toBeVisible()
  } finally {
    // MANGO15 is switched off in the seed: leave it that way
    const headers = await apiLogin(request, VENDOR)
    const coupons = (await (await request.get(`${GATEWAY}/api/v1/vendor/coupons`, { headers })).json()).data.content as { id: number; code: string }[]
    const mango = coupons.find((coupon) => coupon.code === 'MANGO15')
    if (mango) await request.patch(`${GATEWAY}/api/v1/vendor/coupons/${mango.id}/active`, { headers, data: { active: false } })
  }
})

test('a new vendor registers a store, which waits for approval', async ({ page }) => {
  await skipSalePopup(page)
  const run = `${Date.now()}${Math.floor(Math.random() * 10)}`
  await page.goto('/register?role=VENDOR')
  await page.getByLabel('Full name').fill('Smoke Vendor')
  await page.getByLabel('Email').fill(`e2e.vendor.${run}@example.com`)
  await page.getByLabel('Mobile number').fill(`8${run.slice(-9)}`)
  await page.getByLabel('Password', { exact: true }).fill('Store1234')
  await page.getByRole('button', { name: 'Create vendor account' }).click()

  await expect(page.getByRole('heading', { name: 'Register your store' })).toBeVisible()
  await page.getByLabel('Store name').fill(`Smoke Store ${run.slice(-6)}`)
  await page.getByLabel('Street address').fill('1, Test Street')
  await page.getByLabel('City').fill('Bengaluru')
  await page.getByLabel('State').fill('Karnataka')
  await page.getByLabel('PIN code').fill('560001')
  await page.getByRole('button', { name: 'Send for approval' }).click()
  await expect(page.getByRole('heading', { name: 'Your store is waiting for approval' })).toBeVisible()
})

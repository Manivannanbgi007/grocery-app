import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { apiLogin, emptyCart, fillCart, GATEWAY, skipSalePopup } from './helpers'

/*
 * Smoke flows 12 to 17: the admin panel (admin@grocery.com) against the real backend.
 * Every flow puts the data back where it can: a test order is cancelled, a test store is disabled after its approval
 * (so the shop does not list it), a KYC is rejected rather than approved. New accounts stay (a new email every run).
 */

const ADMIN = { email: 'admin@grocery.com', password: 'Password@123' }
const VENDOR1 = { email: 'vendor1@grocery.com', password: 'Password@123' }

async function logInAsAdmin(page: Page, path: string) {
  await skipSalePopup(page)
  await page.goto(path)
  await expect(page.getByRole('heading', { name: 'Admin login' })).toBeVisible()
  await page.getByLabel('Email').fill(ADMIN.email)
  await page.getByLabel('Password', { exact: true }).fill(ADMIN.password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

/** A new account through the API (a new email and phone every run). */
async function register(request: APIRequestContext, role: 'CUSTOMER' | 'VENDOR' | 'DELIVERY_PARTNER', fullName: string) {
  const run = `${Date.now()}${Math.floor(Math.random() * 10)}`
  const account = { fullName, email: `e2e.admin.${role.toLowerCase()}.${run}@example.com`, phone: `7${run.slice(-9)}`, password: 'Admin1234', role }
  const response = await request.post(`${GATEWAY}/api/v1/auth/register`, { data: account })
  expect(response.ok(), `a new ${role} can register: ${await response.text()}`).toBeTruthy()
  return { ...account, run, headers: { Authorization: `Bearer ${(await response.json()).data.accessToken as string}` } }
}

/** A cash-on-delivery order of customer1 (3 × Tomato), CONFIRMED. */
async function placeConfirmedOrder(request: APIRequestContext): Promise<number> {
  await fillCart(request, [[1, 3]])
  const headers = await apiLogin(request)
  const placed = await request.post(`${GATEWAY}/api/v1/orders`, { headers, data: { addressId: 1, paymentMethod: 'COD', note: 'Playwright admin test' } })
  expect(placed.ok(), `the order can be placed: ${await placed.text()}`).toBeTruthy()
  const orderId = (await placed.json()).data.id as number
  await expect.poll(async () => (await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers })).json()).data.status, { timeout: 15_000 }).toBe('CONFIRMED')
  return orderId
}

let orderId: number | undefined

test.beforeEach(async ({ request }) => {
  orderId = undefined
  await emptyCart(request)
})

test.afterEach(async ({ request }) => {
  // A test order still with the store or waiting for pickup is cancelled (its delivery job too)
  if (orderId) await request.post(`${GATEWAY}/api/v1/orders/${orderId}/cancel`, { headers: await apiLogin(request), data: { reason: 'Playwright clean-up' } })
  await emptyCart(request)
})

test('an admin approves a new store, and its vendor reaches the vendor panel', async ({ page, request }) => {
  const vendor = await register(request, 'VENDOR', 'Playwright Vendor')
  const storeName = `Playwright Store ${vendor.run.slice(-6)}`
  const created = await request.post(`${GATEWAY}/api/v1/vendor/store`, {
    headers: vendor.headers,
    data: { name: storeName, description: null, phone: vendor.phone, email: null, addressLine1: '1, Test Street', addressLine2: null, city: 'Bengaluru', state: 'Karnataka', pincode: '560001' },
  })
  expect(created.ok(), `the store can be registered: ${await created.text()}`).toBeTruthy()
  const storeId = (await created.json()).data.id as number

  try {
    await logInAsAdmin(page, '/admin/stores')
    await expect(page.getByRole('heading', { level: 1, name: 'Stores' })).toBeVisible()
    await page.getByLabel('Search stores by name').fill(storeName)
    await page.getByLabel('Search stores by name').press('Enter')
    await page.getByRole('link', { name: storeName }).click()
    await expect(page.getByRole('heading', { name: 'Waiting for approval' })).toBeVisible()
    await page.getByRole('button', { name: 'Approve' }).click()
    await page.getByRole('alertdialog', { name: `Approve ${storeName}?` }).getByRole('button', { name: 'Approve store' }).click()
    await expect(page.getByText(`${storeName} approved`)).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Active' })).toBeVisible()

    // auth-service links the store to the vendor a moment later (StoreApproved): then a new login has the store id.
    // A login every 2 seconds at most: the gateway limits logins per minute
    await expect
      .poll(async () => (await (await request.post(`${GATEWAY}/api/v1/auth/login`, { data: { email: vendor.email, password: vendor.password } })).json()).data?.user.storeId, {
        timeout: 20_000,
        intervals: [1000, 2000],
      })
      .toBe(storeId)
    // "Log out" is in the admin's profile card
    await page.getByRole('button', { name: /^Account of / }).click()
    await page.getByRole('button', { name: 'Log out' }).click()
    await page.goto('/vendor/login')
    await page.getByLabel('Email').fill(vendor.email)
    await page.getByLabel('Password', { exact: true }).fill(vendor.password)
    await page.getByRole('button', { name: 'Log in' }).click()
    await expect(page.getByRole('heading', { name: 'Hello, Playwright' })).toBeVisible()
  } finally {
    // The test store must not stay in the shop
    await request.post(`${GATEWAY}/api/v1/admin/stores/${storeId}/disable`, { headers: await apiLogin(request, ADMIN), data: { reason: 'Playwright clean-up' } })
  }
})

test('an admin cancels a problem order with a reason', async ({ page, request }) => {
  orderId = await placeConfirmedOrder(request)
  await logInAsAdmin(page, `/admin/orders/${orderId}`)
  await expect(page.getByRole('heading', { level: 1, name: `Order #${orderId}` })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Waiting for the store' })).toBeVisible()
  await expect(page.getByText('Note: “Playwright admin test”')).toBeVisible()

  await page.getByRole('button', { name: 'Cancel order' }).click()
  const dialog = page.getByRole('alertdialog', { name: `Cancel order #${orderId}?` })
  await dialog.getByRole('button', { name: 'The store cannot deliver it' }).click()
  await dialog.getByRole('button', { name: 'Cancel order' }).click()
  await expect(page.getByText(`Order #${orderId} cancelled`)).toBeVisible()
  await expect(page.getByText('Cancelled by an admin: “The store cannot deliver it”.')).toBeVisible()

  const customerView = (await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers: await apiLogin(request) })).json()).data
  expect(customerView.status).toBe('CANCELLED')
  expect(customerView.cancelledBy).toBe('ADMIN')
  orderId = undefined
})

test('an admin gives a delivery nobody took to a partner', async ({ page, request }) => {
  orderId = await placeConfirmedOrder(request)
  const vendorHeaders = await apiLogin(request, VENDOR1)
  for (const step of ['accept', 'pack', 'ready-for-pickup']) {
    const moved = await request.post(`${GATEWAY}/api/v1/vendor/orders/${orderId}/${step}`, { headers: vendorHeaders })
    expect(moved.ok(), `vendor1 can ${step} the order: ${await moved.text()}`).toBeTruthy()
  }
  // delivery-service makes the job from OrderReadyForPickup; with nobody online it waits for a partner
  const adminHeaders = await apiLogin(request, ADMIN)
  const findDelivery = async () => {
    const deliveries = (await (await request.get(`${GATEWAY}/api/v1/admin/deliveries?size=50`, { headers: adminHeaders })).json()).data.content as { orderId: number; status: string }[]
    return deliveries.find((delivery) => delivery.orderId === orderId)
  }
  await expect.poll(async () => (await findDelivery())?.status ?? 'none', { timeout: 15_000, message: 'is delivery-service running?' }).not.toBe('none')
  test.skip((await findDelivery())?.status !== 'WAITING_FOR_PARTNER', 'A partner is online and took the delivery by itself')

  await logInAsAdmin(page, '/admin/deliveries')
  await page.getByRole('button', { name: `Assign a partner to order #${orderId}` }).click()
  const dialog = page.getByRole('alertdialog', { name: `Assign order #${orderId}` })
  await dialog.getByRole('radio', { name: /Arjun Singh/ }).check()
  await dialog.getByRole('button', { name: 'Assign' }).click()
  await expect(page.getByText(`Order #${orderId} assigned to Arjun Singh`)).toBeVisible()

  // order-service hears about it (DeliveryAssigned): the order shows who brings it
  await expect
    .poll(async () => (await (await request.get(`${GATEWAY}/api/v1/orders/${orderId}`, { headers: await apiLogin(request) })).json()).data.deliveryPartnerName, { timeout: 15_000 })
    .toBe('Arjun Singh')
})

test('an admin switches a customer account off and on again', async ({ page, request }) => {
  const customer = await register(request, 'CUSTOMER', 'Playwright Shopper')
  await logInAsAdmin(page, '/admin/accounts')
  await page.getByLabel('Search accounts by name or email').fill(customer.email)
  await page.getByLabel('Search accounts by name or email').press('Enter')
  await page.getByRole('link', { name: 'Playwright Shopper' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Playwright Shopper' })).toBeVisible()

  await page.getByRole('button', { name: 'Disable account' }).click()
  await page.getByRole('alertdialog', { name: "Disable Playwright Shopper's account?" }).getByRole('button', { name: 'Disable account' }).click()
  await expect(page.getByText('Playwright Shopper can no longer log in')).toBeVisible()
  const refused = await request.post(`${GATEWAY}/api/v1/auth/login`, { data: { email: customer.email, password: customer.password } })
  expect(refused.status()).toBe(403)

  await page.getByRole('button', { name: 'Enable account' }).click()
  await expect(page.getByText('Playwright Shopper can log in again')).toBeVisible()
  const allowed = await request.post(`${GATEWAY}/api/v1/auth/login`, { data: { email: customer.email, password: customer.password } })
  expect(allowed.ok()).toBeTruthy()
})

test("an admin reviews a new delivery partner's KYC", async ({ page, request }) => {
  const partner = await register(request, 'DELIVERY_PARTNER', 'Playwright Rider')
  // delivery-service makes the partner from UserRegistered, a moment later
  await expect.poll(async () => (await request.get(`${GATEWAY}/api/v1/delivery/profile`, { headers: partner.headers })).status(), { timeout: 15_000 }).toBe(200)
  const kyc = await request.put(`${GATEWAY}/api/v1/delivery/profile/kyc`, {
    headers: partner.headers,
    data: { vehicleType: 'BICYCLE', vehicleNumber: 'KA03PW1234', drivingLicenseNumber: `KA03${partner.run.slice(-9)}`, zoneId: 1 },
  })
  expect(kyc.ok(), `the KYC can be sent: ${await kyc.text()}`).toBeTruthy()

  await logInAsAdmin(page, '/admin/partners')
  const row = page.getByRole('row').filter({ hasText: 'Playwright Rider' })
  await expect(row).toContainText('KA03PW1234')
  // Rejected (not approved), so the real data gets no partner who could be given deliveries
  await row.getByRole('button', { name: 'Reject the KYC of Playwright Rider' }).click()
  const dialog = page.getByRole('alertdialog', { name: 'Reject the KYC of Playwright Rider?' })
  await dialog.getByRole('button', { name: 'The documents are not readable' }).click()
  await dialog.getByRole('button', { name: 'Reject KYC' }).click()
  await expect(page.getByText('The KYC of Playwright Rider is rejected')).toBeVisible()
  const profile = (await (await request.get(`${GATEWAY}/api/v1/delivery/profile`, { headers: partner.headers })).json()).data
  expect(profile.kycStatus).toBe('REJECTED')
})

test('every admin page opens with data from the backend', async ({ page }) => {
  await logInAsAdmin(page, '/admin')
  await expect(page.getByRole('heading', { name: 'Hello, Platform' })).toBeVisible()
  await expect(page.getByText('Total orders')).toBeVisible()
  const pages: [string, string][] = [
    ['Orders', 'Orders'],
    ['Deliveries', 'Deliveries'],
    ['Payments', 'Payments'],
    ['Stores', 'Stores'],
    ['Delivery partners', 'Delivery partners'],
    ['Delivery zones', 'Delivery zones'],
    ['Accounts', 'Accounts'],
    ['Products', 'Products'],
    ['Categories', 'Categories'],
    ['Reviews', 'Reviews'],
    ['Coupons', 'Coupons'],
    ['Banners', 'Banners'],
    ['Reports', 'Reports'],
    ['Notifications', 'Notifications'],
  ]
  const menu = page.getByRole('navigation', { name: 'Admin panel' })
  for (const [link, heading] of pages) {
    await menu.getByRole('link', { name: new RegExp(`^${link}`) }).click()
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible()
    await expect(page.getByRole('alert')).toHaveCount(0)
  }
})

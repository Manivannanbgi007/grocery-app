import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { apiLogin, choose, emptyCart, fillCart, GATEWAY, skipSalePopup } from './helpers'

/*
 * Smoke flows 21 to 24: the super admin's Owner pages (superadmin@grocery.com) against the real backend.
 * Every flow puts the data back: the settings, Fresh Mart's commission and the DELIVERY_SLOTS flag get their old
 * values again (also when a step fails); a new admin is switched off at the end. New accounts stay (a new email every
 * run), and the audit log keeps the setting and flag changes.
 */

const SUPER_ADMIN = { email: 'superadmin@grocery.com', password: 'Password@123' }

async function logInAsSuperAdmin(page: Page, path: string, account: { email: string; password: string } = SUPER_ADMIN) {
  await skipSalePopup(page)
  await page.goto(path)
  await expect(page.getByRole('heading', { name: 'Admin login' })).toBeVisible()
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

/** A new number every run: emails and mobile numbers must be unique. */
function newRun() {
  return `${Date.now()}${Math.floor(Math.random() * 10)}`
}

async function superAdmin(request: APIRequestContext) {
  return apiLogin(request, SUPER_ADMIN)
}

test('the super admin adds an admin, who logs in to the admin panel without the Owner pages', async ({ page, browser, request }) => {
  const run = newRun()
  const newAdmin = { fullName: 'Playwright Admin', email: `e2e.owner.admin.${run}@example.com`, phone: `6${run.slice(-9)}`, password: 'Owner1234' }
  let newAdminId: number | undefined
  try {
    await logInAsSuperAdmin(page, '/admin/admins')
    await expect(page.getByRole('heading', { level: 1, name: 'Admins' })).toBeVisible()
    await page.getByRole('button', { name: 'Add an admin' }).click()
    await page.getByLabel('Full name').fill(newAdmin.fullName)
    await page.getByLabel('Email').fill(newAdmin.email)
    await page.getByLabel('Mobile number').fill(newAdmin.phone)
    await page.getByLabel('First password').fill(newAdmin.password)
    await page.getByRole('button', { name: 'Add admin' }).click()
    await expect(page.getByText('Playwright Admin is an admin now and can log in at the admin panel')).toBeVisible()
    const row = page.getByRole('table', { name: /Admins/ }).getByRole('row').filter({ hasText: newAdmin.email })
    await expect(row).toBeVisible()
    newAdminId = Number((await row.getByRole('link').getAttribute('href'))!.split('/').pop())

    // The new admin works in the admin panel, but the Owner pages are not theirs
    const context = await browser.newContext()
    const adminPage = await context.newPage()
    await logInAsSuperAdmin(adminPage, '/admin', newAdmin)
    await expect(adminPage.getByRole('heading', { name: 'Hello, Playwright' })).toBeVisible()
    await expect(adminPage.getByRole('navigation', { name: 'Admin panel' }).getByRole('link', { name: 'Settings' })).toHaveCount(0)
    await adminPage.goto('/admin/settings')
    await expect(adminPage.getByRole('heading', { name: 'This page is for the super admin' })).toBeVisible()
    await context.close()
  } finally {
    // The test admin cannot log in afterwards
    if (newAdminId) await request.patch(`${GATEWAY}/api/v1/admin/users/${newAdminId}/status`, { headers: await superAdmin(request), data: { status: 'DISABLED' } })
  }
})

test('the super admin makes a customer an admin and a customer again', async ({ page, request }) => {
  const run = newRun()
  const email = `e2e.owner.role.${run}@example.com`
  const registered = await request.post(`${GATEWAY}/api/v1/auth/register`, { data: { fullName: 'Playwright Role', email, phone: `7${run.slice(-9)}`, password: 'Role12345', role: 'CUSTOMER' } })
  expect(registered.ok(), `a new customer can register: ${await registered.text()}`).toBeTruthy()
  const userId = (await registered.json()).data.user.id as number
  const headers = await superAdmin(request)
  // customer-service makes the profile from UserRegistered a moment later: then "Customer" can be chosen again
  await expect
    .poll(async () => (await (await request.get(`${GATEWAY}/api/v1/admin/customers?keyword=${encodeURIComponent(email)}`, { headers })).json()).data.totalElements, { timeout: 15_000 })
    .toBe(1)
  const roleOf = async () => (await (await request.get(`${GATEWAY}/api/v1/admin/users/${userId}`, { headers })).json()).data.role as string

  await logInAsSuperAdmin(page, `/admin/accounts/${userId}`)
  const card = page.getByRole('region', { name: 'Role' })
  await expect(card.getByRole('radio', { name: /Delivery partner/ })).toBeDisabled()
  await card.getByRole('radio', { name: 'Admin' }).check()
  await card.getByRole('button', { name: 'Change role' }).click()
  await page.getByRole('alertdialog', { name: 'Make Playwright Role an admin?' }).getByRole('button', { name: 'Change role' }).click()
  await expect(page.getByText('Playwright Role is now an admin')).toBeVisible()
  expect(await roleOf()).toBe('ADMIN')

  await card.getByRole('radio', { name: 'Customer' }).check()
  await card.getByRole('button', { name: 'Change role' }).click()
  await page.getByRole('alertdialog', { name: 'Make Playwright Role a customer?' }).getByRole('button', { name: 'Change role' }).click()
  await expect(page.getByText('Playwright Role is now a customer')).toBeVisible()
  expect(await roleOf()).toBe('CUSTOMER')
})

test('a new delivery fee reaches the cart and the audit log', async ({ page, request }) => {
  const headers = await superAdmin(request)
  const before = (await (await request.get(`${GATEWAY}/api/v1/super-admin/settings`, { headers })).json()).data
  const newFee = before.deliveryFee === 35 ? 36 : 35
  const cartFee = async () => (await (await request.get(`${GATEWAY}/api/v1/cart`, { headers: await apiLogin(request) })).json()).data.totals.deliveryFee as number
  await emptyCart(request)
  // 3 x Tomato = ₹108: below the free delivery amount, so the cart charges the delivery fee
  await fillCart(request, [[1, 3]])
  try {
    await logInAsSuperAdmin(page, '/admin/settings')
    await expect(page.getByLabel('Delivery fee')).toHaveValue(String(before.deliveryFee))
    await page.getByLabel('Delivery fee').fill(String(newFee))
    await page.getByRole('button', { name: 'Save settings' }).click()
    await expect(page.getByText('Settings saved. New carts use them in a moment.')).toBeVisible()
    // cart-service hears PlatformSettingsChanged
    await expect.poll(cartFee, { timeout: 15_000, message: 'cart-service uses the new delivery fee' }).toBe(newFee)

    await page.getByRole('navigation', { name: 'Admin panel' }).getByRole('link', { name: 'Audit log' }).click()
    await choose(page.getByRole('combobox', { name: 'Source' }), 'Platform changes')
    const first = page.getByRole('list', { name: 'Audit entries' }).getByRole('listitem').first()
    await expect(first.getByRole('heading')).toHaveText('Platform settings updated')
    await first.getByText('Details').click()
    // Java writes the request as "SettingsRequest[currency=INR, deliveryFee=35, ...]"
    await expect(first.getByText(new RegExp(`deliveryFee=${newFee}(\\.00)?,`))).toBeVisible()
  } finally {
    // Only when the fee was changed: every save is an audit log entry
    const now = (await (await request.get(`${GATEWAY}/api/v1/super-admin/settings`, { headers })).json()).data
    if (now.deliveryFee !== before.deliveryFee) {
      const { updatedAt: _updatedAt, ...settings } = before
      await request.put(`${GATEWAY}/api/v1/super-admin/settings`, { headers, data: settings })
      await expect.poll(cartFee, { timeout: 15_000, message: 'the old delivery fee is back' }).toBe(before.deliveryFee)
    }
    await emptyCart(request)
  }
})

test('the super admin switches a flag, changes a commission, and reads the reports, the audit log and the system', async ({ page, request }) => {
  const headers = await superAdmin(request)
  try {
    await logInAsSuperAdmin(page, '/admin/feature-flags')
    const flags = page.getByRole('list', { name: 'Feature flags' })
    const slots = flags.getByRole('switch', { name: 'DELIVERY_SLOTS on' })
    await expect(slots).not.toBeChecked()
    await slots.click()
    await expect(page.getByText('DELIVERY_SLOTS is on')).toBeVisible()
    await expect(slots).toBeChecked()
    await slots.click()
    await expect(page.getByText('DELIVERY_SLOTS is off')).toBeVisible()

    await page.goto('/admin/stores/1')
    const card = page.getByRole('region', { name: 'Platform commission' })
    await expect(card.getByLabel('Commission')).toHaveValue('10')
    await card.getByLabel('Commission').fill('12.5')
    await card.getByRole('button', { name: 'Save' }).click()
    await expect(page.getByText('Fresh Mart now pays 12.5% commission on new orders')).toBeVisible()
    await expect(page.getByText('12.5% of each order')).toBeVisible()

    // Delivered orders of the last 30 days (the delivery flows deliver one every run)
    const menu = page.getByRole('navigation', { name: 'Admin panel' })
    await menu.getByRole('link', { name: 'Commission' }).click()
    await expect(page.getByRole('table', { name: /Commission per store/ }).getByRole('row', { name: /Fresh Mart/ })).toBeVisible()

    await menu.getByRole('link', { name: 'Audit log' }).click()
    await choose(page.getByRole('combobox', { name: 'Source' }), 'Orders')
    await expect(page.getByRole('list', { name: 'Audit entries' }).getByRole('listitem').first().getByRole('heading')).toHaveText(/^Order /)

    await menu.getByRole('link', { name: 'System' }).click()
    await expect(page.getByText(/services are running|services are not answering|Could not ask Prometheus/)).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Monitoring tools' })).toBeVisible()
  } finally {
    // DELIVERY_SLOTS off (switched only when still on: every switch is an audit log entry), Fresh Mart at 10 %
    const flags = (await (await request.get(`${GATEWAY}/api/v1/super-admin/feature-flags`, { headers })).json()).data as { key: string; enabled: boolean }[]
    if (flags.find((flag) => flag.key === 'DELIVERY_SLOTS')?.enabled) {
      await request.patch(`${GATEWAY}/api/v1/super-admin/feature-flags/DELIVERY_SLOTS`, { headers, data: { enabled: false } })
    }
    await request.patch(`${GATEWAY}/api/v1/super-admin/stores/1/commission`, { headers, data: { commissionPercentage: 10 } })
  }
})

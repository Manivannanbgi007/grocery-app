import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'

/** The API gateway, called directly to prepare and clean up test data. */
export const GATEWAY = 'http://localhost:8080'

/** A seed customer of the backend's local data (auth-service). */
export const CUSTOMER = { email: 'customer1@grocery.com', password: 'Password@123', firstName: 'Priya' }
/** The seed customer whose cart and orders the assistant's write flows change (customer1's stay as the other flows need them). */
export const CUSTOMER2 = { email: 'customer2@grocery.com', password: 'Password@123', firstName: 'Rahul' }
/**
 * Chooses an option of one of the app's drop-downs (components/ui/Select). They are not native <select> elements, so
 * Playwright's selectOption does not work on them: open the field, then click the option.
 */
export async function choose(field: Locator, option: string) {
  await field.click()
  await field.page().getByRole('option', { name: option, exact: true }).click()
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

/** Chooses a day (YYYY-MM-DD) in one of the app's date fields (components/ui/DateField): opens its calendar, goes to the month and clicks the day. */
export async function pickDate(field: Locator, day: string) {
  const [year, month, date] = day.split('-').map(Number)
  await field.click()
  const calendar = field.page().getByRole('dialog', { name: /choose a date/ })
  await choose(calendar.getByRole('combobox', { name: 'Year' }), String(year))
  await choose(calendar.getByRole('combobox', { name: 'Month' }), MONTHS[month - 1])
  await calendar.getByRole('button', { name: new RegExp(`, ${date} ${MONTHS[month - 1]} ${year}$`) }).click()
}

/** delivery1 = Arjun Singh, zone Indiranagar (Fresh Mart's zone). */
export const PARTNER = { email: 'delivery1@grocery.com', password: 'Password@123' }
/** vendor1 = Fresh Mart. */
export const VENDOR1 = { email: 'vendor1@grocery.com', password: 'Password@123' }
export const ADMIN = { email: 'admin@grocery.com', password: 'Password@123' }

/**
 * The tokens of this test run, one per account, with when they were issued. The gateway allows only so many logins per
 * minute from one address (120 locally), so an account logs in through the API again only when its token is older than
 * 10 minutes (it lasts 15).
 */
const tokens = new Map<string, { token: string; issuedAt: number }>()
const TOKEN_REUSE_MS = 10 * 60 * 1000

/** The Authorization header of an account (customer1 by default) for API calls, logging it in when needed. */
export async function apiLogin(request: APIRequestContext, account: { email: string; password: string } = CUSTOMER): Promise<Record<string, string>> {
  let saved = tokens.get(account.email)
  if (!saved || Date.now() - saved.issuedAt > TOKEN_REUSE_MS) {
    const login = await request.post(`${GATEWAY}/api/v1/auth/login`, { data: { email: account.email, password: account.password } })
    expect(login.ok(), `${account.email} can log in (is auth-service running?): ${await login.text()}`).toBeTruthy()
    saved = { token: (await login.json()).data.accessToken as string, issuedAt: Date.now() }
    tokens.set(account.email, saved)
  }
  return { Authorization: `Bearer ${saved.token}` }
}

/** Empties the customer's cart through the API, so every run starts the same way. */
export async function emptyCart(request: APIRequestContext, customer = CUSTOMER) {
  const headers = await apiLogin(request, customer)
  const cleared = await request.delete(`${GATEWAY}/api/v1/cart`, { headers })
  expect(cleared.ok(), 'the cart can be emptied (is cart-service running?)').toBeTruthy()
}

/** Puts products into the customer's (empty) cart through the API: [productId, quantity] pairs. */
export async function fillCart(request: APIRequestContext, items: [number, number][], customer = CUSTOMER) {
  const headers = await apiLogin(request, customer)
  for (const [productId, quantity] of items) {
    const added = await request.post(`${GATEWAY}/api/v1/cart/items`, { headers, data: { productId, quantity } })
    expect(added.ok(), `product ${productId} can be added (in stock?)`).toBeTruthy()
  }
}

/** Fills in the login form that is on the screen. */
export async function logIn(page: Page, customer = CUSTOMER) {
  await page.getByLabel('Email').fill(customer.email)
  await page.getByLabel('Password', { exact: true }).fill(customer.password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

/** The "on sale" pop-up appears once per visit; marking it as seen keeps it from covering buttons. */
export async function skipSalePopup(page: Page) {
  await page.addInitScript(() => sessionStorage.setItem('fresh-grocery.sale-toast-seen', 'true'))
}

/** Logs in on the delivery panel's login page. */
export async function logInAsPartner(page: Page, account = PARTNER) {
  await skipSalePopup(page)
  await page.goto('/delivery/login')
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: 'Log in' }).click()
}

/** Arjun offline, and no task of his left from a failed run (cancelled while it can be, otherwise back to the store). */
export async function freePartner(request: APIRequestContext) {
  const headers = await apiLogin(request, PARTNER)
  await request.post(`${GATEWAY}/api/v1/delivery/availability/offline`, { headers })
  const active = (await (await request.get(`${GATEWAY}/api/v1/delivery/deliveries?active=true`, { headers })).json()).data.content as { id: number; orderId: number; status: string }[]
  for (const task of active) {
    const at = `${GATEWAY}/api/v1/delivery/deliveries/${task.id}`
    if (task.status === 'ASSIGNED') await request.post(`${at}/reject`, { headers })
    if (task.status === 'ACCEPTED') await request.post(`${GATEWAY}/api/v1/admin/orders/${task.orderId}/cancel`, { headers: await apiLogin(request, ADMIN), data: { reason: 'Playwright clean-up' } })
    if (task.status === 'PICKED_UP' || task.status === 'OUT_FOR_DELIVERY') await request.post(`${at}/issue`, { headers, data: { issue: 'OTHER', note: 'Playwright clean-up' } })
    if (['PICKED_UP', 'OUT_FOR_DELIVERY', 'FAILED'].includes(task.status)) await request.post(`${at}/return`, { headers })
  }
}

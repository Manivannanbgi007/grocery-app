import { expect, test, type Page } from '@playwright/test'
import { logIn, skipSalePopup } from './helpers'

/*
 * Login sessions against the real backend (docs/authentication.md): the access token lives in the page's memory only,
 * the refresh token in an HttpOnly cookie that the page cannot read; POST /api/v1/auth/refresh swaps the cookie for a
 * new access token.
 */

const ACCOUNT_BUTTON = 'Account of Priya Sharma'

async function logInAsPriya(page: Page) {
  await skipSalePopup(page)
  await page.goto('/login')
  await logIn(page)
  await expect(page.getByRole('button', { name: ACCOUNT_BUTTON })).toBeVisible()
}

test('the refresh token is an HttpOnly cookie the page cannot read, and no token is stored in the browser', async ({ page, context }) => {
  await logInAsPriya(page)

  const cookie = (await context.cookies()).find((candidate) => candidate.name === 'refresh_token')
  expect(cookie).toMatchObject({ httpOnly: true, sameSite: 'Strict', path: '/api/v1/auth' })
  // about 7 days
  expect(cookie!.expires).toBeGreaterThan(Date.now() / 1000 + 6 * 24 * 3600)
  expect(await page.evaluate(() => document.cookie)).not.toContain('refresh_token')
  // A JWT starts with "eyJ": none in localStorage or sessionStorage
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('eyJ')
})

test('a reload keeps the login; after a logout a reload does not', async ({ page, context }) => {
  await logInAsPriya(page)
  await page.goto('/orders')
  await expect(page.getByRole('button', { name: ACCOUNT_BUTTON })).toBeVisible()
  await expect(page).toHaveURL(/\/orders$/)

  await page.getByRole('button', { name: ACCOUNT_BUTTON }).click()
  const logout = page.waitForResponse((response) => response.url().endsWith('/api/v1/auth/logout'))
  await page.getByRole('button', { name: 'Log out' }).click()
  expect((await logout).status()).toBe(204)
  expect((await context.cookies()).find((candidate) => candidate.name === 'refresh_token')).toBeUndefined()

  await page.reload()
  await expect(page.getByRole('link', { name: 'Account', exact: true })).toBeVisible()
  await page.goto('/orders')
  await expect(page).toHaveURL(/\/login\?next=%2Forders$/)
})

test('a call refused with 401 gets a new token from /refresh and is sent again, without logging out', async ({ page }) => {
  // The first cart call after the login is answered 401, as if the access token had expired early
  let cartCalls = 0
  await page.route('**/api/v1/cart', (route) => {
    cartCalls++
    if (cartCalls > 1) return route.continue()
    return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ success: false, message: 'Your login has expired.', data: null }) })
  })
  const renewed = page.waitForResponse((response) => response.url().endsWith('/api/v1/auth/refresh') && response.status() === 200)

  await logInAsPriya(page)
  await renewed
  await expect.poll(() => cartCalls).toBeGreaterThanOrEqual(2)
  await expect(page.getByRole('button', { name: ACCOUNT_BUTTON })).toBeVisible()
  await expect(page.getByText('Your session has expired')).toHaveCount(0)
})

test('logging out in one tab logs out the other tabs', async ({ page, context }) => {
  await logInAsPriya(page)
  const otherTab = await context.newPage()
  await skipSalePopup(otherTab)
  await otherTab.goto('/')
  // The new tab gets the login with the cookie
  await expect(otherTab.getByRole('button', { name: ACCOUNT_BUTTON })).toBeVisible()

  await page.getByRole('button', { name: ACCOUNT_BUTTON }).click()
  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(otherTab.getByText('You logged out in another tab.')).toBeVisible()
  await expect(otherTab.getByRole('link', { name: 'Account', exact: true })).toBeVisible()
})

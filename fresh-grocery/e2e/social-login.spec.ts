import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { GATEWAY, logIn, skipSalePopup } from './helpers'

/*
 * Social login in a real browser, against the real backend and the local fake provider (mock-oauth2-server): the
 * button, the provider's login page, the way back, and the shop's own session (docs/social-login.md).
 *
 * Needs (docs/social-login.md, "Try it locally with the fake provider"):
 *   docker compose -f infrastructure/docker-compose.yml --profile tools up -d oauth-test-provider
 *   auth-service started with SOCIAL_LOGIN_ENABLED=true, SOCIAL_TEST_CLIENT_ID=grocery-local and
 *   APP_PUBLIC_URL=http://localhost:5175: this suite's dev server, where the provider must send the browser back to.
 * Without them, the tests are skipped.
 */

const APP = 'http://localhost:5175'
const PROVIDER = 'http://127.0.0.1:8099/test'
const BUTTON = 'Continue with Test provider (local only)'
const EXPIRED = 'This sign-in has expired or was already used. Please try again.'
/** Different for every run: new provider accounts, emails and mobile numbers */
const RUN = Date.now().toString().slice(-9)

/** Why the tests cannot run here, or null when everything is set up for them. */
async function missingSetup(request: APIRequestContext): Promise<string | null> {
  const discovery = await request.get(`${PROVIDER}/.well-known/openid-configuration`).catch(() => null)
  if (!discovery?.ok()) return 'the fake provider is not running (oauth-test-provider, profile tools)'
  const providers = (await (await request.get(`${GATEWAY}/api/v1/auth/social/providers`)).json()).data as { id: string }[]
  if (!providers.some((provider) => provider.id === 'test')) {
    return 'auth-service runs without SOCIAL_LOGIN_ENABLED=true and SOCIAL_TEST_CLIENT_ID=grocery-local'
  }
  const start = await request.get(`${GATEWAY}/api/v1/auth/oauth2/authorization/test`, { maxRedirects: 0 })
  const redirectUri = new URL(start.headers().location).searchParams.get('redirect_uri') ?? ''
  if (!redirectUri.startsWith(`${APP}/`)) return `auth-service sends the browser back to ${redirectUri} (APP_PUBLIC_URL), not to ${APP}`
  return null
}

test.beforeEach(async ({ page, request }) => {
  const missing = await missingSetup(request)
  test.skip(missing !== null, missing ?? '')
  await skipSalePopup(page)
})

/** From the shop's login page to the fake provider's. */
async function startLogin(page: Page) {
  await page.goto('/login')
  await page.getByRole('link', { name: BUTTON }).click()
  await expect(page).toHaveURL(/127\.0\.0\.1:8099\/test\/authorize/)
}

/** The fake provider's login form: the username is the person's id there (the same name is the same account). */
async function logInAtProvider(page: Page, username: string, claims: Record<string, unknown>) {
  await page.locator('input[name="username"]').fill(username)
  await page.locator('textarea[name="claims"]').fill(JSON.stringify(claims))
  await page.getByRole('button', { name: 'Sign-in' }).click()
}

test('a new person signs up with the provider and the mobile number, then logs in with it at once', async ({ page, context }) => {
  const person = { username: `e2e-new-${RUN}`, email: `e2e-new-${RUN}@example.com`, name: 'Esha Social' }
  const claims = { email: person.email, email_verified: true, name: person.name }
  await startLogin(page)
  await logInAtProvider(page, person.username, claims)

  await expect(page.getByRole('heading', { name: 'Finish creating your account' })).toBeVisible()
  // The one-time code is gone from the address bar
  await expect(page).toHaveURL(`${APP}/login/social`)
  await expect(page.getByLabel('Full name')).toHaveValue(person.name)
  await expect(page.getByLabel('Email')).toHaveValue(person.email)
  await page.getByLabel('Mobile number').fill(`9${RUN}`)
  await page.getByRole('button', { name: 'Create account' }).click()

  const account = page.getByRole('button', { name: `Account of ${person.name}` })
  await expect(account).toBeVisible()
  await expect(page).toHaveURL(`${APP}/customer/home`)
  // The shop's own session, exactly as after a password login; no cookie of the provider's
  const cookies = await context.cookies()
  expect(cookies.find((cookie) => cookie.name === 'refresh_token')).toMatchObject({ httpOnly: true, sameSite: 'Strict' })
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('eyJ')

  // Log out; the same provider account logs in at once next time
  await account.click()
  await page.getByRole('button', { name: 'Log out' }).click()
  await expect(account).toBeHidden()
  await startLogin(page)
  await logInAtProvider(page, person.username, claims)
  await expect(account).toBeVisible()
})

test('a social login while the browser holds another login replaces it, and a reload keeps the new one', async ({ page }) => {
  // Priya logs in with her password (as in another tab while this one was at the provider) ...
  await page.goto('/login')
  await logIn(page)
  await expect(page.getByRole('button', { name: 'Account of Priya Sharma' })).toBeVisible()

  // ... then the provider's login comes back: the button's address, opened directly (Priya's login page has no button)
  await page.goto('/api/v1/auth/oauth2/authorization/test')
  await logInAtProvider(page, `e2e-switch-${RUN}`, { email: `e2e-switch-${RUN}@example.com`, email_verified: true, name: 'Sara Switch' })
  await expect(page.getByRole('heading', { name: 'Finish creating your account' })).toBeVisible()
  await page.getByLabel('Mobile number').fill(`7${RUN}`)
  await page.getByRole('button', { name: 'Create account' }).click()
  const account = page.getByRole('button', { name: 'Account of Sara Switch' })
  await expect(account).toBeVisible()

  // The new refresh cookie works: a reload restores Sara, not Priya and not a guest
  await page.reload()
  await expect(account).toBeVisible()
})

test('an existing account is connected once with its password, and its owner gets an email', async ({ page, request }) => {
  const email = `e2e-link-${RUN}@example.com`
  const registered = await request.post(`${GATEWAY}/api/v1/auth/register`, {
    data: { fullName: 'Lina Link', email, phone: `8${RUN}`, password: 'Fresh123', role: 'CUSTOMER' },
  })
  expect(registered.status()).toBe(201)

  await startLogin(page)
  await logInAtProvider(page, `e2e-link-${RUN}`, { email, email_verified: true, name: 'Lina Link' })
  await expect(page.getByRole('heading', { name: 'Connect your account' })).toBeVisible()
  await expect(page.getByText(email)).toBeVisible()
  await page.getByLabel('Password', { exact: true }).fill('Fresh123')
  await page.getByRole('button', { name: 'Connect and log in' }).click()
  await expect(page.getByRole('button', { name: 'Account of Lina Link' })).toBeVisible()

  // SocialLoginLinked: auth-service's outbox -> Kafka -> notification-service -> Mailpit
  await expect
    .poll(
      async () => {
        const search = await request.get(`http://localhost:8025/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)
        return ((await search.json()).messages as { Subject: string }[]).map((message) => message.Subject).join(' | ')
      },
      { timeout: 30_000 },
    )
    .toContain('was connected to your account')
})

test('cancelling at the provider brings the person back with a message', async ({ page }) => {
  // The button's redirect, read instead of followed (page.route cannot catch a redirect's target) ...
  await page.goto('/login')
  const start = await page.request.get(`${APP}/api/v1/auth/oauth2/authorization/test`, { maxRedirects: 0 })
  const authorize = new URL(start.headers().location)
  // ... then what a real provider does when the person clicks "Cancel": back to the callback with error=access_denied
  const callback = new URL(authorize.searchParams.get('redirect_uri')!)
  callback.searchParams.set('error', 'access_denied')
  callback.searchParams.set('state', authorize.searchParams.get('state')!)
  await page.goto(callback.toString())

  await expect(page.getByRole('alert')).toContainText('You cancelled the login.')
  await expect(page).toHaveURL(`${APP}/login/social`)
  await expect(page.getByRole('link', { name: 'Back to the login' })).toHaveAttribute('href', '/login')
})

test('a one-time code works only once', async ({ page }) => {
  const callback = page.waitForResponse((response) => response.url().startsWith(`${APP}/api/v1/auth/login/oauth2/code/test`))
  await startLogin(page)
  await logInAtProvider(page, `e2e-replay-${RUN}`, { email: `e2e-replay-${RUN}@example.com`, email_verified: true, name: 'Ravi Replay' })
  const location = (await callback).headers().location
  expect(location).toMatch(new RegExp(`^${APP}/login/social#code=`))
  const code = new URL(location).hash.replace('#code=', '')

  // The page exchanged it already (the sign-up form is shown); the same code again is refused
  await expect(page.getByRole('heading', { name: 'Finish creating your account' })).toBeVisible()
  const replay = await page.request.post(`${APP}/api/v1/auth/social/session`, { data: { code } })
  expect(replay.status()).toBe(400)
  expect((await replay.json()).message).toBe(EXPIRED)
})

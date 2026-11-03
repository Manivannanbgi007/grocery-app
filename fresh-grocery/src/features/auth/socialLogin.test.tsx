import { screen, waitFor } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadSession } from '../../api/session'
import { mockLoginUser } from '../../mocks/mockAuth'
import { linkedSocialUser, linkSocialAccount, MOCK_SOCIAL_CODES } from '../../mocks/mockSocial'
import { server } from '../../mocks/server'
import { renderApp } from '../../test/render'

/*
 * "Continue with Google" and /login/social, against the mock API (mocks/handlers/socialAuth.ts). The real round trip to
 * the provider is tested by the backend's SocialLoginFlowIntegrationTest and by e2e/social-login.spec.ts.
 */

afterEach(() => {
  vi.unstubAllEnvs()
})

/** Like `npm run dev:mock`: the button plays the provider inside the app (it goes to /login/social#code=mock-google). */
function playProviderInApp() {
  vi.stubEnv('VITE_API_MOCKING', 'true')
}

describe('Social login buttons', () => {
  it.each(['/login', '/vendor/login', '/delivery/login'])('are on %s, as links to the backend', async (page) => {
    renderApp(page)
    expect(await screen.findByRole('link', { name: 'Continue with Google' })).toHaveAttribute('href', '/api/v1/auth/oauth2/authorization/google')
  })

  it("offer every provider the server has, in the server's order", async () => {
    renderApp('/login')
    await screen.findByRole('link', { name: 'Continue with Google' })
    const buttons = screen.getAllByRole('link', { name: /^Continue with / })
    expect(buttons.map((button) => button.textContent)).toEqual([
      'Continue with Google',
      'Continue with Microsoft',
      'Continue with Facebook',
      'Continue with Apple',
    ])
    expect(screen.getByRole('link', { name: 'Continue with Apple' })).toHaveAttribute('href', '/api/v1/auth/oauth2/authorization/apple')
  })

  it('are not there at all while social login is switched off on the server (SOCIAL_LOGIN_ENABLED)', async () => {
    let asked = 0
    server.use(
      http.get('*/api/v1/auth/social/providers', () => {
        asked++
        return HttpResponse.json({ success: true, message: 'Login providers', data: [] })
      }),
    )
    renderApp('/login')
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    await waitFor(() => expect(asked).toBe(1))

    expect(screen.queryByRole('link', { name: /^Continue with / })).not.toBeInTheDocument()
    expect(screen.queryByText('or')).not.toBeInTheDocument()
  })

  it('are not on the admin login (admins keep the password only)', async () => {
    renderApp('/admin/login')
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Google/ })).not.toBeInTheDocument()
  })
})

describe('Social login page', () => {
  it('signs a new person up with their mobile number, and goes to the page they wanted', async () => {
    playProviderInApp()
    const { user, router } = renderApp('/login?next=%2Fwishlist')
    await user.click(await screen.findByRole('link', { name: 'Continue with Google' }))

    expect(await screen.findByRole('heading', { name: 'Finish creating your account' })).toBeInTheDocument()
    // The one-time code is gone from the address bar
    expect(router.state.location).toMatchObject({ pathname: '/login/social', hash: '' })
    expect(screen.getByLabelText('Full name')).toHaveValue('Asha Rao')
    // Google verified the email: it cannot be changed
    expect(screen.getByLabelText('Email')).toHaveValue('asha.rao@gmail.com')
    expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')

    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(screen.getByLabelText('Mobile number')).toHaveAccessibleDescription(/10-digit Indian mobile number/)
    await user.type(screen.getByLabelText('Mobile number'), '9876543210')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/wishlist'))
    expect(await screen.findByText('Welcome, Asha!')).toBeInTheDocument()
    expect(loadSession()?.user).toMatchObject({ email: 'asha.rao@gmail.com', role: 'CUSTOMER' })
    // The next time, this Google account logs in at once
    expect(linkedSocialUser(MOCK_SOCIAL_CODES['mock-google'])?.email).toBe('asha.rao@gmail.com')
  })

  it("ends on the customer's own home page when no page was waiting", async () => {
    playProviderInApp()
    const { user, router } = renderApp('/login')
    await user.click(await screen.findByRole('link', { name: 'Continue with Google' }))
    await user.type(await screen.findByLabelText('Mobile number'), '9876543210')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/customer/home'))
    expect(await screen.findByText('Welcome, Asha!')).toBeInTheDocument()
  })

  it.each([
    ['Microsoft', 'meera.iyer@outlook.com', true],
    ['Facebook', 'farah.khan@example.com', false],
    ['Apple', 'x7k2qp9d@privaterelay.appleid.com', true],
  ])('signs up with %s too (the email locked only when the provider checked it)', async (provider, email, locked) => {
    playProviderInApp()
    const { user } = renderApp('/login')
    await user.click(await screen.findByRole('link', { name: `Continue with ${provider}` }))

    expect(await screen.findByText(`Signed in with ${provider}`)).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveValue(email)
    if (locked) expect(screen.getByLabelText('Email')).toHaveAttribute('readonly')
    else expect(screen.getByLabelText('Email')).not.toHaveAttribute('readonly')
  })

  it('creates the account type chosen on the register page', async () => {
    playProviderInApp()
    const { user, router } = renderApp('/register?role=DELIVERY_PARTNER')
    await user.click(await screen.findByRole('link', { name: 'Sign up with Google' }))

    expect(await screen.findByText(/Account type: Delivery partner/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Mobile number'), '9123456780')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/delivery'))
    expect(loadSession()?.user).toMatchObject({ email: 'asha.rao@gmail.com', role: 'DELIVERY_PARTNER' })
  })

  it('asks for the email when the provider gave none, and shows the backend field errors', async () => {
    const { user, router } = renderApp('/login/social#code=mock-google-no-email')
    const email = await screen.findByLabelText('Email')
    expect(email).toHaveValue('')
    expect(email).not.toHaveAttribute('readonly')

    await user.type(email, 'vikram@example.com')
    await user.type(screen.getByLabelText('Mobile number'), '9000000004') // Priya's number
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(screen.getByLabelText('Mobile number')).toHaveAccessibleDescription('This mobile number is already registered.'))

    await user.clear(screen.getByLabelText('Mobile number'))
    await user.type(screen.getByLabelText('Mobile number'), '9123456781')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/customer/home'))
    expect(loadSession()?.user.email).toBe('vikram@example.com')
  })

  it('logs a connected account in at once', async () => {
    linkSocialAccount(MOCK_SOCIAL_CODES['mock-google-customer'], 4)
    const { router } = renderApp('/login/social#code=mock-google-customer')
    await waitFor(() => expect(router.state.location.pathname).toBe('/customer/home'))
    expect(await screen.findByText('Welcome, Priya!')).toBeInTheDocument()
    expect(loadSession()?.user.email).toBe('customer1@grocery.com')
  })

  it('connects an existing account with its password', async () => {
    const { user, router } = renderApp('/login/social#code=mock-google-customer')
    expect(await screen.findByRole('heading', { name: 'Connect your account' })).toBeInTheDocument()
    expect(screen.getByText('customer1@grocery.com')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Forgot your password?' })).toHaveAttribute('href', '/forgot-password')

    await user.type(screen.getByLabelText('Password'), 'wrong-password1')
    await user.click(screen.getByRole('button', { name: 'Connect and log in' }))
    await waitFor(() => expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Invalid email or password'))
    expect(loadSession()).toBeNull()

    await user.clear(screen.getByLabelText('Password'))
    await user.type(screen.getByLabelText('Password'), 'Password@123')
    await user.click(screen.getByRole('button', { name: 'Connect and log in' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/customer/home'))
    expect(loadSession()?.user.email).toBe('customer1@grocery.com')
    expect(linkedSocialUser(MOCK_SOCIAL_CODES['mock-google-customer'])?.id).toBe(4)
  })

  it('sends a vendor to the vendor login, and ends the session the server started', async () => {
    const { user } = renderApp('/login/social#code=mock-google-vendor')
    await user.type(await screen.findByLabelText('Password'), 'Password@123')
    await user.click(screen.getByRole('button', { name: 'Connect and log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This is a vendor account. Vendors log in on the vendor panel.')
    expect(screen.getByRole('link', { name: 'Go to the vendor login' })).toHaveAttribute('href', '/vendor/login')
    expect(loadSession()).toBeNull()
    await waitFor(() => expect(mockLoginUser()).toBeNull())
  })

  it("exchanges the code only after the app's check for an earlier login has its answer", async () => {
    const order: string[] = []
    let answerRefresh = () => {}
    const refreshAnswered = new Promise<void>((resolve) => (answerRefresh = resolve))
    server.use(
      // The check after the page load waits until the test lets it answer
      http.post('*/api/v1/auth/refresh', async () => {
        order.push('refresh')
        await refreshAnswered
        order.push('refresh answered')
        return HttpResponse.json({ success: false, message: 'Your session has ended. Please log in again.' }, { status: 401 })
      }),
      http.post('*/api/v1/auth/social/session', () => {
        order.push('exchange')
        // undefined: the mock's own handler answers
      }),
    )
    renderApp('/login/social#code=mock-google')
    expect(await screen.findByText('Signing you in…')).toBeInTheDocument()
    // Time for an exchange that started too early to reach the mock
    await delay(200)
    expect(order).toEqual(['refresh'])

    answerRefresh()
    expect(await screen.findByRole('heading', { name: 'Finish creating your account' })).toBeInTheDocument()
    expect(order).toEqual(['refresh', 'refresh answered', 'exchange'])
  })

  it('refuses an admin account', async () => {
    renderApp('/login/social#code=mock-google-admin')
    expect(await screen.findByRole('alert')).toHaveTextContent('Admin accounts cannot use social login. Please log in with your password.')
    expect(screen.getByRole('link', { name: 'Back to the login' })).toHaveAttribute('href', '/login')
  })

  it.each([
    ['#error=cancelled', 'You cancelled the login.'],
    ['#error=failed', 'The login with your account provider did not work.'],
    ['#error=something-else', 'The login with your account provider did not work.'],
    ['#code=used-already', 'This sign-in has expired or was already used. Please try again.'],
    ['', 'The login with your account provider did not work.'],
  ])('explains "%s"', async (hash, message) => {
    renderApp(`/login/social${hash}`)
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })
})

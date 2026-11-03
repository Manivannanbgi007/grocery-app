import { act, screen, waitFor, within } from '@testing-library/react'
import { delay, http } from 'msw'
import { describe, expect, it } from 'vitest'
import { refreshSession } from '../../api/client'
import { loadSession, saveSession } from '../../api/session'
import { endMockLogin, mockLoginUser, startMockLogin } from '../../mocks/mockAuth'
import { server } from '../../mocks/server'
import { logInAs, renderApp } from '../../test/render'

/** Counts the calls to an auth endpoint (e.g. 'logout'), then lets the mock answer them as usual. */
function countCalls(endpoint: string) {
  const calls = { count: 0 }
  server.use(
    http.post(`*/api/v1/auth/${endpoint}`, () => {
      calls.count++
      // undefined: MSW passes the request on to the mock's own handler
    }),
  )
  return calls
}

/** What another tab of the app sends (see TABS_CHANNEL in AuthProvider). */
function messageFromAnotherTab(message: { type: 'login'; userId: number } | { type: 'logout' }) {
  const otherTab = new BroadcastChannel('fresh-grocery-auth')
  otherTab.postMessage(message)
  otherTab.close()
}

async function fillLogin(user: ReturnType<typeof renderApp>['user'], email: string, password: string) {
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('Login', () => {
  it('logs a customer in and goes back to the page in ?next=', async () => {
    const { user, router } = renderApp('/login?next=%2Fwishlist')
    await fillLogin(user, 'customer1@grocery.com', 'Password@123')

    await waitFor(() => expect(router.state.location.pathname).toBe('/wishlist'))
    expect(await screen.findByText('Welcome back, Priya!')).toBeInTheDocument()
    // The login page has no shop header; the next page brings it
    expect(await screen.findByRole('button', { name: 'Account of Priya Sharma' })).toBeInTheDocument()
    expect(loadSession()?.user.email).toBe('customer1@grocery.com')
  })

  it.each(['/login', `/login?next=${encodeURIComponent('/')}`])("logs a customer in on %s and opens the customer's own home page", async (page) => {
    const { user, router } = renderApp(page)
    await fillLogin(user, 'customer1@grocery.com', 'Password@123')

    await waitFor(() => expect(router.state.location.pathname).toBe('/customer/home'))
    // The shop's home page, with Priya logged in and "Home" marked in the main menu
    expect(await screen.findByRole('heading', { level: 1, name: 'Fresh vegetables, delivered today' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Account of Priya Sharma' })).toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Home' })).toHaveClass('bg-brand')
  })

  it("keeps the customer's home page for customers: a guest logs in first and comes back", async () => {
    const { user, router } = renderApp('/customer/home')
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.search).toBe(`?next=${encodeURIComponent('/customer/home')}`)

    await fillLogin(user, 'customer1@grocery.com', 'Password@123')
    await waitFor(() => expect(router.state.location.pathname).toBe('/customer/home'))
    expect(await screen.findByRole('heading', { level: 1, name: 'Fresh vegetables, delivered today' })).toBeInTheDocument()
  })

  it("tells another kind of account that the customer's home page is for customers", async () => {
    logInAs('vendor1@grocery.com')
    renderApp('/customer/home')
    expect(await screen.findByRole('heading', { name: 'This page is for customers' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the vendor panel' })).toHaveAttribute('href', '/vendor')
  })

  it('shows the backend message for a wrong password', async () => {
    const { user } = renderApp('/login')
    await fillLogin(user, 'customer1@grocery.com', 'wrong-password1')
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(loadSession()).toBeNull()
  })

  it('sends a vendor to the vendor login', async () => {
    const { user } = renderApp('/login')
    await fillLogin(user, 'vendor1@grocery.com', 'Password@123')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This is a vendor account. Vendors log in on the vendor panel.')
    expect(within(alert).getByRole('link', { name: 'Go to the vendor login' })).toHaveAttribute('href', '/vendor/login')
    expect(loadSession()).toBeNull()
    // The server had started a session for the vendor: it is ended again
    await waitFor(() => expect(mockLoginUser()).toBeNull())
  })

  it('sends an admin to the admin login, and a delivery partner to the delivery login', async () => {
    const { user } = renderApp('/login')
    await fillLogin(user, 'admin@grocery.com', 'Password@123')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This is an admin account. Admins log in on the admin panel.')
    expect(within(alert).getByRole('link', { name: 'Go to the admin login' })).toHaveAttribute('href', '/admin/login')
    expect(loadSession()).toBeNull()

    await user.clear(screen.getByLabelText('Email'))
    await user.clear(screen.getByLabelText('Password'))
    await fillLogin(user, 'delivery1@grocery.com', 'Password@123')
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('This is a delivery partner account. Delivery partners log in on the delivery panel.'))
    expect(within(screen.getByRole('alert')).getByRole('link', { name: 'Go to the delivery login' })).toHaveAttribute('href', '/delivery/login')
  })

  it('explains a disabled account', async () => {
    const { user } = renderApp('/login')
    await fillLogin(user, 'customer3@grocery.com', 'Password@123')
    expect(await screen.findByRole('alert')).toHaveTextContent('Your account is disabled. Please contact support.')
  })

  it('checks the fields before asking the server', async () => {
    const { user } = renderApp('/login')
    await user.click(await screen.findByRole('button', { name: 'Log in' }))
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Please enter your email address.')
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
  })

  it("is a page of its own: no shop header, a way back to the shop, and the panels' logins", async () => {
    renderApp('/login')
    expect(await screen.findByRole('heading', { level: 1, name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('search')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the shop' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', `/register?next=${encodeURIComponent('/')}`)
    expect(screen.getByRole('link', { name: 'Vendor login' })).toHaveAttribute('href', '/vendor/login')
    expect(screen.getByRole('link', { name: 'Delivery login' })).toHaveAttribute('href', '/delivery/login')
  })

  it('fills in a demo account with one click while developing', async () => {
    const { user } = renderApp('/login')
    await user.click(await screen.findByRole('button', { name: /Rahul Verma/ }))
    expect(screen.getByLabelText('Email')).toHaveValue('customer2@grocery.com')
    expect(screen.getByLabelText('Password')).toHaveValue('Password@123')
    await user.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByText('Welcome back, Rahul!')).toBeInTheDocument()
  })
})

describe('Register', () => {
  async function fillRegistration(user: ReturnType<typeof renderApp>['user'], values: { name: string; email: string; phone: string; password: string }) {
    await user.type(await screen.findByLabelText('Full name'), values.name)
    await user.type(screen.getByLabelText('Email'), values.email)
    await user.type(screen.getByLabelText('Mobile number'), values.phone)
    await user.type(screen.getByLabelText('Password'), values.password)
  }

  it('creates a customer account and logs it in', async () => {
    const { user, router } = renderApp('/register')
    await fillRegistration(user, { name: 'Neha Gupta', email: 'neha@example.com', phone: '9876543210', password: 'Fresh123' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(await screen.findByText('Welcome to Fresh Grocery, Neha!')).toBeInTheDocument()
    expect(loadSession()?.user).toMatchObject({ email: 'neha@example.com', role: 'CUSTOMER' })
  })

  it('shows the rules next to the fields', async () => {
    const { user } = renderApp('/register')
    await fillRegistration(user, { name: 'Neha', email: 'neha@example.com', phone: '1234567890', password: 'password' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(screen.getByLabelText('Mobile number')).toHaveAccessibleDescription(/10-digit Indian mobile number/)
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription(/at least one letter and one digit/)
  })

  it('says when the email is already registered', async () => {
    const { user } = renderApp('/register')
    await fillRegistration(user, { name: 'Priya', email: 'customer1@grocery.com', phone: '9876543210', password: 'Fresh123' })
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('This email address is already registered. Log in instead?'))
  })

  it('ticks the password rules off while typing', async () => {
    const { user } = renderApp('/register')
    const password = await screen.findByLabelText('Password')
    expect(password).not.toHaveAccessibleDescription(/done/)
    await user.type(password, 'fresh')
    expect(password).toHaveAccessibleDescription(/At least one letter: done/)
    expect(password).not.toHaveAccessibleDescription(/8 to 64 characters: done/)
    await user.type(password, '1234')
    expect(password).toHaveAccessibleDescription(/8 to 64 characters: done.*At least one letter: done.*At least one digit: done/)
  })

  it('switches between shop, sell and deliver, keeping what was typed', async () => {
    const { user } = renderApp('/register')
    await user.type(await screen.findByLabelText('Full name'), 'Sunil Rao')
    await user.click(screen.getByRole('link', { name: 'Sell' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Become a vendor' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Sell' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByLabelText('Full name')).toHaveValue('Sunil Rao')
    // A vendor logs in on the vendor panel
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/vendor/login')

    await user.click(screen.getByRole('link', { name: 'Deliver' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Become a delivery partner' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/delivery/login')
  })

  it('creates a vendor account and opens the store registration in the vendor panel', async () => {
    const { user, router } = renderApp('/register?role=VENDOR')
    await fillRegistration(user, { name: 'Sunil Rao', email: 'sunil@example.com', phone: '9123456780', password: 'Store123' })
    await user.click(screen.getByRole('button', { name: 'Create vendor account' }))
    expect(await screen.findByRole('heading', { name: 'Register your store' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/vendor')
    expect(loadSession()?.user).toMatchObject({ email: 'sunil@example.com', role: 'VENDOR' })
  })
})

describe('Session', () => {
  it('logs out from the account menu, and the server ends the refresh cookie', async () => {
    const logouts = countCalls('logout')
    logInAs()
    const { user, router } = renderApp('/shop')
    await user.click(await screen.findByRole('button', { name: 'Account of Priya Sharma' }))
    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('link', { name: 'Account' })).toHaveAttribute('href', '/login')
    expect(router.state.location.pathname).toBe('/')
    expect(loadSession()).toBeNull()
    await waitFor(() => expect(mockLoginUser()).toBeNull())
    expect(logouts.count).toBe(1)
    expect(localStorage.getItem('fresh-grocery.logout-pending')).toBeNull()
  })

  it('logs out on a page that needs a login and lands on the home page, not on the login page', async () => {
    logInAs()
    const { user, router } = renderApp('/account')
    const visited: string[] = []
    router.subscribe((state) => visited.push(state.location.pathname))
    // "Log out" in the side menu of My account (a page only for logged-in customers)
    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('link', { name: 'Account' })).toHaveAttribute('href', '/login')
    expect(router.state.location.pathname).toBe('/')
    expect(visited).not.toContain('/login')
  })

  it.each([
    ['vendor1@grocery.com', 'Ravi Kumar', '/vendor/orders?tab=all', '/vendor/login'],
    ['delivery1@grocery.com', 'Arjun Singh', '/delivery/history', '/delivery/login'],
    ['admin@grocery.com', 'Platform Admin', '/admin/orders?tab=delivered', '/admin/login'],
  ])('logs %s out of the panel to the plain login page, without ?next=', async (email, name, page, login) => {
    logInAs(email)
    const { user, router } = renderApp(page)
    const visited: string[] = []
    router.subscribe((state) => visited.push(state.location.pathname + state.location.search))
    await user.click(await screen.findByRole('button', { name: `Account of ${name}` }, { timeout: 10_000 }))
    await user.click(within(screen.getByRole('region', { name })).getByRole('button', { name: 'Log out' }))

    expect(await screen.findByText('You are logged out.')).toBeInTheDocument()
    // The login page is loaded on first use: the address changes once it is there
    await waitFor(() => expect(router.state.location.pathname).toBe(login))
    expect(await screen.findByLabelText('Email')).toBeInTheDocument()
    expect(router.state.location.search).toBe('')
    expect(visited.filter((address) => address.includes('next='))).toEqual([])
    expect(loadSession()).toBeNull()
  })

  it('keeps ?next= when a panel session ends by itself, so the user comes back to the page after logging in', async () => {
    logInAs('vendor1@grocery.com')
    const { router } = renderApp('/vendor/orders?tab=all')
    expect(await screen.findByRole('button', { name: 'Account of Ravi Kumar' }, { timeout: 10_000 })).toBeInTheDocument()

    endMockLogin() // e.g. the password was reset on another device
    await act(() => refreshSession())
    await waitFor(() => expect(router.state.location.pathname).toBe('/vendor/login'))
    expect(router.state.location.search).toBe(`?next=${encodeURIComponent('/vendor/orders?tab=all')}`)
  })

  it('logs out when the server refuses the refresh (the session ended on the server)', async () => {
    logInAs()
    renderApp('/')
    expect(await screen.findByRole('button', { name: 'Account of Priya Sharma' })).toBeInTheDocument()

    endMockLogin() // e.g. the password was reset on another device
    await act(() => refreshSession())
    expect(await screen.findByText('Your session has expired. Please log in again.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Account' })).toBeInTheDocument()
  })

  it('gets the login back after a reload with the refresh cookie, without a detour to the login page', async () => {
    startMockLogin(4) // the browser still has Priya's cookie; the memory is empty after the reload
    // A slow answer, so the check can be seen (then the mock answers as usual)
    server.use(http.post('*/api/v1/auth/refresh', () => delay(500).then(() => undefined)))
    const { router } = renderApp('/cart')
    const visited: string[] = []
    router.subscribe((state) => visited.push(state.location.pathname))
    expect(await screen.findByText('Checking your login…')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Account of Priya Sharma' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { level: 1, name: /cart/i })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/cart')
    expect(visited).not.toContain('/login')
    expect(loadSession()?.user.email).toBe('customer1@grocery.com')
  })

  it('sends a guest without a cookie to the login page after the check, without a message', async () => {
    const { router } = renderApp('/cart')
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.search).toBe(`?next=${encodeURIComponent('/cart')}`)
    expect(screen.queryByText(/session has expired/)).not.toBeInTheDocument()
  })

  it('finishes a logout that did not reach the server before the reload, instead of restoring the login', async () => {
    const refreshes = countCalls('refresh')
    localStorage.setItem('fresh-grocery.logout-pending', 'true')
    startMockLogin(4)
    renderApp('/')
    await waitFor(() => expect(mockLoginUser()).toBeNull())
    expect(screen.getByRole('link', { name: 'Account' })).toBeInTheDocument()
    expect(refreshes.count).toBe(0)
    expect(localStorage.getItem('fresh-grocery.logout-pending')).toBeNull()
  })

  it('renews the token shortly before it expires', async () => {
    logInAs()
    const before = loadSession()!
    // due for renewal in a moment (normally after 12 of its 15 minutes)
    saveSession({ ...before, renewAt: Date.now() + 50 })
    renderApp('/')
    await waitFor(() => expect(loadSession()?.accessToken).not.toBe(before.accessToken))
    expect(loadSession()?.user.email).toBe('customer1@grocery.com')
  })

  it('logs out when another tab logs out', async () => {
    logInAs()
    renderApp('/')
    expect(await screen.findByRole('button', { name: 'Account of Priya Sharma' })).toBeInTheDocument()
    messageFromAnotherTab({ type: 'logout' })
    expect(await screen.findByText('You logged out in another tab.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Account' })).toBeInTheDocument()
  })

  it('logs out when another tab logs in with another account', async () => {
    logInAs()
    renderApp('/')
    expect(await screen.findByRole('button', { name: 'Account of Priya Sharma' })).toBeInTheDocument()
    messageFromAnotherTab({ type: 'login', userId: 5 })
    expect(await screen.findByText('You logged in with another account in another tab. Please log in again.')).toBeInTheDocument()
    expect(loadSession()).toBeNull()
  })

  it('takes over the login of another tab while it is a guest', async () => {
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'Account' })).toBeInTheDocument()
    startMockLogin(4) // the other tab's login set the cookie
    messageFromAnotherTab({ type: 'login', userId: 4 })
    expect(await screen.findByRole('button', { name: 'Account of Priya Sharma' })).toBeInTheDocument()
  })
})

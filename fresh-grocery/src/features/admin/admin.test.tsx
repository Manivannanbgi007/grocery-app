import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { loadSession } from '../../api/session'
import { periodLabel } from '../../components/sales/salesPeriod'
import { publicStores, registerStore, storeById } from '../../mocks/mockStores'
import { clickDay, pickDate } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'
import { dayOf, formatDayLabel, partsOf } from '../../utils/calendar'
import { indiaDay } from '../../utils/dates'

/*
 * The admin panel on the mock API: login, the dashboard and the stores. admin@grocery.com = Platform Admin (user 2);
 * vendor3 = Suresh Patel (user 10) has no store yet, so a test can register one and approve it.
 */

const SURESH_STORE = { name: 'Suresh Kirana', description: null, phone: '9000000010', email: null, addressLine1: '5, Market Road', addressLine2: null, city: 'Bengaluru', state: 'Karnataka', pincode: '560001' }

async function fillLogin(user: ReturnType<typeof renderApp>['user'], email: string) {
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), 'Password@123')
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('Admin login', () => {
  it('sends a guest to the admin login, and an admin to the dashboard afterwards', async () => {
    const { user, router } = renderApp('/admin/stores')
    expect(await screen.findByRole('heading', { name: 'Admin login' })).toBeInTheDocument()
    expect(router.state.location.search).toBe(`?next=${encodeURIComponent('/admin/stores')}`)

    await fillLogin(user, 'admin@grocery.com')
    expect(await screen.findByRole('heading', { level: 1, name: 'Stores' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(loadSession()?.user.role).toBe('ADMIN')
  })

  it('lets the super admin in too, and sends a vendor to the vendor login', async () => {
    const { user } = renderApp('/admin/login')
    await fillLogin(user, 'vendor1@grocery.com')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This is a vendor account. Vendors log in on the vendor panel.')
    expect(within(alert).getByRole('link', { name: 'Go to the vendor login' })).toHaveAttribute('href', '/vendor/login')

    await user.clear(screen.getByLabelText('Email'))
    await user.clear(screen.getByLabelText('Password'))
    await fillLogin(user, 'superadmin@grocery.com')
    expect(await screen.findByRole('heading', { name: 'Hello, Super' }, { timeout: 10_000 })).toBeInTheDocument()
  })

  it('tells a logged-in customer that the panel is for the platform team', async () => {
    logInAs('customer1@grocery.com')
    renderApp('/admin')
    expect(await screen.findByRole('heading', { name: 'This panel is for the platform team' })).toBeInTheDocument()
    expect(within(screen.getByRole('main')).getByRole('link', { name: 'Go to the shop' })).toHaveAttribute('href', '/')
  })

  it('gives an admin in the shop a way back to the panel, and no bell', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Account of Platform Admin' }))
    expect(screen.getByRole('link', { name: 'Admin panel' })).toHaveAttribute('href', '/admin')
    expect(screen.queryByRole('button', { name: /Notifications/ })).not.toBeInTheDocument()
  })
})

describe('Admin dashboard', () => {
  it("shows the work that waits, today's numbers and the sales", async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin')
    expect(await screen.findByRole('heading', { name: 'Hello, Platform' }, { timeout: 10_000 })).toBeInTheDocument()

    const tile = (label: string) => screen.getByText(label, { selector: 'p' }).nextElementSibling
    await waitFor(() => expect(tile('Total orders')).toHaveTextContent('3'))
    expect(tile('Active stores')).toHaveTextContent('2')
    expect(tile('Active delivery partners')).toHaveTextContent('2')
    // Order 901 took 65 minutes from order to door
    expect(tile('Average delivery time')).toHaveTextContent('65 min')

    const work = screen.getByRole('region', { name: 'What needs your attention' })
    await waitFor(() => expect(within(work).getByRole('link', { name: /Stores to approve/ })).toHaveTextContent('0'))

    expect(await screen.findByRole('list', { name: 'Top stores' })).toHaveTextContent('Fresh Mart')
    expect(within(screen.getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem')).toHaveLength(14)
  })

  it("makes today's cards links to the pages with the details, and shows the active customers as a share", async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin')
    const today = await screen.findByRole('region', { name: 'Today' }, { timeout: 10_000 })

    expect(await within(today).findByRole('link', { name: /^Orders today/ })).toHaveAttribute('href', '/admin/orders')
    expect(within(today).getByRole('link', { name: /^Sales today/ })).toHaveAttribute('href', '/admin/reports')
    expect(within(today).getByRole('link', { name: /^Revenue today/ })).toHaveAttribute('href', '/admin/payments')
    expect(within(today).getByRole('link', { name: /^Pending refunds/ })).toHaveAttribute('href', '/admin/payments')

    // The ring is decoration: its share and the words under it say the same
    const platform = screen.getByRole('region', { name: 'The platform' })
    expect(within(platform).getByText(/ordered in the last 30 days/)).toBeInTheDocument()
    expect(within(platform).getByText('Active customers')).toBeInTheDocument()
  })

  it('says "All clear" or "Review now" per kind of work, and lists the latest orders', async () => {
    registerStore(10, SURESH_STORE)
    logInAs('superadmin@grocery.com')
    renderApp('/admin')

    const work = await screen.findByRole('region', { name: 'What needs your attention' }, { timeout: 10_000 })
    const stores = within(work).getByRole('link', { name: /Stores to approve/ })
    await waitFor(() => expect(stores).toHaveTextContent('Review now'))
    expect(within(stores).getByText('1')).toBeInTheDocument()
    await waitFor(() => expect(within(work).getByRole('link', { name: /Deliveries without a partner/ })).toHaveTextContent('All clear'))

    // The newest orders first, each opening the order
    const latest = await screen.findByRole('list', { name: 'Latest orders' })
    expect(within(latest).getAllByRole('listitem').length).toBeLessThanOrEqual(5)
    expect(within(latest).getByRole('link', { name: 'Order #901' })).toHaveAttribute('href', '/admin/orders/901')
    // The super admin gets the System page among the quick links
    expect(within(screen.getByRole('main')).getByRole('link', { name: 'System' })).toHaveAttribute('href', '/admin/system')
  })
})

describe('Admin dashboard: the sales period', () => {
  // The seed orders: 901 (delivered) was placed 3 days ago, 902 (cancelled) 2 days ago, 903 a moment ago
  const daysAgo = (days: number) => indiaDay(Date.now() - days * 24 * 60 * 60 * 1000)
  const salesDays = () => within(screen.getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem')

  it('shows the sales of one day picked in the calendar, and marks that choice instead of the tab', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin')
    const period = await screen.findByRole('navigation', { name: 'Period' }, { timeout: 10_000 })
    expect(within(period).getByRole('link', { name: '14 days' })).toHaveAttribute('aria-current', 'page')

    const day = daysAgo(3)
    await pickDate(user, within(period).getByRole('button', { name: 'Pick a day' }), day)

    expect(router.state.location.search).toBe(`?from=${day}&to=${day}`)
    const picked = within(period).getByRole('button', { name: `Day: ${formatDayLabel(day)}` })
    expect(picked).toHaveAttribute('aria-current', 'true')
    expect(picked).toHaveFocus()
    expect(within(period).getByRole('link', { name: '14 days' })).not.toHaveAttribute('aria-current')
    expect(screen.getByText(formatDayLabel(day), { selector: 'strong' })).toBeInTheDocument()
    await waitFor(() => expect(salesDays()).toHaveLength(1))
    expect(salesDays()[0]).toHaveAccessibleName(/from 1 order$/)
  })

  it('shows a custom range: the first click picks the first day, the next one the last day', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin')
    const period = await screen.findByRole('navigation', { name: 'Period' }, { timeout: 10_000 })
    await user.click(within(period).getByRole('button', { name: 'Custom range' }))
    const calendar = screen.getByRole('dialog', { name: /^Custom range/ })
    const show = within(calendar).getByRole('button', { name: 'Show sales' })
    expect(show).toBeDisabled()

    // More than 366 days cannot be shown
    await clickDay(user, calendar, daysAgo(400))
    await clickDay(user, calendar, daysAgo(0))
    expect(within(calendar).getByRole('alert')).toHaveTextContent('A report covers at most 366 days.')
    expect(show).toBeDisabled()

    await clickDay(user, calendar, daysAgo(9)) // a new first day
    await clickDay(user, calendar, daysAgo(12)) // before it: the first day again
    await clickDay(user, calendar, daysAgo(3)) // the last day
    expect(within(calendar).getByText('10 days')).toBeInTheDocument()
    await user.click(show)

    expect(router.state.location.search).toBe(`?from=${daysAgo(12)}&to=${daysAgo(3)}`)
    expect(within(period).getByRole('button', { name: `Custom range: ${periodLabel(daysAgo(12), daysAgo(3))}` })).toHaveAttribute('aria-current', 'true')
    await waitFor(() => expect(salesDays()).toHaveLength(10))

    // "Last month" shows the whole month before this one at once
    await user.click(within(period).getByRole('button', { name: /^Custom range/ }))
    await user.click(screen.getByRole('button', { name: 'Last month' }))
    const { year, month } = partsOf(daysAgo(0))
    expect(router.state.location.search).toBe(`?from=${dayOf(year, month - 1, 1)}&to=${dayOf(year, month, 0)}`)
  })

  it('says when a period has no orders at all', async () => {
    logInAs('admin@grocery.com')
    renderApp(`/admin?from=${daysAgo(60)}&to=${daysAgo(50)}`)

    expect(await screen.findByRole('heading', { name: 'No sales in this period' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Sales per day' })).not.toBeInTheDocument()
  })

  it('explains dates in the URL that cannot be shown, with a way back to the last 14 days', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp(`/admin?from=${daysAgo(3)}&to=${daysAgo(9)}`)

    const problem = await screen.findByRole('heading', { name: 'These dates cannot be shown' }, { timeout: 10_000 })
    const message = problem.closest('[role="alert"]') as HTMLElement
    expect(message).toHaveTextContent('The first day must not be after the last day.')
    const period = screen.getByRole('navigation', { name: 'Period' })
    expect(within(period).queryByRole('link', { current: 'page' })).not.toBeInTheDocument()

    await user.click(within(message).getByRole('link', { name: 'Show the last 14 days' }))
    expect(router.state.location.search).toBe('')
    expect(within(period).getByRole('link', { name: '14 days' })).toHaveAttribute('aria-current', 'page')
    await waitFor(() => expect(salesDays()).toHaveLength(14))
  })
})

describe('Admin stores', () => {
  it('approves a new store, after which its vendor reaches the vendor panel', async () => {
    registerStore(10, SURESH_STORE)
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/stores')

    // The approval queue is the first tab; the menu and the tab count the waiting store
    const menu = await screen.findByRole('navigation', { name: 'Admin panel' })
    await waitFor(() => expect(within(menu).getByRole('link', { name: 'Stores, 1 waiting for approval' })).toBeInTheDocument())
    expect(screen.getByRole('link', { name: 'Waiting for approval, 1 waiting' })).toHaveAttribute('aria-current', 'page')
    const table = await screen.findByRole('table', { name: /Waiting for approval stores/ })
    await user.click(within(table).getByRole('link', { name: 'Suresh Kirana' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Suresh Kirana' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'PIN codes it delivers to' })).toHaveTextContent('560001')
    await user.click(screen.getByRole('button', { name: 'Approve' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Approve Suresh Kirana?' })
    await user.click(within(dialog).getByRole('button', { name: 'Approve store' }))
    expect(await screen.findByText('Suresh Kirana approved')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Active' })).toBeInTheDocument()
    expect(publicStores().map((store) => store.name)).toContain('Suresh Kirana')

    // The vendor's next login has the store id: the vendor panel opens
    logInAs('vendor3@grocery.com')
    renderApp('/vendor')
    expect(await screen.findAllByRole('heading', { name: 'Hello, Suresh' }, { timeout: 10_000 })).not.toHaveLength(0)
  })

  it('rejects a store only with a reason', async () => {
    const store = registerStore(10, SURESH_STORE)
    logInAs('admin@grocery.com')
    const { user } = renderApp(`/admin/stores/${store.id}`)

    await user.click(await screen.findByRole('button', { name: 'Reject' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Reject Suresh Kirana?' })
    await user.click(within(dialog).getByRole('button', { name: 'Reject store' }))
    expect(within(dialog).getByText('Please tell the vendor why.')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'The address could not be verified' }))
    await user.click(within(dialog).getByRole('button', { name: 'Reject store' }))
    expect(await screen.findByText('Suresh Kirana rejected')).toBeInTheDocument()
    expect(screen.getByText(/Reason: “The address could not be verified”/)).toBeInTheDocument()
    expect(storeById(store.id)?.status).toBe('REJECTED')
  })

  it('disables an active store (customers no longer see it) and enables it again', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/stores?tab=active')
    const table = await screen.findByRole('table', { name: /Active stores/ })
    await user.click(within(table).getByRole('link', { name: 'Green Basket' }))

    await user.click(await screen.findByRole('button', { name: 'Disable store' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Disable Green Basket?' })
    await user.type(within(dialog).getByLabelText('Reason'), 'Many complaints this week')
    await user.click(within(dialog).getByRole('button', { name: 'Disable store' }))
    expect(await screen.findByText('Green Basket disabled')).toBeInTheDocument()
    expect(publicStores().map((store) => store.name)).not.toContain('Green Basket')

    await user.click(screen.getByRole('button', { name: 'Enable store' }))
    expect(await screen.findByText('Green Basket is active again')).toBeInTheDocument()
    expect(publicStores().map((store) => store.name)).toContain('Green Basket')
  })

  it('searches stores by name and shows a store that does not exist as such', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin/stores?tab=all')
    await screen.findByRole('table', { name: /All stores/ })
    await user.type(screen.getByLabelText('Search stores by name'), 'basket{Enter}')
    await waitFor(() => expect(router.state.location.search).toBe('?tab=all&q=basket'))
    await waitFor(() => expect(within(screen.getByRole('table', { name: /All stores/ })).getAllByRole('row')).toHaveLength(2))

    renderApp('/admin/stores/999')
    expect(await screen.findByRole('heading', { name: 'Store not found' })).toBeInTheDocument()
  })
})

import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { allUsers } from '../../../mocks/mockAuth'
import { allFlags, currentSettings, searchAuditEntries } from '../../../mocks/mockPlatform'
import { logInAs, renderApp } from '../../../test/render'

/*
 * The super admin's Owner pages (mock API): admins and roles, platform settings, feature flags.
 * superadmin = user 1, admin = user 2 (Platform Admin), customer1 = Priya Sharma (user 4), delivery1 = Arjun (user 5).
 */

const NO_FILTER = { eventType: null, source: null, userId: null, storeId: null, aggregateId: null, from: null, to: null }

describe('Owner pages', () => {
  it('shows the Owner menu to the super admin only', async () => {
    logInAs('superadmin@grocery.com')
    renderApp('/admin')
    const menu = await screen.findByRole('navigation', { name: 'Admin panel' }, { timeout: 10_000 })
    for (const page of ['Admins', 'Settings', 'Feature flags', 'Commission', 'Audit log', 'System']) {
      expect(within(menu).getByRole('link', { name: page })).toBeInTheDocument()
    }
    cleanup()

    logInAs('admin@grocery.com')
    renderApp('/admin/settings')
    expect(await screen.findByRole('heading', { name: 'This page is for the super admin' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: 'Admin panel' })).queryByRole('link', { name: 'Settings' })).not.toBeInTheDocument()
  })

  it('adds an admin, who can then log in to the admin panel', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/admins')
    const table = await screen.findByRole('table', { name: 'Admins (1)' }, { timeout: 10_000 })
    expect(table).toHaveTextContent('Platform Admin')

    await user.click(screen.getByRole('button', { name: 'Add an admin' }))
    await user.click(screen.getByRole('button', { name: 'Add admin' }))
    expect(screen.getByText('Please enter your name.')).toBeInTheDocument()
    expect(screen.getByText('Use 8 to 64 characters.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Full name'), 'Operations Admin')
    await user.type(screen.getByLabelText('Email'), 'admin@grocery.com')
    await user.type(screen.getByLabelText('Mobile number'), '9123456780')
    await user.type(screen.getByLabelText('First password'), 'Welcome123')
    await user.click(screen.getByRole('button', { name: 'Add admin' }))
    expect(await screen.findByText('This email address is already registered.')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Email'))
    await user.type(screen.getByLabelText('Email'), 'ops.admin@grocery.com')
    await user.click(screen.getByRole('button', { name: 'Add admin' }))
    expect(await screen.findByText('Operations Admin is an admin now and can log in at the admin panel')).toBeInTheDocument()
    expect(await screen.findByRole('table', { name: 'Admins (2)' })).toHaveTextContent('Operations Admin')
    expect(allUsers().find((account) => account.email === 'ops.admin@grocery.com')).toMatchObject({ role: 'ADMIN', status: 'ACTIVE', password: 'Welcome123' })
  })

  it('changes a role, offering only the roles whose profile exists', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/accounts/4')
    const card = await screen.findByRole('region', { name: 'Role' }, { timeout: 10_000 })
    // Priya signed up as a customer: no delivery profile
    expect(await within(card).findByRole('radio', { name: /Delivery partner/ })).toBeDisabled()
    expect(within(card).getByText('No delivery profile: it is only made when someone signs up as a delivery partner.')).toBeInTheDocument()
    expect(within(card).queryByRole('radio', { name: /Customer/ })).not.toBeInTheDocument()

    await user.click(within(card).getByRole('radio', { name: 'Admin' }))
    await user.click(within(card).getByRole('button', { name: 'Change role' }))
    await user.click(within(screen.getByRole('alertdialog', { name: 'Make Priya Sharma an admin?' })).getByRole('button', { name: 'Change role' }))
    expect(await screen.findByText('Priya Sharma is now an admin')).toBeInTheDocument()
    expect(allUsers().find((account) => account.id === 4)?.role).toBe('ADMIN')
    // ... and back: her customer profile still exists
    expect(await within(card).findByRole('radio', { name: 'Customer' })).toBeEnabled()
  })

  it("never offers to change the super admin's role, and shows no role card to an admin", async () => {
    logInAs('superadmin@grocery.com')
    renderApp('/admin/accounts/1')
    expect(await screen.findByText("The super admin's role cannot be changed.", {}, { timeout: 10_000 })).toBeInTheDocument()
    cleanup()

    logInAs('admin@grocery.com')
    renderApp('/admin/accounts/5')
    expect(await screen.findByRole('heading', { level: 1, name: 'Arjun Singh' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Role' })).not.toBeInTheDocument()
  })

  it('saves the platform settings, which the cart uses and the audit log records', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/settings')
    const fee = await screen.findByLabelText('Delivery fee', {}, { timeout: 10_000 })
    expect(fee).toHaveValue('30')

    await user.clear(screen.getByLabelText('Minimum order value'))
    await user.type(screen.getByLabelText('Minimum order value'), '400')
    await user.click(screen.getByRole('button', { name: 'Save settings' }))
    expect(screen.getByText('The minimum order cannot be higher than the free delivery amount.')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Minimum order value'))
    await user.type(screen.getByLabelText('Minimum order value'), '149')
    await user.clear(fee)
    await user.type(fee, '25.50')
    await user.click(screen.getByRole('button', { name: 'Save settings' }))
    expect(await screen.findByText('Settings saved. New carts use them in a moment.')).toBeInTheDocument()
    expect(currentSettings()).toMatchObject({ currency: 'INR', deliveryFee: 25.5, freeDeliveryFrom: 299, minimumOrderValue: 149 })
    expect(searchAuditEntries(NO_FILTER)[0]).toMatchObject({ eventType: 'PlatformSettingsUpdated', source: 'platform-service', userId: 1 })
  })

  it('switches a feature flag and adds one', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/feature-flags')
    const list = await screen.findByRole('list', { name: 'Feature flags' }, { timeout: 10_000 })
    expect(within(list).getAllByRole('heading').map((heading) => heading.textContent)).toEqual(['CITY_HYDERABAD', 'DELIVERY_SLOTS', 'PRODUCT_REVIEWS', 'RAZORPAY_PAYMENTS'])

    await user.click(within(list).getByRole('switch', { name: 'DELIVERY_SLOTS on' }))
    expect(await screen.findByText('DELIVERY_SLOTS is on')).toBeInTheDocument()
    expect(within(list).getByRole('switch', { name: 'DELIVERY_SLOTS on' })).toBeChecked()
    expect(within(list).getByText(/Changed by you/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add a feature flag' }))
    await user.type(screen.getByLabelText('Key'), 'delivery slots')
    expect(screen.getByLabelText('Key')).toHaveValue('DELIVERY_SLOTS')
    await user.type(screen.getByLabelText('What it switches on'), 'Pick a time slot')
    await user.click(screen.getByRole('button', { name: 'Add flag' }))
    expect(await screen.findByText('A flag with this key exists already.')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Key'))
    await user.type(screen.getByLabelText('Key'), 'city pune')
    await user.clear(screen.getByLabelText('What it switches on'))
    await user.type(screen.getByLabelText('What it switches on'), 'Stores and customers in Pune can use the app')
    await user.click(screen.getByRole('button', { name: 'Add flag' }))
    expect(await screen.findByText('CITY_PUNE added (off)')).toBeInTheDocument()
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Feature flags' })).getAllByRole('heading')[1]).toHaveTextContent('CITY_PUNE'))
    expect(allFlags().map((flag) => flag.key)).toContain('CITY_PUNE')
  })
})

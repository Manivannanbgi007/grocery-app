import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { allUsers } from '../../mocks/mockAuth'
import { listZones, partnerById, registerPartner, submitKyc } from '../../mocks/mockDelivery'
import { chooseOption } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'

/*
 * Accounts, delivery partners and delivery zones in the admin panel (mock API). customer1 = Priya Sharma (user 4, two
 * addresses, orders 901 and 902); customer3 = Anita Desai (user 9) is disabled; delivery1/2 = Arjun and Karthik (approved).
 */

const rows = (table: HTMLElement) => within(table).getAllByRole('row').slice(1)

/** A partner who registered and sent the KYC: it waits for a review. */
function partnerWaitingForReview(userId = 2001, fullName = 'Vikram Rao') {
  registerPartner(userId, fullName, '9876543210')
  submitKyc(userId, { vehicleType: 'BICYCLE', vehicleNumber: 'KA03XY0001', drivingLicenseNumber: 'KA0320210000001', zoneId: 1 })
}

describe('Admin accounts', () => {
  it('lists the accounts by role, status and name', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/accounts?tab=customers')
    const table = await screen.findByRole('table', { name: /Customers accounts/ }, { timeout: 10_000 })
    expect(rows(table).map((row) => within(row).getAllByRole('link')[0].textContent)).toEqual(['Priya Sharma', 'Rahul Verma', 'Anita Desai'])

    await chooseOption(user, screen.getByRole('combobox', { name: 'Account status' }), 'Disabled')
    await waitFor(() => expect(rows(screen.getByRole('table', { name: /Customers accounts/ }))).toHaveLength(1))
    expect(rows(screen.getByRole('table', { name: /Customers accounts/ }))[0]).toHaveTextContent('Anita Desai')

    await user.click(screen.getByRole('link', { name: 'All' }))
    await chooseOption(user, screen.getByRole('combobox', { name: 'Account status' }), 'Active and disabled')
    await user.type(screen.getByLabelText('Search accounts by name or email'), 'vendor{Enter}')
    await waitFor(() => expect(rows(screen.getByRole('table', { name: /All accounts/ }))).toHaveLength(3))
  })

  it("shows a customer's profile, addresses and orders, and switches the account off and on", async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/accounts/4')
    expect(await screen.findByRole('heading', { level: 1, name: 'Priya Sharma' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(await screen.findByText('Delivery phone:')).toBeInTheDocument()
    expect(within(await screen.findByRole('list', { name: 'Addresses' })).getAllByRole('listitem')).toHaveLength(2)
    expect(within(await screen.findByRole('list', { name: 'Latest orders' })).getAllByRole('link').map((link) => link.textContent)).toEqual(['Order #902', 'Order #901'])
    expect(screen.getByRole('link', { name: 'All their orders' })).toHaveAttribute('href', '/admin/orders?customer=4')

    await user.click(screen.getByRole('button', { name: 'Disable account' }))
    await user.click(within(screen.getByRole('alertdialog', { name: "Disable Priya Sharma's account?" })).getByRole('button', { name: 'Disable account' }))
    expect(await screen.findByText('Priya Sharma can no longer log in')).toBeInTheDocument()
    expect(allUsers().find((account) => account.id === 4)?.status).toBe('DISABLED')

    await user.click(screen.getByRole('button', { name: 'Enable account' }))
    expect(await screen.findByText('Priya Sharma can log in again')).toBeInTheDocument()
    expect(allUsers().find((account) => account.id === 4)?.status).toBe('ACTIVE')
  })

  it('leaves admin accounts to the super admin and protects your own account', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/accounts/2')
    expect(await screen.findByText('This is your own account.', {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Disable account' })).not.toBeInTheDocument()

    renderApp('/admin/accounts/1')
    expect(await screen.findByText("The super admin's account cannot be switched off.")).toBeInTheDocument()
  })

  it('lets the super admin switch an admin account off', async () => {
    logInAs('superadmin@grocery.com')
    renderApp('/admin/accounts/2')
    expect(await screen.findByRole('button', { name: 'Disable account' }, { timeout: 10_000 })).toBeInTheDocument()
  })
})

describe('Admin delivery partners', () => {
  it('approves a KYC from the queue', async () => {
    partnerWaitingForReview()
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/partners')
    const menu = await screen.findByRole('navigation', { name: 'Admin panel' })
    await waitFor(() => expect(within(menu).getByRole('link', { name: 'Delivery partners, 1 waiting for a KYC review' })).toBeInTheDocument())
    const table = await screen.findByRole('table', { name: /Waiting for review delivery partners/ })
    expect(rows(table)[0]).toHaveTextContent('KA03XY0001')

    await user.click(within(table).getByRole('button', { name: 'Approve the KYC of Vikram Rao' }))
    await user.click(within(screen.getByRole('alertdialog', { name: 'Approve Vikram Rao?' })).getByRole('button', { name: 'Approve KYC' }))
    expect(await screen.findByText('Vikram Rao is approved')).toBeInTheDocument()
    expect(await screen.findByText('No KYC is waiting for a review.')).toBeInTheDocument()
    expect(partnerById(2001)?.kycStatus).toBe('APPROVED')
  })

  it('rejects a KYC with a reason the partner sees', async () => {
    partnerWaitingForReview()
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/partners')
    const table = await screen.findByRole('table', { name: /Waiting for review delivery partners/ }, { timeout: 10_000 })
    await user.click(within(table).getByRole('button', { name: 'Reject the KYC of Vikram Rao' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Reject the KYC of Vikram Rao?' })
    await user.click(within(dialog).getByRole('button', { name: 'The documents are not readable' }))
    await user.click(within(dialog).getByRole('button', { name: 'Reject KYC' }))
    expect(await screen.findByText('The KYC of Vikram Rao is rejected')).toBeInTheDocument()
    expect(partnerById(2001)?.kycNote).toBe('The documents are not readable')
  })
})

describe('Admin delivery zones', () => {
  it('adds a zone after checking the form, and refuses a name that exists', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/zones')
    expect(within(await screen.findByRole('list', { name: 'Delivery zones' }, { timeout: 10_000 })).getAllByRole('heading').map((heading) => heading.textContent)).toEqual(['Indiranagar', 'Koramangala'])

    await user.click(screen.getByRole('button', { name: 'New zone' }))
    const form = screen.getByRole('region', { name: 'New zone' })
    await user.click(within(form).getByRole('button', { name: 'Add zone' }))
    expect(within(form).getByText('Enter a name, e.g. Indiranagar.')).toBeInTheDocument()
    expect(within(form).getByText('Add at least one PIN code.')).toBeInTheDocument()

    await user.type(within(form).getByLabelText('Zone name'), 'Koramangala')
    await user.type(within(form).getByLabelText('PIN code to add'), '560066{Enter}')
    await user.click(within(form).getByRole('button', { name: 'Add zone' }))
    expect(await within(form).findByText('A zone named Koramangala already exists')).toBeInTheDocument()

    await user.clear(within(form).getByLabelText('Zone name'))
    await user.type(within(form).getByLabelText('Zone name'), 'Whitefield')
    await user.click(within(form).getByRole('button', { name: 'Add zone' }))
    expect(await screen.findByText('Zone Whitefield added')).toBeInTheDocument()
    expect(listZones().find((zone) => zone.name === 'Whitefield')?.pincodes).toEqual(['560066'])
  })

  it("changes a zone's payout", async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/zones')
    await user.click(await screen.findByRole('button', { name: 'Change zone Koramangala' }, { timeout: 10_000 }))
    const payout = screen.getByLabelText('Partner earns per delivery')
    await user.clear(payout)
    await user.type(payout, '45.50')
    await user.click(screen.getByRole('button', { name: 'Save zone' }))
    expect(await screen.findByText('Zone Koramangala saved')).toBeInTheDocument()
    expect(listZones().find((zone) => zone.name === 'Koramangala')?.partnerPayout).toBe(45.5)
  })
})

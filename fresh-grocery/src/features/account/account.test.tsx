import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { logInAs, renderApp } from '../../test/render'

/* customer1 (Priya): profile born 1994-05-17, female; addresses Home (default, 560038) and Work (560034). */

const profileSection = () => screen.getByRole('heading', { name: 'Profile' }).closest('section')!
const passwordSection = () => screen.getByRole('heading', { name: 'Change password' }).closest('section')!

/** Opens /account logged in as customer1 and waits until the profile has loaded. */
async function openAccount() {
  logInAs()
  const rendered = renderApp('/account')
  await screen.findByLabelText('Full name')
  return rendered
}

describe('My account page', () => {
  it('sends a guest to the login page first', async () => {
    const { router } = renderApp('/account')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?next=%2Faccount')
  })

  it('shows the profile from the backend', async () => {
    await openAccount()
    expect(within(profileSection()).getByLabelText('Full name')).toHaveValue('Priya Sharma')
    expect(within(profileSection()).getByText('customer1@grocery.com')).toBeInTheDocument()
    expect(within(profileSection()).getByLabelText('Mobile number')).toHaveValue('9000000004')
    expect(within(profileSection()).getByLabelText('Date of birth (optional)')).toHaveTextContent('17 May 1994')
    expect(within(profileSection()).getByLabelText('Gender (optional)')).toHaveTextContent('Female')
  })

  it('checks and saves the profile', async () => {
    const { user } = await openAccount()
    const name = within(profileSection()).getByLabelText('Full name')
    const phone = within(profileSection()).getByLabelText('Mobile number')

    await user.clear(phone)
    await user.type(phone, '12345')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(within(profileSection()).getByText(/10-digit Indian mobile number/)).toBeInTheDocument()

    await user.clear(phone)
    await user.type(phone, '9876543210')
    await user.clear(name)
    await user.type(name, 'Priya S. Sharma')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByText('Your profile is saved')).toBeInTheDocument()
  })

  it('changes the password, and says when the current one is wrong', async () => {
    const { user } = await openAccount()
    const current = within(passwordSection()).getByLabelText('Current password')
    const next = within(passwordSection()).getByLabelText('New password')

    await user.type(current, 'Wrong@123')
    await user.type(next, 'NewPassword@456')
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await within(passwordSection()).findByText('This is not your current password.')).toBeInTheDocument()

    await user.clear(current)
    await user.type(current, 'Password@123')
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('Your password is changed')).toBeInTheDocument()
    expect(current).toHaveValue('')
  })

  it('is reached from the account menu', async () => {
    logInAs()
    const { user, router } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Account of Priya Sharma' }))
    await user.click(screen.getByRole('link', { name: 'My account' }))
    // The account page is loaded on first use (lazy): with every test file running at once that can take a while
    expect(await screen.findByRole('heading', { level: 1, name: 'My account' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/account')
  })
})

describe('Address book', () => {
  const cards = () => within(screen.getByRole('list', { name: 'Your addresses' })).getAllByRole('listitem')
  const card = (label: string) => cards().find((item) => within(item).queryByRole('heading', { name: label }))!

  async function openAddressBook() {
    logInAs()
    const rendered = renderApp('/account/addresses')
    await screen.findByRole('list', { name: 'Your addresses' })
    return rendered
  }

  it('lists the addresses, the default one marked', async () => {
    await openAddressBook()
    expect(screen.getByText(/2 of 10 addresses saved/)).toBeInTheDocument()
    expect(within(card('Home')).getByText('Default')).toBeInTheDocument()
    expect(within(card('Work')).queryByText('Default')).not.toBeInTheDocument()
  })

  it('adds an address', async () => {
    const { user } = await openAddressBook()
    await user.click(screen.getByRole('button', { name: 'Add a new address' }))
    await user.click(screen.getByRole('radio', { name: 'Other' }))
    await user.type(screen.getByLabelText('Flat, house no., building'), '7, Lake Road')
    await user.type(screen.getByLabelText('City'), 'Bengaluru')
    await user.type(screen.getByLabelText('State'), 'Karnataka')
    await user.type(screen.getByLabelText('PIN code'), '560071')
    await user.click(screen.getByRole('button', { name: 'Save address' }))

    expect(await screen.findByText('Address saved')).toBeInTheDocument()
    expect(within(card('Other')).getByText('7, Lake Road')).toBeInTheDocument()
    expect(screen.getByText(/3 of 10 addresses saved/)).toBeInTheDocument()
  })

  it('changes an address and makes another one the default', async () => {
    const { user } = await openAddressBook()
    await user.click(within(card('Work')).getByRole('button', { name: 'Edit' }))
    const landmark = screen.getByLabelText('Landmark (optional)')
    await user.type(landmark, 'Opposite the food court')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('Address updated')).toBeInTheDocument()
    expect(within(card('Work')).getByText('Opposite the food court')).toBeInTheDocument()

    await user.click(within(card('Work')).getByRole('button', { name: 'Make default' }))
    await waitFor(() => expect(within(card('Work')).getByText('Default')).toBeInTheDocument())
    expect(within(card('Home')).queryByText('Default')).not.toBeInTheDocument()
  })

  it('deletes an address after asking', async () => {
    const { user } = await openAddressBook()
    await user.click(within(card('Work')).getByRole('button', { name: 'Delete' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Delete this address?' })
    expect(dialog).toHaveTextContent('Work: Tower B, 5th Floor, Tech Park')
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText('Address deleted')).toBeInTheDocument()
    expect(cards()).toHaveLength(1)
  })
})

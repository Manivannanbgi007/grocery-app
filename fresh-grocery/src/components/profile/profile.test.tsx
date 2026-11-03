import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { logInAs, renderApp } from '../../test/render'

/* customer1 = Priya Sharma; vendor1 = Ravi Kumar of Fresh Mart. */

describe('Profile card', () => {
  it("shows a customer's details and pages in the shop, and closes with Escape", async () => {
    logInAs()
    const { user } = renderApp('/')
    const button = await screen.findByRole('button', { name: 'Account of Priya Sharma' })
    await user.click(button)

    const card = screen.getByRole('region', { name: 'Priya Sharma' })
    expect(within(card).getByText('customer1@grocery.com')).toBeInTheDocument()
    expect(within(card).getByText('Customer')).toBeInTheDocument()
    expect(within(card).getByText('Member since')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'Notifications' })).toHaveAttribute('href', '/account/notifications')
    expect(within(card).getByRole('link', { name: 'My orders' })).toHaveAttribute('href', '/orders')

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('region', { name: 'Priya Sharma' })).not.toBeInTheDocument()
    expect(button).toHaveFocus()
  })

  it("shows the vendor's store in the panel, and logs out from the card", async () => {
    logInAs('vendor1@grocery.com')
    const { user, router } = renderApp('/vendor')
    await user.click(await screen.findByRole('button', { name: 'Account of Ravi Kumar' }, { timeout: 10_000 }))

    const card = screen.getByRole('region', { name: 'Ravi Kumar' })
    expect(within(card).getByText('Vendor')).toBeInTheDocument()
    expect(within(card).getByText('Fresh Mart')).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'Store settings' })).toHaveAttribute('href', '/vendor/store')

    await user.click(within(card).getByRole('button', { name: 'Log out' }))
    // The login page is loaded on first use: the address changes once it is there
    await waitFor(() => expect(router.state.location.pathname).toBe('/vendor/login'))
    expect(router.state.location.search).toBe('')
  })
})

import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { listZones } from '../../mocks/mockDelivery'
import { publicStores } from '../../mocks/mockStores'
import { logInAs, renderApp } from '../../test/render'

/*
 * The redesigned admin list pages (O18), on the mock API: their summary cards show real numbers (the counts of the
 * lists, or sums of the data on the page) and open their tab or filter; the existing tabs and query parameters stay.
 */

/** A summary card's value: the label is a <p>, the value comes right after it. */
const tile = (label: string) => screen.getByText(label, { selector: 'p' }).nextElementSibling

describe('Admin list pages', () => {
  it('counts the stores per status, opens a tab from its card, and keeps ?tab=all', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/stores?tab=all')
    const numbers = await screen.findByRole('region', { name: 'Stores in numbers' }, { timeout: 10_000 })
    await waitFor(() => expect(tile('Active')).toHaveTextContent(String(publicStores().length)))
    expect(within(numbers).getByRole('link', { name: /^Active/ })).toHaveAttribute('href', '/admin/stores?tab=active')
    expect(within(screen.getByRole('navigation', { name: 'Store status' })).getByRole('link', { name: 'All' })).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByRole('table', { name: /All stores/ })).toBeInTheDocument()
  })

  it('lists every payment method with its statuses, a subtotal per method and the total', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/payments')
    const table = await screen.findByRole('table', { name: 'Payments by method and status' }, { timeout: 10_000 })
    // The row headers: one per method (its group), then the total
    const headers = within(table)
      .getAllByRole('rowheader')
      .map((cell) => cell.textContent)
    expect(headers).toContain('Cash on delivery')
    expect(headers.at(-1)).toBe('All methods')
    // Order 901's cash, as a status in words (colour and icon are extra)
    expect(within(table).getByText('Cash collected')).toBeInTheDocument()
  })

  it('opens the deliveries and the partners of a state from their cards', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/deliveries')
    const deliveries = await screen.findByRole('region', { name: 'Deliveries in numbers' }, { timeout: 10_000 })
    expect(within(deliveries).getByRole('link', { name: /^Delivered/ })).toHaveAttribute('href', '/admin/deliveries?tab=delivered')
    expect(within(deliveries).getByRole('link', { name: /^Failed/ })).toHaveAttribute('href', '/admin/deliveries?tab=failed')
  })

  it('shows each partner online or offline and links the KYC states', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/partners?tab=all')
    const partners = await screen.findByRole('region', { name: 'Delivery partners in numbers' }, { timeout: 10_000 })
    expect(within(partners).getByRole('link', { name: /^Approved/ })).toHaveAttribute('href', '/admin/partners?tab=approved')
    const table = await screen.findByRole('table', { name: /All delivery partners/ })
    // Every row says "Online" or "Offline" in words (the dot is decoration)
    for (const row of within(table).getAllByRole('row').slice(1)) {
      expect(row).toHaveTextContent(/Online|Offline/)
    }
  })

  it('works the zones out from the list on the page', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/zones')
    const zones = listZones()
    await waitFor(() => expect(tile('Zones')).toHaveTextContent(String(zones.length)))
    expect(tile('PIN codes')).toHaveTextContent(String(zones.reduce((sum, zone) => sum + zone.pincodes.length, 0)))
    expect(tile('In use')).toHaveTextContent(String(zones.filter((zone) => zone.active).length))
  })

  it('counts the banners per state, apart from the list the banners are in', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/banners')
    const list = await screen.findByRole('list', { name: 'Banners' }, { timeout: 10_000 })
    expect(tile('Live now')).toHaveTextContent(String(within(list).getAllByText('Live').length))
    expect(tile('Ended')).toHaveTextContent(String(within(list).queryAllByText('Ended').length))
  })

  it('opens the notifications of one email status from its card, and says which were read', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin/notifications')
    const numbers = await screen.findByRole('region', { name: 'Notifications in numbers' }, { timeout: 10_000 })
    const table = await screen.findByRole('table', { name: /Notifications, newest first/ })
    for (const row of within(table).getAllByRole('row').slice(1)) {
      expect(row).toHaveTextContent(/Read|Not read yet/)
    }
    await user.click(within(numbers).getByRole('link', { name: /^Emails sent/ }))
    await waitFor(() => expect(router.state.location.search).toBe('?email=SENT'))
  })
})

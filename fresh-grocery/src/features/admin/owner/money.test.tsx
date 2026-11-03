import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { allOrders } from '../../../mocks/mockOrders'
import { switchFlag } from '../../../mocks/mockPlatform'
import { storeById } from '../../../mocks/mockStores'
import { chooseOption } from '../../../test/pickers'
import { logInAs, renderApp } from '../../../test/render'
import { formatFullDay } from '../../../utils/calendar'
import { indiaDay } from '../../../utils/dates'
import { formatPrice } from '../../../utils/format'

/*
 * The super admin's commission report, a store's commission and the audit log (mock API). Order 901 of Fresh Mart
 * (commission 10 %) was delivered 3 days ago, so it is in the default period (the last 30 days).
 */

const order901Items = () => allOrders().find((order) => order.id === 901)!.amounts.itemsTotal

describe('Commission', () => {
  it('shows the commission per store on the delivered orders', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/commission')
    const table = await screen.findByRole('table', { name: /Commission per store/ }, { timeout: 10_000 })
    const freshMart = within(table).getByRole('row', { name: /Fresh Mart/ })
    const commission = Math.round(order901Items() * 10) / 100
    expect(freshMart).toHaveTextContent(formatPrice(commission))
    expect(freshMart).toHaveTextContent('10%')
    expect(screen.getByText('Platform commission').nextElementSibling).toHaveTextContent(formatPrice(commission))

    // The report-service refuses a period in the future: the calendar does not go past today
    await user.click(screen.getByLabelText('From'))
    await user.keyboard('{PageDown}{PageDown}')
    const calendar = screen.getByRole('dialog', { name: /choose a date/ })
    expect(within(calendar).getByRole('button', { name: formatFullDay(indiaDay()) })).toHaveFocus()
    expect(within(calendar).getByRole('button', { name: 'Next month' })).toBeDisabled()
  })

  it("changes a store's commission on the store page", async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/stores/1')
    const card = await screen.findByRole('region', { name: 'Platform commission' }, { timeout: 10_000 })
    const box = within(card).getByLabelText('Commission')
    expect(box).toHaveValue('10')

    await user.clear(box)
    await user.type(box, '60')
    await user.click(within(card).getByRole('button', { name: 'Save' }))
    expect(within(card).getByText('Use a number from 0 to 50, with at most two decimals.')).toBeInTheDocument()

    await user.clear(box)
    await user.type(box, '12.5')
    await user.click(within(card).getByRole('button', { name: 'Save' }))
    expect(await screen.findByText('Fresh Mart now pays 12.5% commission on new orders')).toBeInTheDocument()
    expect(storeById(1)?.commissionPercentage).toBe(12.5)
    expect(await screen.findByText('12.5% of each order')).toBeInTheDocument()
  })

  it('shows no commission editor to an admin', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/stores/1')
    expect(await screen.findByRole('heading', { level: 1, name: 'Fresh Mart' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Platform commission' })).not.toBeInTheDocument()
  })
})

describe('Audit log', () => {
  it('lists the events newest first and filters them', async () => {
    switchFlag(1, 'DELIVERY_SLOTS', true)
    logInAs('superadmin@grocery.com')
    const { user, router } = renderApp('/admin/audit-log')
    const list = await screen.findByRole('list', { name: 'Audit entries' }, { timeout: 10_000 })
    const first = within(list).getAllByRole('listitem')[0]
    expect(within(first).getByRole('heading')).toHaveTextContent('Feature flag changed')
    expect(first).toHaveTextContent('Platform changes')
    expect(within(first).getByRole('link', { name: 'Account #1' })).toHaveAttribute('href', '/admin/accounts/1')
    await user.click(within(first).getByText('Details'))
    expect(within(first).getByText(/"enabled": true/)).toBeVisible()

    await chooseOption(user, screen.getByRole('combobox', { name: 'Source' }), 'Stores')
    expect(router.state.location.search).toBe('?source=grocery.store.events')
    // Only the stores' event types are offered now
    await user.click(screen.getByRole('combobox', { name: 'Event type' }))
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual([
      'All events',
      'Store registered',
      'Store approved',
      'Store status changed',
    ])
    await user.keyboard('{Escape}')
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Audit entries' })).getAllByRole('listitem')).toHaveLength(2))

    await user.type(screen.getByLabelText('Search by reference'), '2{Enter}')
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Audit entries' })).getAllByRole('listitem')).toHaveLength(1))
    expect(screen.getByRole('list', { name: 'Audit entries' })).toHaveTextContent('Store #2')
  })

  it('says when nothing matches', async () => {
    logInAs('superadmin@grocery.com')
    renderApp('/admin/audit-log?day=2026-02-01')
    expect(await screen.findByRole('heading', { name: 'No entries match these filters' }, { timeout: 10_000 })).toBeInTheDocument()
  })
})

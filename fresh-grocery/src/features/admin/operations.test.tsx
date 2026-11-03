import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { assignDelivery, searchDeliveries } from '../../mocks/mockDelivery'
import { listNotifications } from '../../mocks/mockNotifications'
import { acceptOrder, adminGetOrder, getOrder, markReadyForPickup, packOrder, placeOrder } from '../../mocks/mockOrders'
import { chooseOption } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'

/*
 * Orders, payments and deliveries in the admin panel (mock API). The seed orders: 901 of customer1 (delivered by Arjun
 * three days ago, ₹177 cash), 902 of customer1 (cancelled), 903 of customer3 (₹196 cash, waiting for Fresh Mart).
 */

const rows = (table: HTMLElement) => within(table).getAllByRole('row').slice(1)

/** Fresh Mart accepts, packs and hands over order 903: a delivery job waits for a partner. */
function orderReadyForPickup(orderId = 903) {
  acceptOrder(1, orderId)
  packOrder(1, orderId)
  markReadyForPickup(1, orderId)
}

describe('Admin orders', () => {
  it('lists every order, newest first, by status, store and customer', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin/orders')
    const table = await screen.findByRole('table', { name: /All orders/ }, { timeout: 10_000 })
    expect(rows(table).map((row) => within(row).getAllByRole('link')[0].textContent)).toEqual(['Order #903', 'Order #902', 'Order #901'])

    await user.click(screen.getByRole('link', { name: 'Waiting for the store' }))
    await waitFor(() => expect(rows(screen.getByRole('table', { name: /Waiting for the store orders/ }))).toHaveLength(1))

    await user.click(screen.getByRole('link', { name: 'All' }))
    await user.type(screen.getByLabelText('Orders of one customer account'), '4{Enter}')
    await waitFor(() => expect(router.state.location.search).toBe('?customer=4'))
    await waitFor(() => expect(rows(screen.getByRole('table', { name: /All orders/ }))).toHaveLength(2))
    await chooseOption(user, screen.getByRole('combobox', { name: 'Store' }), 'Green Basket')
    expect(await screen.findByText('No orders match these filters')).toBeInTheDocument()
  })

  it('cancels a problem order with a reason; the customer is told', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/orders/903')
    expect(await screen.findByRole('heading', { name: 'Waiting for the store' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('Note: “Please call before coming”')).toBeInTheDocument()
    expect(screen.getByText('Cash on delivery: the delivery partner collects ₹196')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Fresh Mart' })).toHaveAttribute('href', '/admin/stores/1')

    await user.click(screen.getByRole('button', { name: 'Cancel order' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Cancel order #903?' })
    await user.click(within(dialog).getByRole('button', { name: 'Cancel order' }))
    expect(within(dialog).getByText('Please give the reason: the customer and the store see it.')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'The store cannot deliver it' }))
    await user.click(within(dialog).getByRole('button', { name: 'Cancel order' }))

    expect(await screen.findByText('Order #903 cancelled')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Cancelled' })).toBeInTheDocument()
    expect(screen.getByText('Cancelled by an admin: “The store cannot deliver it”.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
    expect(adminGetOrder(903).cancelledBy).toBe('ADMIN')
    expect(listNotifications(9, false)[0].title).toBe('Order #903 cancelled')
  })

  it('shows a delivered order with its invoice and the history from the staff side', async () => {
    logInAs('admin@grocery.com')
    renderApp('/admin/orders/901')
    expect(await screen.findByRole('heading', { name: 'Delivered' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Invoice' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
    const history = screen.getByRole('heading', { name: 'History' }).parentElement!
    expect(within(history).getByText('On the way to the customer with Arjun Singh')).toBeInTheDocument()
    expect(within(history).queryByText('The store accepted your order')).not.toBeInTheDocument()
  })
})

describe('Admin payments', () => {
  it('sums up the money of the period and filters the payments', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/payments')
    expect(await screen.findByRole('heading', { name: 'Money' }, { timeout: 10_000 })).toBeInTheDocument()
    const tile = (label: string) => screen.getByText(label, { selector: 'p' }).nextElementSibling
    // Only order 901 was paid: ₹177 in cash, three days ago
    await waitFor(() => expect(tile('Cash collected')).toHaveTextContent('₹177'))
    expect(tile('Received')).toHaveTextContent('₹177')
    expect(tile('Refunded')).toHaveTextContent('₹0')

    const table = await screen.findByRole('table', { name: /Payments, newest first/ })
    expect(rows(table)).toHaveLength(3)
    await chooseOption(user, screen.getByRole('combobox', { name: 'Payment status' }), 'Cash collected')
    await waitFor(() => expect(rows(screen.getByRole('table', { name: /Payments, newest first/ }))).toHaveLength(1))
    expect(rows(screen.getByRole('table', { name: /Payments, newest first/ }))[0]).toHaveTextContent('Order #901')
  })
})

describe('Admin deliveries', () => {
  it('gives a delivery nobody took to a partner; the customer sees who brings it', async () => {
    orderReadyForPickup()
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/deliveries')

    const menu = await screen.findByRole('navigation', { name: 'Admin panel' })
    await waitFor(() => expect(within(menu).getByRole('link', { name: 'Deliveries, 1 waiting for a partner' })).toBeInTheDocument())
    const table = await screen.findByRole('table', { name: /Waiting for a partner deliveries/ })
    expect(rows(table)[0]).toHaveTextContent('Anita Desai')
    expect(rows(table)[0]).toHaveTextContent('₹196')

    await user.click(within(table).getByRole('button', { name: 'Assign a partner to order #903' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Assign order #903' })
    await user.click(within(dialog).getByRole('button', { name: 'Assign' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Choose a partner.')
    await user.click(await within(dialog).findByRole('radio', { name: /Arjun Singh/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Assign' }))

    expect(await screen.findByText('Order #903 assigned to Arjun Singh')).toBeInTheDocument()
    expect(await screen.findByText('Every delivery has a partner.')).toBeInTheDocument()
    expect(searchDeliveries('ASSIGNED', 5)).toHaveLength(1)
    expect(adminGetOrder(903).deliveryPartnerName).toBe('Arjun Singh')
    expect(listNotifications(9, false)[0].title).toBe('A delivery partner for order #903')
  })

  it('says so when the partner is busy with another delivery', async () => {
    // Two orders are ready: 903, and a new one of customer1 (the seed cart); Arjun already has 903
    orderReadyForPickup(903)
    const second = placeOrder(4, { addressId: 1, paymentMethod: 'COD', note: null })
    getOrder(4, second.id) // the mock confirms a new order the next time it is read
    orderReadyForPickup(second.id)
    const first = searchDeliveries('WAITING_FOR_PARTNER', null).find((delivery) => delivery.orderId === 903)!
    assignDelivery(first.id, 5)

    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/deliveries')
    const table = await screen.findByRole('table', { name: /Waiting for a partner deliveries/ }, { timeout: 10_000 })
    await user.click(within(table).getByRole('button', { name: `Assign a partner to order #${second.id}` }))
    const dialog = screen.getByRole('alertdialog', { name: `Assign order #${second.id}` })
    await user.click(await within(dialog).findByRole('radio', { name: /Arjun Singh/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Assign' }))

    // The dialog stays open with the backend's message, so another partner can be chosen
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Partner 5 is busy with another delivery')
    await user.click(within(dialog).getByRole('radio', { name: /Karthik Reddy/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Assign' }))
    expect(await screen.findByText(`Order #${second.id} assigned to Karthik Reddy`)).toBeInTheDocument()
  })
})

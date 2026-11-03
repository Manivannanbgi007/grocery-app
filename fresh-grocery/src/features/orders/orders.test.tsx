import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { placeOrder } from '../../mocks/mockOrders'
import { logInAs, renderApp } from '../../test/render'

/* customer1 (Priya) has two older mock orders: 901 delivered (₹177) and 902 cancelled. */

const orderCards = () => within(screen.getByRole('list', { name: 'Your orders' })).getAllByRole('listitem')

describe('My orders page', () => {
  it('sends a guest to the login page first', async () => {
    const { router } = renderApp('/orders')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?next=%2Forders')
  })

  it('lists the orders, newest first, with status, total and a link to each', async () => {
    placeOrder(4, { addressId: 1, paymentMethod: 'COD', note: null })
    logInAs()
    renderApp('/orders')

    await screen.findByRole('list', { name: 'Your orders' })
    expect(screen.getByText('3 orders')).toBeInTheDocument()
    expect(orderCards().map((card) => within(card).getByRole('heading').textContent)).toEqual(['Order #1001', 'Order #902', 'Order #901'])
    expect(orderCards()[0]).toHaveTextContent('Confirmed')
    expect(orderCards()[0]).toHaveTextContent('₹231')
    expect(orderCards()[2]).toHaveTextContent('Delivered')
    expect(orderCards()[2]).toHaveTextContent('4 items · Cash on delivery')
    expect(screen.getByRole('link', { name: 'View order #901' })).toHaveAttribute('href', '/orders/901')
  })

  it('filters by status through the URL', async () => {
    logInAs()
    const { user, router } = renderApp('/orders')
    await screen.findByRole('list', { name: 'Your orders' })

    await user.click(within(screen.getByRole('navigation', { name: 'Filter orders' })).getByRole('link', { name: 'Cancelled' }))
    expect(router.state.location.search).toBe('?status=CANCELLED')
    expect(await screen.findByText('1 order')).toBeInTheDocument()
    expect(orderCards()).toHaveLength(1)
    expect(orderCards()[0]).toHaveTextContent('Order #902')
  })

  it('invites a customer without orders to start shopping', async () => {
    logInAs('customer2@grocery.com')
    renderApp('/orders')
    expect(await screen.findByRole('heading', { name: 'No orders yet' })).toBeInTheDocument()
    expect(within(screen.getByRole('main')).getByRole('link', { name: 'Start shopping' })).toHaveAttribute('href', '/shop')
  })

  it('is reached from the account menu', async () => {
    logInAs()
    const { user, router } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Account of Priya Sharma' }))
    await user.click(screen.getByRole('link', { name: 'My orders' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'My orders' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders')
  })
})

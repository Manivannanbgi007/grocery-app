import { act, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { Delivery } from '../../api/types'
import { addItem } from '../../mocks/mockCarts'
import { acceptDelivery, deliverDelivery, deliveryOfOrder, goOnline, pickUpDelivery, startTrip } from '../../mocks/mockDelivery'
import { acceptOrder, markReadyForPickup, packOrder, placeOrder } from '../../mocks/mockOrders'
import { updateLocation } from '../../mocks/mockTracking'
import { server } from '../../mocks/server'
import { logInAs, renderApp } from '../../test/render'

/*
 * Live delivery tracking on the mock API. customer1 (Priya, user 4) orders her seed cart from Fresh Mart to her home
 * (order 1001); delivery1 (Arjun Singh, user 5, phone 9000000005) delivers it. The mock has no routing service, so there
 * is no arrival time: the page shows the straight-line distance instead.
 */

/** Priya orders (cash on delivery): order 1001, confirmed at once. */
function priyaOrders() {
  return placeOrder(4, { addressId: 1, paymentMethod: 'COD', note: null })
}

/** Fresh Mart hands the order over, and Arjun (online) gets the delivery and accepts it. */
function arjunAccepts(orderId: number): Delivery {
  goOnline(5)
  acceptOrder(1, orderId)
  packOrder(1, orderId)
  markReadyForPickup(1, orderId)
  const delivery = deliveryOfOrder(orderId)!
  acceptDelivery(5, delivery.id)
  return delivery
}

/** What happens on Arjun's side (a position, a step) reaches the page through the live stream. */
function onArjunsSide(change: () => void) {
  act(change)
}

describe('Live tracking of an order', () => {
  it('follows the partner on the map, step by step, until the order is delivered', async () => {
    const order = priyaOrders()
    const delivery = arjunAccepts(order.id)
    logInAs()
    renderApp(`/orders/${order.id}/track`)

    expect(await screen.findByRole('heading', { name: 'Arjun is on the way to Fresh Mart' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('Live')).toBeInTheDocument()
    expect(screen.getByText('Waiting for Arjun’s location…')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Call Arjun: 9000000005' })).toHaveAttribute('href', 'tel:9000000005')
    // The map (loaded on demand) shows the store and the address, with OpenStreetMap's attribution
    expect(await screen.findByRole('region', { name: 'Delivery map: the store, your address and the delivery partner' })).toBeInTheDocument()
    expect(await screen.findByTitle('Fresh Mart (pickup)')).toBeInTheDocument()
    expect(screen.getByTitle('Your address')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'OpenStreetMap' })).toHaveAttribute('href', 'https://www.openstreetmap.org/copyright')

    // Arjun's phone sends a position (980 m south of the store): he appears on the map
    onArjunsSide(() => updateLocation(delivery, { latitude: 12.9696, longitude: 77.6408, accuracyMeters: 8, recordedAt: new Date().toISOString() }))
    expect(await screen.findByText('Arjun is about 980 m away from the store (straight line)')).toBeInTheDocument()
    expect(screen.getByText('Location updated just now')).toBeInTheDocument()
    expect(await screen.findByTitle('Arjun Singh, just now')).toBeInTheDocument()
    expect(screen.queryByText('Waiting for Arjun’s location…')).not.toBeInTheDocument()

    onArjunsSide(() => pickUpDelivery(5, delivery.id))
    expect(await screen.findByRole('heading', { name: 'Arjun has picked up your order' })).toBeInTheDocument()
    onArjunsSide(() => startTrip(5, delivery.id))
    expect(await screen.findByRole('heading', { name: 'Arjun is on the way to you' })).toBeInTheDocument()
    expect(screen.getByText(/^Arjun is about .+ away from you \(straight line\)$/)).toBeInTheDocument()

    // Delivered: the last state stays on the page, the live updates end, and there is no one to call any more
    onArjunsSide(() => deliverDelivery(5, delivery.id))
    expect(await screen.findByRole('heading', { name: 'Delivered' })).toBeInTheDocument()
    expect(screen.getByText('Finished')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Call Arjun/ })).not.toBeInTheDocument()
    expect(screen.queryByTitle(/^Arjun Singh/)).not.toBeInTheDocument()
  })

  it('explains that tracking starts once the store hands the order over', async () => {
    const order = priyaOrders()
    logInAs()
    renderApp(`/orders/${order.id}/track`)

    expect(await screen.findByRole('heading', { name: 'Live tracking starts soon' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText(/as soon as Fresh Mart hands it to a delivery partner/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /Delivery map/ })).not.toBeInTheDocument()
  })

  it('says so when the live connection is lost, and keeps trying', async () => {
    const order = priyaOrders()
    arjunAccepts(order.id)
    server.use(http.get('*/api/v1/delivery-tracking/orders/:orderId/stream', () => HttpResponse.error()))
    logInAs()
    renderApp(`/orders/${order.id}/track`)

    expect(await screen.findByText('Reconnecting…', {}, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByText('Connecting to live tracking…')).toBeInTheDocument()
  })

  it("does not track another customer's order", async () => {
    const order = priyaOrders()
    arjunAccepts(order.id)
    logInAs('customer2@grocery.com')
    renderApp(`/orders/${order.id}/track`)

    expect(await screen.findByRole('heading', { name: 'We could not find this order' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.queryByText('Live')).not.toBeInTheDocument()
  })

  it('is opened from the order page once the order is ready for pickup', async () => {
    const order = priyaOrders()
    arjunAccepts(order.id)
    logInAs()
    const { user, router } = renderApp(`/orders/${order.id}`)

    await user.click(await screen.findByRole('link', { name: 'Track on the map' }, { timeout: 10_000 }))
    expect(router.state.location.pathname).toBe(`/orders/${order.id}/track`)
    expect(await screen.findByRole('heading', { name: 'Arjun is on the way to Fresh Mart' })).toBeInTheDocument()
  })
})

describe('Finding the tracking', () => {
  it('has "Track my order" in the account menu', async () => {
    logInAs()
    const { user } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Account of Priya Sharma' }, { timeout: 10_000 }))
    const card = screen.getByRole('region', { name: 'Priya Sharma' })
    expect(within(card).getByRole('link', { name: 'Track my order' })).toHaveAttribute('href', '/orders/track')
  })

  it('says so when no order is on its way', async () => {
    logInAs() // Priya's seed orders are delivered and cancelled
    renderApp('/orders/track')
    const empty = (await screen.findByRole('heading', { name: 'No order is on its way' }, { timeout: 10_000 })).parentElement!
    expect(within(empty).getByRole('link', { name: 'My orders' })).toHaveAttribute('href', '/orders')
  })

  it('goes straight to the tracking of the one open order', async () => {
    const order = priyaOrders()
    logInAs()
    const { router } = renderApp('/orders/track')
    expect(await screen.findByRole('heading', { name: 'Live tracking starts soon' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe(`/orders/${order.id}/track`)
  })

  it('lets the customer choose between several open orders, also on My orders', async () => {
    const first = priyaOrders()
    arjunAccepts(first.id) // handed over: on the map
    addItem(4, 2, 3, false) // 3 onions: more than the minimum order of ₹99
    const second = priyaOrders() // still at the store
    logInAs()
    const { router } = renderApp('/orders/track')

    expect(await screen.findByRole('heading', { name: 'Which order do you want to follow?' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: `Track on the map (order #${first.id})` })).toHaveAttribute('href', `/orders/${first.id}/track`)
    expect(screen.getByRole('link', { name: `Follow the order (order #${second.id})` })).toHaveAttribute('href', `/orders/${second.id}/track`)

    await act(() => router.navigate('/orders'))
    const onItsWay = await screen.findByRole('region', { name: 'On its way' })
    expect(within(onItsWay).getAllByRole('listitem')).toHaveLength(2)
    // The order card of the handed-over order has the button too
    const list = await screen.findByRole('list', { name: 'Your orders' })
    expect(within(list).getByRole('link', { name: `Track on the map (order #${first.id})` })).toBeInTheDocument()
    expect(within(list).queryByRole('link', { name: `Track on the map (order #${second.id})` })).not.toBeInTheDocument()
  })
})

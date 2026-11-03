import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { PlaceOrderRequest } from '../../api/orders'
import type { ChatAction } from '../../api/types'
import { ok } from '../../mocks/handlers/respond'
import { addItem } from '../../mocks/mockCarts'
import { cancelMyOrder, getOrder, listOrders, placeOrder } from '../../mocks/mockOrders'
import { server } from '../../mocks/server'
import { logInAs, renderApp, renderWithProviders } from '../../test/render'
import { CARD_LIFETIME_MS, ChatActionCard } from './ChatActionCard'

/*
 * "Place my order" and "Cancel my order" in the shop's chat (mock assistant): the answer shows a summary with a Confirm
 * button, and only the customer's click places or cancels the order. customer1 = Priya (user 4): her seed cart costs
 * ₹231; none of her seed orders can still be cancelled.
 */

const PRIYA = 4

/** Priya's orders placed during the test (the seed orders have smaller numbers) */
const newOrders = () => listOrders(PRIYA, null).filter((order) => order.id >= 1001)

async function askForTheOrder(question = 'Place my order, cash on delivery') {
  logInAs('customer1@grocery.com')
  const { user } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  const panel = screen.getByRole('dialog', { name: 'Shopping assistant' })
  await user.type(within(panel).getByLabelText('Your question'), `${question}{Enter}`)
  const card = await within(panel).findByRole('region', { name: 'Order summary' })
  return { user, panel, card }
}

/** order-service answers the order with this paymentUrl (undefined: without the field, like an older order-service) */
function orderServiceSendsPaymentUrl(paymentUrl: string | undefined) {
  server.use(
    http.post('*/api/v1/orders', async ({ request }) => {
      const order = placeOrder(PRIYA, (await request.json()) as PlaceOrderRequest)
      return ok({ ...order, paymentUrl }, 'Order placed. We are reserving your items and confirming the payment.', 201)
    }),
  )
}

describe('Ordering in the chat', () => {
  it('places the order only when the customer presses Confirm', async () => {
    const { user, panel, card } = await askForTheOrder()
    expect(within(card).getByText('₹231')).toBeInTheDocument()
    expect(within(card).getByText('Cash on delivery')).toBeInTheDocument()
    expect(newOrders()).toHaveLength(0) // the answer alone orders nothing

    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))

    expect(await within(panel).findByText('Order #1001 placed. Please pay ₹231 in cash when it is delivered.')).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: 'Order #1001' })).toHaveAttribute('href', '/orders/1001')
    expect(within(panel).queryByRole('link', { name: 'Pay Now' })).not.toBeInTheDocument() // nothing to pay online
    expect(within(card).getByText('Confirmed: the order is placed.')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument()
    expect(newOrders()).toHaveLength(1)
    expect(await screen.findByRole('link', { name: 'Cart, 0 items' })).toBeInTheDocument()
  })

  it('shows Pay Now after an online order, and it opens the payment on the page order-service named', async () => {
    const { user, panel, card } = await askForTheOrder('Place my order, pay online')

    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))

    expect(
      await within(panel).findByText('Order #1001 placed. Please pay ₹231 online on the order page within 15 minutes; the order is confirmed after the payment.'),
    ).toBeInTheDocument()
    const payNow = within(panel).getByRole('link', { name: 'Pay Now' })
    expect(payNow).toHaveAttribute('href', '/orders/1001') // the mock order-service's paymentUrl
    expect(within(panel).queryByRole('alert')).not.toBeInTheDocument()

    await user.click(payNow)

    // The order page is loaded on first use (lazy route): slower while other test files run
    expect(await screen.findByRole('heading', { level: 1, name: 'Order #1001' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Pay ₹231' }, { timeout: 5_000 })).toBeInTheDocument()
  })

  it('shows Pay Now for an order that waits for its payment when the answer brings its link ("proceed to pay" again)', async () => {
    // mcp-client-service: the cart is empty, order #1001 waits for its online payment (order-service's own paymentUrl)
    server.use(
      http.post('*/api/v1/chat/messages', () =>
        ok({
          conversationId: '3f0c8a8e-2d6b-4c1e-9a43-8f1f4f3c2b10',
          answer: 'Order #1001 is already placed and waits for your online payment of ₹231. Please press Pay Now to pay it; nothing new was ordered.',
          grounded: true,
          toolsUsed: ['prepareMyOrder'],
          links: [{ label: 'Order #1001', path: '/orders/1001' }],
          action: null,
          paymentUrl: '/orders/1001',
        }, 'Answer ready'),
      ),
    )
    logInAs('customer1@grocery.com')
    const { user } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
    const panel = screen.getByRole('dialog', { name: 'Shopping assistant' })

    await user.type(within(panel).getByLabelText('Your question'), 'Proceed to pay{Enter}')

    expect(await within(panel).findByRole('link', { name: 'Pay Now' })).toHaveAttribute('href', '/orders/1001')
    expect(within(panel).queryByRole('region', { name: 'Order summary' })).not.toBeInTheDocument() // no second order
  })

  it('says how to pay instead of a Pay Now button when order-service sent no payment link', async () => {
    orderServiceSendsPaymentUrl(undefined)
    const { user, panel, card } = await askForTheOrder('Place my order, pay online')

    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))

    expect(await within(panel).findByRole('alert')).toHaveTextContent('The payment link is not available. Please open the order from My orders to pay.')
    expect(within(panel).queryByRole('link', { name: 'Pay Now' })).not.toBeInTheDocument()
    expect(newOrders()).toHaveLength(1) // the order itself is placed
  })

  it('shows no Pay Now button for a payment link that is not a page of the shop', async () => {
    orderServiceSendsPaymentUrl('https://pay.example.com/orders/1001')
    const { user, panel, card } = await askForTheOrder('Place my order, pay online')

    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))

    expect(await within(panel).findByRole('alert')).toHaveTextContent('The payment link is not available.')
    expect(within(panel).queryByRole('link', { name: 'Pay Now' })).not.toBeInTheDocument()
  })

  it('places nothing after Cancel', async () => {
    const { user, panel, card } = await askForTheOrder()

    await user.click(within(card).getByRole('button', { name: 'Cancel' }))

    expect(await within(panel).findByText('OK, nothing was ordered. Your cart is unchanged.')).toBeInTheDocument()
    expect(within(card).getByText('Cancelled: nothing was ordered.')).toBeInTheDocument()
    expect(newOrders()).toHaveLength(0)
  })

  it('places nothing when the cart changed after the summary, and says why', async () => {
    const { user, card } = await askForTheOrder()
    addItem(PRIYA, 1, 1, false) // one more tomato, e.g. in another tab

    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))

    expect(await within(card).findByRole('alert')).toHaveTextContent(
      'Your cart or its prices changed since the summary. Please check the new total. Ask me again for a new summary.',
    )
    expect(within(card).queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument()
    expect(newOrders()).toHaveLength(0)
  })

  it('sends the same Idempotency-Key when Confirm is pressed again after an error, so it is one order', async () => {
    const keys: (string | null)[] = []
    let failFirst = true
    server.use(
      http.post('*/api/v1/orders', ({ request }) => {
        keys.push(request.headers.get('Idempotency-Key'))
        if (failFirst) {
          failFirst = false
          return HttpResponse.json({ success: false, message: 'The order service is busy. Please try again.', data: null, errors: [] }, { status: 503 })
        }
        return undefined // on to the mock order service
      }),
    )
    const { user, panel, card } = await askForTheOrder()

    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))
    expect(await within(card).findByRole('alert')).toHaveTextContent('The order service is busy. Please try again.')
    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))

    expect(await within(panel).findByText(/Order #1001 placed/)).toBeInTheDocument()
    expect(keys).toHaveLength(2)
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/)
    expect(keys[1]).toBe(keys[0])
    expect(newOrders()).toHaveLength(1)
  })

  it('asks for a fresh summary after 10 minutes', () => {
    renderWithProviders(
      <ChatActionCard action={SUMMARY} actionKey="chat-card-0001" receivedAt={Date.now() - CARD_LIFETIME_MS} done={undefined} onDone={() => {}} />,
    )

    expect(screen.getByText('This summary is older than 10 minutes. Ask me again for a fresh one.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument()
  })
})

/** Priya places an order (number 1001, cash on delivery) and asks the chat to cancel it. */
async function askToCancel() {
  const placed = placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null })
  logInAs('customer1@grocery.com')
  const { user } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  const panel = screen.getByRole('dialog', { name: 'Shopping assistant' })
  await user.type(within(panel).getByLabelText('Your question'), 'Cancel my order{Enter}')
  const card = await within(panel).findByRole('region', { name: 'Order to cancel' })
  return { user, panel, card, orderId: placed.id }
}

describe('Cancelling an order in the chat', () => {
  it('cancels the order only when the customer presses Confirm cancellation', async () => {
    const { user, panel, card, orderId } = await askToCancel()
    expect(within(card).getByRole('heading', { name: `Cancel order #${orderId} from Fresh Mart?` })).toBeInTheDocument()
    expect(within(card).getByText('Nothing was paid yet (cash on delivery).')).toBeInTheDocument()
    expect(getOrder(PRIYA, orderId).status).not.toBe('CANCELLED') // the answer alone cancels nothing

    await user.click(within(card).getByRole('button', { name: 'Confirm cancellation' }))

    expect(await within(panel).findByText(`Order #${orderId} is cancelled.`)).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: `Order #${orderId}` })).toHaveAttribute('href', `/orders/${orderId}`)
    expect(within(card).getByText('Done: the order is cancelled.')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: 'Confirm cancellation' })).not.toBeInTheDocument()
    expect(getOrder(PRIYA, orderId).status).toBe('CANCELLED')
  })

  it('cancels nothing after Keep order', async () => {
    const { user, panel, card, orderId } = await askToCancel()

    await user.click(within(card).getByRole('button', { name: 'Keep order' }))

    expect(await within(panel).findByText(`OK, order #${orderId} stays as it is.`)).toBeInTheDocument()
    expect(within(card).getByText('Kept: nothing was cancelled.')).toBeInTheDocument()
    expect(getOrder(PRIYA, orderId).status).not.toBe('CANCELLED')
  })

  it('tells the assistant about an order placed with the card, so "cancel my last order" finds it in the same chat', async () => {
    const bodies: { confirmed?: unknown }[] = []
    server.use(
      http.post('*/api/v1/chat/messages', async ({ request }) => {
        bodies.push((await request.clone().json()) as { confirmed?: unknown })
        return undefined // on to the mock assistant
      }),
    )
    const { user, panel, card } = await askForTheOrder()
    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))
    await within(panel).findByText(/Order #1001 placed/)

    await user.type(within(panel).getByLabelText('Your question'), 'Cancel my last order{Enter}')
    const cancelCard = await within(panel).findByRole('region', { name: 'Order to cancel' })
    await user.type(within(panel).getByLabelText('Your question'), 'Thanks{Enter}')
    await within(panel).findByText(/reliable answer/) // the mock's answer to "Thanks"

    expect(within(cancelCard).getByRole('heading', { name: 'Cancel order #1001 from Fresh Mart?' })).toBeInTheDocument()
    expect(bodies.map((body) => body.confirmed)).toEqual([undefined, [{ kind: 'PLACE_ORDER', orderId: 1001 }], undefined])
  })

  it('says why when the order can no longer be cancelled, and offers no second try', async () => {
    const { user, card, orderId } = await askToCancel()
    cancelMyOrder(PRIYA, orderId, null) // e.g. cancelled on the order page in another tab

    await user.click(within(card).getByRole('button', { name: 'Confirm cancellation' }))

    expect(await within(card).findByRole('alert')).toHaveTextContent('An order that is CANCELLED can no longer be cancelled')
    expect(within(card).queryByRole('button', { name: 'Confirm cancellation' })).not.toBeInTheDocument()
  })
})

/** A summary of Priya's order, as the backend sends it */
const SUMMARY: ChatAction = {
  kind: 'PLACE_ORDER',
  items: [{ name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72' }],
  itemsTotal: '₹72',
  deliveryFee: '₹30',
  payable: '₹102',
  deliverTo: 'Home: Priya Sharma, 12, 5th Cross, Bengaluru 560038',
  payment: 'Cash on delivery',
  addressId: 1,
  paymentMethod: 'COD',
  checkoutFingerprint: 'mock-0000',
}

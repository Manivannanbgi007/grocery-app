import { screen, within } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { ok } from '../../mocks/handlers/respond'
import { getPayment, payWithMockGateway } from '../../mocks/mockOrders'
import { server } from '../../mocks/server'
import { logInAs, renderApp } from '../../test/render'

/*
 * The payment of an online order placed in the shop's chat (mock assistant and mock payment-service). Under the line
 * "Order #1001 placed ..." the chat shows the order, the amount, the payment's status and how to pay, with Pay Now; once
 * payment-service says the payment is made, "Payment of ₹231 received for order #1001.". customer1 = Priya (user 4):
 * her seed cart costs ₹231, and her first new order is #1001.
 */

const PRIYA = 4

/** "Place my order, pay online" and Confirm: returns the chat and the payment box under the "placed" line */
async function placeAnOnlineOrder() {
  logInAs('customer1@grocery.com')
  const { user } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  const panel = screen.getByRole('dialog', { name: 'Shopping assistant' })
  await user.type(within(panel).getByLabelText('Your question'), 'Place my order, pay online{Enter}')
  const card = await within(panel).findByRole('region', { name: 'Order summary' })
  await user.click(within(card).getByRole('button', { name: 'Confirm order' }))
  const payment = await within(panel).findByRole('region', { name: 'Payment of order #1001' })
  return { user, panel, payment }
}

/** The chat again, after a page of the shop closed it (on a narrow screen a link closes the chat) */
async function openTheChatAgain(user: Awaited<ReturnType<typeof placeAnOnlineOrder>>['user']) {
  const open = screen.queryByRole('dialog', { name: 'Shopping assistant' })
  if (open) return open
  await user.click(screen.getByRole('button', { name: 'Ask our assistant' }))
  return screen.getByRole('dialog', { name: 'Shopping assistant' })
}

describe('Paying an online order placed in the chat', () => {
  it('shows the order, the amount, the status and how to pay, with Pay Now', async () => {
    const { payment } = await placeAnOnlineOrder()

    expect(within(payment).getByText('#1001')).toBeInTheDocument()
    expect(await within(payment).findByText('Waiting for your payment')).toBeInTheDocument()
    expect(within(payment).getByText('₹231')).toBeInTheDocument()
    // payment-service runs the mock gateway here (gatewayOrderId "mock_order_1001")
    expect(within(payment).getByText('Test payment gateway (no real money)')).toBeInTheDocument()
    expect(within(payment).getByRole('link', { name: 'Pay Now' })).toHaveAttribute('href', '/orders/1001')
  })

  it('names Razorpay when payment-service opened a Razorpay order', async () => {
    server.use(
      http.get('*/api/v1/payments/orders/:orderId', ({ params }) =>
        ok({ ...getPayment(PRIYA, Number(params.orderId)), gatewayOrderId: 'order_N5x9Razorpay' }, 'Payment fetched successfully'),
      ),
    )
    const { payment } = await placeAnOnlineOrder()

    expect(await within(payment).findByText('Razorpay: UPI, cards, net banking or wallets')).toBeInTheDocument()
    expect(within(payment).getByRole('link', { name: 'Pay Now' })).toBeInTheDocument()
  })

  it('confirms the payment in the chat after the customer paid on the order page', async () => {
    const { user, payment } = await placeAnOnlineOrder()
    await within(payment).findByText('Waiting for your payment')

    await user.click(within(payment).getByRole('link', { name: 'Pay Now' }))
    await user.click(await screen.findByRole('button', { name: 'Pay ₹231' }, { timeout: 10_000 }))
    expect(await screen.findByText('Payment of ₹231 received')).toBeInTheDocument() // the order page's message

    const panel = await openTheChatAgain(user)
    const paid = within(panel).getByRole('region', { name: 'Payment of order #1001' })
    expect(await within(paid).findByText('Payment of ₹231 received for order #1001.')).toBeInTheDocument()
    expect(within(paid).queryByRole('link', { name: 'Pay Now' })).not.toBeInTheDocument() // nothing left to pay
  })

  it('shows the payment as received when payment-service confirms it later (e.g. Razorpay webhook)', async () => {
    const { payment } = await placeAnOnlineOrder()
    await within(payment).findByText('Waiting for your payment')

    // Not this page: payment-service marks it paid by itself (in the real shop: Razorpay's signed webhook)
    payWithMockGateway(PRIYA, 1001, true)

    // The chat asks payment-service again every 5 seconds while the payment waits
    expect(await within(payment).findByText('Payment of ₹231 received for order #1001.', {}, { timeout: 8_000 })).toBeInTheDocument()
  }, 15_000)

  it('says when the payment failed, and that no money was taken', async () => {
    const { user, payment } = await placeAnOnlineOrder()
    await within(payment).findByText('Waiting for your payment')

    await user.click(within(payment).getByRole('link', { name: 'Pay Now' }))
    await user.click(await screen.findByRole('button', { name: 'Decline (test)' }, { timeout: 10_000 }))

    const panel = await openTheChatAgain(user)
    const failed = within(panel).getByRole('region', { name: 'Payment of order #1001' })
    expect(
      await within(failed).findByText('The payment failed: The bank declined the payment (mock). Order #1001 is being cancelled; no money was taken.'),
    ).toBeInTheDocument()
    expect(within(failed).queryByRole('link', { name: 'Pay Now' })).not.toBeInTheDocument()
  })
})

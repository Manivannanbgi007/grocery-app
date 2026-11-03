import { act, screen, waitFor } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { ok } from '../../mocks/handlers/respond'
import { userFromRequest } from '../../mocks/mockAuth'
import { getPayment, payWithMockGateway, placeOrder } from '../../mocks/mockOrders'
import { server } from '../../mocks/server'
import { logInAs, renderApp } from '../../test/render'
import type { RazorpayOptions } from './razorpayCheckout'

/*
 * Paying with Razorpay Checkout (payment-service with PAYMENT_GATEWAY=razorpay). The mock API plays payment-service in
 * Razorpay mode (gateway order "order_RZP1001", the two Razorpay endpoints); a fake window.Razorpay plays checkout.js.
 * customer1 (Priya, user 4) orders her seed cart (₹231) online: order 1001.
 */

let checkoutOptions: RazorpayOptions | null = null
let onFailed: ((response: { error: { description: string } }) => void) | null = null

class FakeRazorpay {
  constructor(options: RazorpayOptions) {
    checkoutOptions = options
  }
  on(_event: string, handler: (response: { error: { description: string } }) => void) {
    onFailed = handler
  }
  open() {}
}

beforeEach(() => {
  checkoutOptions = null
  onFailed = null
  window.Razorpay = FakeRazorpay
  placeOrder(4, { addressId: 1, paymentMethod: 'ONLINE', note: null })
  server.use(
    http.get('*/api/v1/payments/orders/:orderId', ({ request, params }) =>
      ok({ ...getPayment(userFromRequest(request)!.id, Number(params.orderId)), gatewayOrderId: 'order_RZP1001' }, 'Payment fetched successfully'),
    ),
    http.get('*/api/v1/payments/orders/:orderId/razorpay-checkout', () =>
      ok({ keyId: 'rzp_test_key', razorpayOrderId: 'order_RZP1001', amountInPaise: 23100, currency: 'INR', description: 'Fresh Grocery order #1001', payBefore: new Date(Date.now() + 900_000).toISOString() }, 'Checkout details ready'),
    ),
    http.post('*/api/v1/payments/orders/:orderId/razorpay-verify', async ({ request, params }) => {
      const body = (await request.json()) as { razorpaySignature: string }
      expect(body).toEqual({ razorpayOrderId: 'order_RZP1001', razorpayPaymentId: 'pay_123', razorpaySignature: 'signature' })
      return ok({ ...payWithMockGateway(userFromRequest(request)!.id, Number(params.orderId), true), gatewayOrderId: 'order_RZP1001' }, 'Payment successful')
    }),
  )
})

afterEach(() => {
  delete window.Razorpay
})

describe('Paying with Razorpay', () => {
  it("opens Razorpay's window and confirms the payment with what it returns", async () => {
    logInAs()
    const { user } = renderApp('/orders/1001')
    expect(await screen.findByText(/Pay securely in Razorpay's window/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Decline (test)' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Pay ₹231' }))
    await waitFor(() => expect(checkoutOptions).not.toBeNull())
    expect(checkoutOptions).toMatchObject({ key: 'rzp_test_key', order_id: 'order_RZP1001', amount: 23100, currency: 'INR', prefill: { name: 'Priya Sharma', email: 'customer1@grocery.com', contact: '9000000004' } })

    act(() => checkoutOptions!.handler({ razorpay_order_id: 'order_RZP1001', razorpay_payment_id: 'pay_123', razorpay_signature: 'signature' }))
    expect(await screen.findByText('Payment of ₹231 received')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Order confirmed' })).toBeInTheDocument()
  })

  it('says why a closed window did not pay, and the customer can try again', async () => {
    logInAs()
    const { user } = renderApp('/orders/1001')
    await user.click(await screen.findByRole('button', { name: 'Pay ₹231' }))
    await waitFor(() => expect(checkoutOptions).not.toBeNull())

    act(() => {
      onFailed!({ error: { description: 'Your card was declined' } })
      checkoutOptions!.modal.ondismiss()
    })
    expect(await screen.findByText('The payment failed: Your card was declined')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pay ₹231' })).toBeEnabled()

    // Closing without an attempt is not an error
    checkoutOptions = null
    await user.click(screen.getByRole('button', { name: 'Pay ₹231' }))
    await waitFor(() => expect(checkoutOptions).not.toBeNull())
    act(() => checkoutOptions!.modal.ondismiss())
    expect(await screen.findByRole('status')).toHaveTextContent('You closed the payment window. You can pay until the time runs out.')
  })
})

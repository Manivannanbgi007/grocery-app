import type { RazorpayCheckout, RazorpayResult } from '../../api/payments'
import type { Payment } from '../../api/types'
import { themeColour } from '../../utils/themeColour'

/*
 * Razorpay Checkout: Razorpay's own payment window (UPI, cards, net banking, wallets), opened by their script
 * checkout.js. https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/
 * The script is loaded the first time someone pays, never before.
 */

const CHECKOUT_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js'

/** The parts of Razorpay's window object this app uses. */
interface RazorpayInstance {
  open: () => void
  on: (event: 'payment.failed', handler: (response: { error: { description: string } }) => void) => void
}

export interface RazorpayOptions {
  key: string
  order_id: string
  amount: number
  currency: string
  name: string
  description: string
  prefill: { name?: string; email?: string; contact?: string }
  theme: { color: string }
  handler: (response: { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string }) => void
  modal: { ondismiss: () => void }
}

export type RazorpayConstructor = new (options: RazorpayOptions) => RazorpayInstance

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor
  }
}

/** The customer closed Razorpay's window without paying (after a failed attempt, `message` says why it failed). */
export class RazorpayClosedError extends Error {}

/** The mock gateway names its orders "mock_order_12"; Razorpay's are "order_...". */
export function usesTestGateway(payment: Payment): boolean {
  return payment.gatewayOrderId === null || payment.gatewayOrderId.startsWith('mock_')
}

let loading: Promise<RazorpayConstructor> | null = null

/** Loads checkout.js once (a failed load is tried again next time). */
export function loadRazorpay(): Promise<RazorpayConstructor> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay)
  loading ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = CHECKOUT_SCRIPT
    script.async = true
    script.onload = () => (window.Razorpay ? resolve(window.Razorpay) : reject(new Error('Razorpay could not be started. Please try again.')))
    script.onerror = () => {
      loading = null
      script.remove()
      reject(new Error('Could not load Razorpay. Check your internet connection and try again.'))
    }
    document.head.append(script)
  })
  return loading
}

/**
 * Opens Razorpay's window and waits: resolves with what Razorpay returns after a payment, rejects with
 * RazorpayClosedError when the customer closes the window. A failed attempt keeps the window open (the customer can try
 * another way to pay); its reason becomes the message if the window is then closed.
 */
export function openRazorpayCheckout(Razorpay: RazorpayConstructor, details: RazorpayCheckout, prefill: RazorpayOptions['prefill']): Promise<RazorpayResult> {
  return new Promise((resolve, reject) => {
    let lastFailure: string | null = null
    const checkout = new Razorpay({
      key: details.keyId,
      order_id: details.razorpayOrderId,
      amount: details.amountInPaise,
      currency: details.currency,
      name: 'Fresh Grocery',
      description: details.description,
      prefill,
      theme: { color: themeColour('brand') },
      handler: (response) => resolve({ razorpayOrderId: response.razorpay_order_id, razorpayPaymentId: response.razorpay_payment_id, razorpaySignature: response.razorpay_signature }),
      modal: {
        ondismiss: () => reject(new RazorpayClosedError(lastFailure ? `The payment failed: ${lastFailure}` : 'You closed the payment window. You can pay until the time runs out.')),
      },
    })
    checkout.on('payment.failed', (response) => {
      lastFailure = response.error.description
    })
    checkout.open()
  })
}

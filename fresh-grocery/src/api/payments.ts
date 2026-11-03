import { apiRequest } from './client'
import type { Payment } from './types'

/*
 * The payment of one of the customer's orders (payment-service).
 * It is created in the background a moment after the order: until then this answers 404
 * "No payment found for order 12. If you have just placed it, try again in a few seconds."
 */

export function getMyPayment(orderId: number, signal?: AbortSignal): Promise<Payment> {
  return apiRequest<Payment>(`/api/v1/payments/orders/${orderId}`, { signal })
}

/**
 * The MOCK payment gateway (local development, no real money): success=true pays the order, success=false lets the
 * bank decline it (the order is then cancelled). Fails with 400 while the stock is still being reserved.
 */
export function payWithMockGateway(orderId: number, success: boolean): Promise<Payment> {
  return apiRequest<Payment>(`/api/v1/payments/orders/${orderId}/mock-pay`, { method: 'POST', body: { success } })
}

/** RazorpayCheckoutResponse: what Razorpay Checkout (checkout.js) needs to open its payment window. */
export interface RazorpayCheckout {
  /** the public key id (never the secret) */
  keyId: string
  /** "order_..." */
  razorpayOrderId: string
  /** ₹158.00 = 15800 */
  amountInPaise: number
  currency: string
  description: string
  payBefore: string
}

/** What Razorpay Checkout hands back after a payment; sent on unchanged so payment-service can check the signature. */
export interface RazorpayResult {
  razorpayOrderId: string
  razorpayPaymentId: string
  razorpaySignature: string
}

/*
 * RAZORPAY gateway (payment-service started with PAYMENT_GATEWAY=razorpay; the mock gateway's endpoints are not there
 * then): the details for Checkout, and the check of its result. A wrong signature answers 400.
 */

export function getRazorpayCheckout(orderId: number): Promise<RazorpayCheckout> {
  return apiRequest<RazorpayCheckout>(`/api/v1/payments/orders/${orderId}/razorpay-checkout`)
}

export function verifyRazorpayPayment(orderId: number, result: RazorpayResult): Promise<Payment> {
  return apiRequest<Payment>(`/api/v1/payments/orders/${orderId}/razorpay-verify`, { method: 'POST', body: result })
}

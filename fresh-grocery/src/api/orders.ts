import { apiDownload, apiRequest, type DownloadedFile } from './client'
import type { Order, OrderStatus, OrderSummary, PageResponse, PaymentMethod } from './types'

export interface PlaceOrderRequest {
  /** one of the customer's addresses */
  addressId: number
  paymentMethod: PaymentMethod
  /** optional, at most 250 characters, e.g. "Please ring the bell twice" */
  note: string | null
  /**
   * optional: the checkoutFingerprint of the summary the customer confirmed (the chat's order card). When the cart or
   * its prices changed since, the order is refused with 409 instead of placing something the customer did not see.
   */
  expectedCheckoutFingerprint?: string
}

/*
 * The logged-in customer's orders (order-service).
 * Placing an order answers at once with a PENDING order; reserving the stock, settling the payment and emptying
 * the cart happen in the background afterwards, so the order page asks again until the order has moved on.
 */

/**
 * Turns the whole cart into an order (the items and prices come from the cart). Fails with
 * - 409 "Your cart cannot be ordered yet ..." with the problems in `details` (out of stock, minimum order ...),
 * - 400 when the store cannot take it, e.g. "Fresh Mart does not deliver to PIN code 560034. Please choose another address."
 */
export function placeOrder(request: PlaceOrderRequest, idempotencyKey?: string): Promise<Order> {
  // The same key for the same order (a retry, a double click): order-service answers the order already placed
  return apiRequest<Order>('/api/v1/orders', {
    method: 'POST',
    body: request,
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  })
}

/** The order history, newest first. `page` starts at 0. */
export function getMyOrders(params: { status?: OrderStatus; page?: number; size?: number }, signal?: AbortSignal): Promise<PageResponse<OrderSummary>> {
  return apiRequest<PageResponse<OrderSummary>>('/api/v1/orders', { query: { ...params }, signal })
}

/** One order with its tracking timeline. Another customer's order answers 404, like one that does not exist. */
export function getMyOrder(orderId: number, signal?: AbortSignal): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${orderId}`, { signal })
}

/**
 * Possible until the order leaves the store; a paid order is refunded (in the background, a moment later).
 * Fails with 400 "An order that is OUT_FOR_DELIVERY can no longer be cancelled".
 */
export function cancelMyOrder(orderId: number, reason: string | null): Promise<Order> {
  return apiRequest<Order>(`/api/v1/orders/${orderId}/cancel`, { method: 'POST', body: { reason } })
}

/** The tax invoice as a PDF. Exists once the store accepted the order; before that 400 "Order 12 has no invoice ...". */
export function downloadInvoice(orderId: number): Promise<DownloadedFile> {
  return apiDownload(`/api/v1/orders/${orderId}/invoice`)
}

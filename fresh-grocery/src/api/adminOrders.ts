import { apiDownload, apiRequest, type DownloadedFile } from './client'
import type { Order, OrderStatus, OrderSummary, PageResponse, Payment, PaymentMethod, PaymentStatus, PaymentSummary } from './types'

/*
 * Every order (order-service) and every payment (payment-service), for admins. An admin can cancel a problem order
 * while it is still with the store; a paid one is then refunded by payment-service by itself.
 */

/** The query of the order list. Leave a field out to not filter on it. */
export interface AdminOrderFilter {
  customerUserId?: number
  storeId?: number
  status?: OrderStatus
  /** 0-based */
  page: number
}

/** Orders, newest first. */
export function searchOrders(filter: AdminOrderFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<OrderSummary>> {
  return apiRequest<PageResponse<OrderSummary>>('/api/v1/admin/orders', { query: { ...filter, size }, signal })
}

export function getOrder(orderId: number, signal?: AbortSignal): Promise<Order> {
  return apiRequest<Order>(`/api/v1/admin/orders/${orderId}`, { signal })
}

/** Cancels a problem order (reason required; the customer and the store see it). Only while order.cancellable. */
export function cancelOrder(orderId: number, reason: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/admin/orders/${orderId}/cancel`, { method: 'POST', body: { reason } })
}

/** The tax invoice PDF (once the store accepted the order). */
export function downloadOrderInvoice(orderId: number): Promise<DownloadedFile> {
  return apiDownload(`/api/v1/admin/orders/${orderId}/invoice`)
}

/** The query of the payment list. */
export interface AdminPaymentFilter {
  status?: PaymentStatus
  method?: PaymentMethod
  storeId?: number
  /** 0-based */
  page: number
}

/** Payments, newest first. */
export function searchPayments(filter: AdminPaymentFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<Payment>> {
  return apiRequest<PageResponse<Payment>>('/api/v1/admin/payments', { query: { ...filter, size }, signal })
}

/** Money in and out of the payments created from `from` to `to` (India dates, at most 366 days). */
export function getPaymentSummary(from: string, to: string, signal?: AbortSignal): Promise<PaymentSummary> {
  return apiRequest<PaymentSummary>('/api/v1/admin/payments/summary', { query: { from, to }, signal })
}

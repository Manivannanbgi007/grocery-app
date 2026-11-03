import { apiDownload, apiRequest, apiStream, type DownloadedFile } from './client'
import { readEventStream } from './eventStream'
import type { Order, OrderStatus, OrderSummary, PageResponse } from './types'

/*
 * The orders of the vendor's store (order-service). They need the store id in the login token: a vendor who logged in
 * before the store was approved gets 403 "No store found for this vendor ... log in again".
 *
 * The store's part of the order: CONFIRMED → accept (or reject) → ACCEPTED → pack → PACKED → ready → READY_FOR_PICKUP.
 * A wrong step answers 400, e.g. "Order 12 is PENDING, so it cannot become ACCEPTED".
 */

/** e.g. status=CONFIRMED for the new orders waiting for the store. Newest first; `page` starts at 0. */
export function getStoreOrders(params: { status?: OrderStatus; page?: number; size?: number }, signal?: AbortSignal): Promise<PageResponse<OrderSummary>> {
  return apiRequest<PageResponse<OrderSummary>>('/api/v1/vendor/orders', { query: { ...params }, signal })
}

export function getStoreOrder(orderId: number, signal?: AbortSignal): Promise<Order> {
  return apiRequest<Order>(`/api/v1/vendor/orders/${orderId}`, { signal })
}

export function acceptOrder(orderId: number): Promise<Order> {
  return apiRequest<Order>(`/api/v1/vendor/orders/${orderId}/accept`, { method: 'POST' })
}

/** The stock is released and a paid order is refunded. The reason (1-255 characters) is shown to the customer. */
export function rejectOrder(orderId: number, reason: string): Promise<Order> {
  return apiRequest<Order>(`/api/v1/vendor/orders/${orderId}/reject`, { method: 'POST', body: { reason } })
}

export function packOrder(orderId: number): Promise<Order> {
  return apiRequest<Order>(`/api/v1/vendor/orders/${orderId}/pack`, { method: 'POST' })
}

/** delivery-service then assigns a delivery partner. */
export function markReadyForPickup(orderId: number): Promise<Order> {
  return apiRequest<Order>(`/api/v1/vendor/orders/${orderId}/ready-for-pickup`, { method: 'POST' })
}

/** The tax invoice PDF (from ACCEPTED on). */
export function downloadStoreInvoice(orderId: number): Promise<DownloadedFile> {
  return apiDownload(`/api/v1/vendor/orders/${orderId}/invoice`)
}

/** One order of the store changed (the live stream's "order" event): which one, and its status now. */
export interface StoreOrderChange {
  orderId: number
  status: OrderStatus
}

/**
 * Follows the store's orders live (Server-Sent Events, order-service's VendorOrderStreams):
 * - onReady: the stream is open (load the queue again: something may have changed while it was connecting);
 * - onChange: an order of the store changed (a new order, a cancellation, the partner picked it up ...).
 * Resolves when the server ends the stream (after 30 minutes: the caller reconnects), rejects when the connection
 * breaks or is aborted. onActivity: something arrived (also a keep-alive).
 */
export async function followStoreOrders(
  onReady: () => void,
  onChange: (change: StoreOrderChange) => void,
  onActivity: () => void,
  signal: AbortSignal,
): Promise<void> {
  const response = await apiStream('/api/v1/vendor/orders/stream', signal)
  onActivity()
  await readEventStream(
    response,
    (event) => {
      if (event.event === 'ready') onReady()
      else if (event.event === 'order') onChange(JSON.parse(event.data) as StoreOrderChange)
    },
    onActivity,
  )
}

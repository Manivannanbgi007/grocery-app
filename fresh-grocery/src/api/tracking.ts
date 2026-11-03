import { apiRequest, apiStream } from './client'
import { readEventStream } from './eventStream'
import type { LocationUpdate, LocationUpdateResult, TrackingSnapshot } from './types'

/*
 * Live delivery tracking (delivery-service, docs/delivery-tracking.md).
 *   customer: the delivery of my order, once (getOrderTracking) or live (followOrderTracking)
 *   partner:  share my position (sendLocation) and see my delivery on the map (getDeliveryTracking)
 * The gateway checks the role; delivery-service answers 404 for someone else's order or delivery.
 */

/** The delivery of the customer's order now. 404 until the order is ready for pickup (no delivery yet). */
export function getOrderTracking(orderId: number, signal?: AbortSignal): Promise<TrackingSnapshot> {
  return apiRequest<TrackingSnapshot>(`/api/v1/delivery-tracking/orders/${orderId}`, { signal })
}

/**
 * Follows the delivery of the customer's order live: onSnapshot gets the current state at once and again after every
 * change. Resolves when the server ends the stream (the delivery is over, or after 30 minutes: the caller reconnects),
 * rejects when the connection breaks or is aborted. onActivity: something arrived (also a keep-alive).
 */
export async function followOrderTracking(
  orderId: number,
  onSnapshot: (snapshot: TrackingSnapshot) => void,
  onActivity: () => void,
  signal: AbortSignal,
): Promise<void> {
  const response = await apiStream(`/api/v1/delivery-tracking/orders/${orderId}/stream`, signal)
  onActivity()
  await readEventStream(
    response,
    (event) => {
      if (event.event === 'tracking') onSnapshot(JSON.parse(event.data) as TrackingSnapshot)
    },
    onActivity,
  )
}

/** The partner's position: only for their own delivery, while it is accepted, picked up or on its way. */
export function sendLocation(deliveryId: number, update: LocationUpdate): Promise<LocationUpdateResult> {
  return apiRequest<LocationUpdateResult>(`/api/v1/delivery/deliveries/${deliveryId}/location`, { method: 'PUT', body: update })
}

/** The partner's delivery on the map: store, customer, the last position they shared, the route. */
export function getDeliveryTracking(deliveryId: number, signal?: AbortSignal): Promise<TrackingSnapshot> {
  return apiRequest<TrackingSnapshot>(`/api/v1/delivery/deliveries/${deliveryId}/tracking`, { signal })
}

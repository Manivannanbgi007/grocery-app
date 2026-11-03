import { http } from 'msw'
import type { Delivery, LocationUpdate } from '../../api/types'
import { MockApiError } from '../mockApiError'
import { deliveryOfOrder, myDelivery, partnerById } from '../mockDelivery'
import { getOrder } from '../mockOrders'
import { snapshotOf, trackingStream, updateLocation } from '../mockTracking'
import { asCustomer, asPartner } from './withRole'

/*
 * Mock of the live delivery tracking endpoints (delivery-service): the customer's snapshot and live stream
 * (/api/v1/delivery-tracking/orders/{orderId}), the partner's location updates and map (/api/v1/delivery/deliveries/{id}/...).
 */

const snapshot = (delivery: Delivery) => snapshotOf(delivery, delivery.partnerUserId === null ? undefined : partnerById(delivery.partnerUserId))

/** Only the customer's own order (another customer's: 404), and only once it has a delivery. */
function customerDelivery(customerUserId: number, orderId: number): Delivery {
  getOrder(customerUserId, orderId) // 404 for someone else's order
  const delivery = deliveryOfOrder(orderId)
  if (!delivery) throw new MockApiError(404, `No delivery for order ${orderId} yet: tracking starts when the order is ready for pickup`)
  return delivery
}

export const trackingHandlers = [
  http.get(
    '*/api/v1/delivery-tracking/orders/:orderId',
    asCustomer(async (customer, _request, params) => ({
      data: snapshot(customerDelivery(customer.id, Number(params.orderId))),
      message: 'Tracking fetched successfully',
    })),
  ),

  http.get(
    '*/api/v1/delivery-tracking/orders/:orderId/stream',
    asCustomer(async (customer, _request, params) => {
      const orderId = Number(params.orderId)
      const delivery = customerDelivery(customer.id, orderId)
      const body = trackingStream(orderId, () => snapshot(delivery), snapshot)
      return new Response(body, { headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' } })
    }),
  ),

  http.put(
    '*/api/v1/delivery/deliveries/:deliveryId/location',
    asPartner(async (partner, request, params) => {
      const delivery = myDelivery(partner.id, Number(params.deliveryId))
      const stored = updateLocation(delivery, (await request.json()) as Partial<LocationUpdate>)
      return { data: { stored, route: null }, message: stored ? 'Location updated' : 'Location skipped' }
    }),
  ),

  http.get(
    '*/api/v1/delivery/deliveries/:deliveryId/tracking',
    asPartner(async (partner, _request, params) => ({
      data: snapshot(myDelivery(partner.id, Number(params.deliveryId))),
      message: 'Tracking fetched successfully',
    })),
  ),
]

import type { DeliveryStatus, OrderStatus, TrackingRoute, TrackingSnapshot } from '../../api/types'
import { formatTime } from '../../utils/format'

/* What the customer's tracking page says, in one place (the components only show it). */

const TRACKED: DeliveryStatus[] = ['ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY']
const OVER: DeliveryStatus[] = ['DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED']

/** The partner shares their position in this status (from accepting until handing over). */
export const isTrackedStatus = (status: DeliveryStatus) => TRACKED.includes(status)
/** Nothing more will happen: the live updates end. */
export const isOverStatus = (status: DeliveryStatus) => OVER.includes(status)

/** "Arjun" from "Arjun Singh"; "Your delivery partner" before one is assigned. */
export function partnerFirstName(snapshot: TrackingSnapshot): string {
  return snapshot.partner?.name.split(' ')[0] ?? 'Your delivery partner'
}

/** The big line at the top: what is happening now. */
export function trackingHeadline(snapshot: TrackingSnapshot): { title: string; text: string } {
  const name = partnerFirstName(snapshot)
  const store = snapshot.pickup.name
  switch (snapshot.status) {
    case 'WAITING_FOR_PARTNER':
      return { title: 'Finding a delivery partner', text: `${store} has your order ready. A delivery partner near the store will take it.` }
    case 'ASSIGNED':
      return { title: `${name} will bring your order`, text: `${name} was asked to deliver it and will confirm in a moment.` }
    case 'ACCEPTED':
      return { title: `${name} is on the way to ${store}`, text: 'Your order is waiting at the store.' }
    case 'PICKED_UP':
      return { title: `${name} has picked up your order`, text: `${name} is leaving ${store}.` }
    case 'OUT_FOR_DELIVERY':
      return { title: `${name} is on the way to you`, text: 'Please keep your phone close.' }
    case 'DELIVERED':
      return { title: 'Delivered', text: 'Your order has arrived. Enjoy your groceries!' }
    case 'FAILED':
    case 'RETURNED':
      return { title: 'The delivery did not work out', text: 'Your order goes back to the store. You will find the details on the order page.' }
    case 'CANCELLED':
      return { title: 'The delivery was cancelled', text: 'Your order was cancelled before it left the store.' }
  }
}

/**
 * The order's step for the progress list. The live delivery status is a step ahead of the order for a moment:
 * order-service hears about a pick-up or a hand-over a little later (through Kafka), and the page loads the order again
 * only every 30 s. So "picked up" already shows the order on the way, and "delivered" shows it delivered.
 */
export function orderStepOf(orderStatus: OrderStatus, deliveryStatus: DeliveryStatus): OrderStatus {
  if (orderStatus !== 'READY_FOR_PICKUP' && orderStatus !== 'OUT_FOR_DELIVERY') return orderStatus
  if (deliveryStatus === 'DELIVERED') return 'DELIVERED'
  if (deliveryStatus === 'PICKED_UP' || deliveryStatus === 'OUT_FOR_DELIVERY') return 'OUT_FOR_DELIVERY'
  return orderStatus
}

/**
 * The arrival time from the routing service, e.g. "Arrives around 10:42 (in about 7 min)". Null without a route: then
 * there is no reliable arrival time, and the page does not guess one.
 */
export function arrivalText(route: TrackingRoute | null, storeName: string, now: number): string | null {
  if (!route) return null
  const minutes = Math.max(1, Math.round((new Date(route.estimatedArrivalAt).getTime() - now) / 60_000))
  const at = formatTime(route.estimatedArrivalAt)
  return route.target === 'DESTINATION' ? `Arrives around ${at} (in about ${minutes} min)` : `At ${storeName} around ${at}`
}

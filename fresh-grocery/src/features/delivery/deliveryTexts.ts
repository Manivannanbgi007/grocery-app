import { Bike, CircleCheck, House, Package } from 'lucide-react'
import type { DeliveryStep } from '../../api/delivery'
import type { Delivery, DeliveryIssue, DeliveryStatus, VehicleType } from '../../api/types'
import type { BadgeTone } from '../../components/ui/Badge'
import type { Step } from '../../components/ui/StepBar'
import { formatDateTime, formatPrice } from '../../utils/format'

/* How a delivery looks to the partner: the names of the steps, the next button, and what to do now. */

export const VEHICLE_NAMES: Record<VehicleType, string> = { BICYCLE: 'Bicycle', SCOOTER: 'Scooter', MOTORBIKE: 'Motorbike' }

export const TASK_STATUS_LABELS: Record<DeliveryStatus, string> = {
  WAITING_FOR_PARTNER: 'Waiting for a partner',
  ASSIGNED: 'New job',
  ACCEPTED: 'Go to the store',
  PICKED_UP: 'Picked up',
  OUT_FOR_DELIVERY: 'On the way',
  DELIVERED: 'Delivered',
  FAILED: 'Not delivered',
  RETURNED: 'Returned to the store',
  CANCELLED: 'Cancelled',
}

export const TASK_STATUS_TONES: Record<DeliveryStatus, BadgeTone> = {
  WAITING_FOR_PARTNER: 'neutral',
  ASSIGNED: 'new',
  ACCEPTED: 'sale',
  PICKED_UP: 'sale',
  OUT_FOR_DELIVERY: 'brand',
  DELIVERED: 'brand',
  FAILED: 'hot',
  RETURNED: 'neutral',
  CANCELLED: 'neutral',
}

/** The step bar of a delivery: the partner's milestones from accepting it to handing it over. */
export const TASK_STEPS: Step[] = [
  { label: 'Accepted', icon: CircleCheck },
  { label: 'Picked up', icon: Package },
  { label: 'On the way', icon: Bike },
  { label: 'Delivered', icon: House },
]

const TASK_STEP_OF: Partial<Record<DeliveryStatus, number>> = { ASSIGNED: -1, ACCEPTED: 0, PICKED_UP: 1, OUT_FOR_DELIVERY: 2, DELIVERED: 3 }

/** How far a delivery is on TASK_STEPS (-1: a new job, not accepted yet); null for one that stopped (no step bar). */
export function taskStep(status: DeliveryStatus): number | null {
  return TASK_STEP_OF[status] ?? null
}

/** The problems a partner can report (DeliveryIssue). */
export const ISSUE_LABELS: Record<DeliveryIssue, string> = {
  CUSTOMER_UNAVAILABLE: 'The customer is not there',
  ADDRESS_WRONG: 'The address is wrong',
  ITEM_DAMAGED: 'An item is damaged',
  OTHER: 'Something else',
}

/** The partner's next step for a delivery in this status (Accept / Reject of a new job are two buttons of their own). */
export function nextTaskStep(status: DeliveryStatus): { step: DeliveryStep; label: string; done: string } | null {
  switch (status) {
    case 'ACCEPTED':
      return { step: 'pick-up', label: 'I picked up the order', done: 'Picked up: the customer now sees the order is on the way' }
    case 'PICKED_UP':
      return { step: 'start', label: 'I am on my way', done: 'On the way to the customer' }
    case 'OUT_FOR_DELIVERY':
      return { step: 'deliver', label: 'Delivered', done: 'Delivered. Well done!' }
    case 'FAILED':
      return { step: 'return', label: 'Items returned to the store', done: 'The items are back at the store' }
    default:
      return null
  }
}

/** The partner shares their position (live tracking) from accepting the delivery until handing it over. */
export const sharesLocation = (status: DeliveryStatus) => status === 'ACCEPTED' || status === 'PICKED_UP' || status === 'OUT_FOR_DELIVERY'

/** A problem can be reported once the partner has the items, until they are delivered. */
export const canReportIssue = (status: DeliveryStatus) => status === 'PICKED_UP' || status === 'OUT_FOR_DELIVERY'

/** A proof photo can be added on the way or after delivering. */
export const canAddProof = (status: DeliveryStatus) => status === 'OUT_FOR_DELIVERY' || status === 'DELIVERED'

/** The headline of a delivery: what the partner should do now. */
export function taskMessage(delivery: Delivery): { title: string; text: string } {
  switch (delivery.status) {
    case 'ASSIGNED':
      return { title: 'A new delivery for you', text: 'Accept it, or reject it so it goes to another partner.' }
    case 'ACCEPTED':
      return { title: 'Go to the store', text: `Pick up order #${delivery.orderId} at ${delivery.storeName}.` }
    case 'PICKED_UP':
      return { title: 'Take it to the customer', text: 'Tap “I am on my way” when you leave the store.' }
    case 'OUT_FOR_DELIVERY':
      return {
        title: 'Bring it to the customer',
        text:
          delivery.amountToCollect > 0
            ? `Collect ${formatPrice(delivery.amountToCollect)} in cash, then tap “Delivered”.`
            : 'It is paid online: hand it over, then tap “Delivered”.',
      }
    case 'DELIVERED':
      return {
        title: 'Delivered',
        text: `${delivery.deliveredAt ? `On ${formatDateTime(delivery.deliveredAt)}. ` : ''}${delivery.partnerPayout !== null ? `You earned ${formatPrice(delivery.partnerPayout)}.` : ''}`,
      }
    case 'FAILED':
      return {
        title: 'Take the items back to the store',
        text: `${delivery.issue ? ISSUE_LABELS[delivery.issue] : 'Not delivered'}${delivery.issueNote ? `: “${delivery.issueNote}”` : ''}. Then tap “Items returned to the store”.`,
      }
    case 'RETURNED':
      return { title: 'Returned to the store', text: 'The items are back at the store.' }
    case 'CANCELLED':
      return { title: 'Cancelled', text: 'The order was cancelled before you picked it up.' }
    case 'WAITING_FOR_PARTNER':
      return { title: 'Waiting for a partner', text: 'This delivery is not yours any more.' }
  }
}

/** Google Maps with the route from the store to the customer (the backend has no map). */
export function directionsLink(from: string, to: string): string {
  return `https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(from)}&destination=${encodeURIComponent(to)}`
}

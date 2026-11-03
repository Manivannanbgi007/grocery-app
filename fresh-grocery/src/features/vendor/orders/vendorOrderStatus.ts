import type { ChangedBy, Order, OrderStatus } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'
import { formatDateTime, formatPrice } from '../../../utils/format'
import { ORDER_STATUS_TONES } from '../../orders/orderStatus'

/* How an order looks from the store's side: the names of the statuses, the next step, and what to do now. */

/**
 * The statuses' colours for the store: the customer's colours, except that a new order is orange (the store has to act
 * now, like "To accept" in the order flow) and one not confirmed yet is grey (nothing to do yet).
 */
export const VENDOR_STATUS_TONES: Record<OrderStatus, BadgeTone> = { ...ORDER_STATUS_TONES, CONFIRMED: 'new', PENDING: 'neutral' }

export const VENDOR_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Not confirmed yet',
  CONFIRMED: 'New',
  ACCEPTED: 'Accepted',
  PACKED: 'Packed',
  READY_FOR_PICKUP: 'Ready for pickup',
  OUT_FOR_DELIVERY: 'On the way',
  DELIVERED: 'Delivered',
  DELIVERY_FAILED: 'Delivery failed',
  CANCELLED: 'Cancelled',
  REJECTED: 'Rejected',
}

export const VENDOR_CHANGED_BY_LABELS: Record<ChangedBy, string> = {
  CUSTOMER: 'the customer',
  VENDOR: 'you',
  ADMIN: 'customer support',
  DELIVERY_PARTNER: 'the delivery partner',
  SYSTEM: 'Fresh Grocery',
}

export type StoreStep = 'accept' | 'pack' | 'ready'

/** The store's next step for an order in this status (rejecting is offered next to "Accept"), or null when there is none. */
export function nextStep(status: OrderStatus): { step: StoreStep; label: string } | null {
  if (status === 'CONFIRMED') return { step: 'accept', label: 'Accept' }
  if (status === 'ACCEPTED') return { step: 'pack', label: 'Mark as packed' }
  if (status === 'PACKED') return { step: 'ready', label: 'Ready for pickup' }
  return null
}

type MessageInput = Pick<Order, 'status' | 'statusReason' | 'cancelledBy' | 'deliveryPartnerName' | 'deliveredAt'>

/** The headline of the vendor's order page: where the order is and what the store should do now. */
export function vendorStatusMessage(order: MessageInput): { title: string; text: string } {
  const partner = order.deliveryPartnerName
  switch (order.status) {
    case 'PENDING':
      return { title: 'Not confirmed yet', text: 'The stock and the payment are still being settled. Do not prepare it yet.' }
    case 'CONFIRMED':
      return { title: 'New order', text: 'Accept it to start preparing, or reject it with a reason (for example when items are missing).' }
    case 'ACCEPTED':
      return { title: 'Pick and pack the items', text: 'When everything is in the bag, mark the order as packed.' }
    case 'PACKED':
      return { title: 'Packed', text: 'When the bag is ready to hand over, mark the order ready for pickup: a delivery partner is then assigned.' }
    case 'READY_FOR_PICKUP':
      return { title: 'Waiting for pickup', text: partner ? `${partner} will pick it up. Hand the bag over when they arrive.` : 'A delivery partner is being assigned.' }
    case 'OUT_FOR_DELIVERY':
      return { title: 'On the way', text: `${partner ?? 'The delivery partner'} picked it up.` }
    case 'DELIVERED':
      return { title: 'Delivered', text: `Delivered${order.deliveredAt ? ` on ${formatDateTime(order.deliveredAt)}` : ''}.` }
    case 'CANCELLED': {
      const by = order.cancelledBy ? VENDOR_CHANGED_BY_LABELS[order.cancelledBy] : 'someone'
      return { title: 'Cancelled', text: `Cancelled by ${by}${order.statusReason ? `: ${order.statusReason}` : ''}. Do not pack it.` }
    }
    case 'REJECTED':
      return { title: 'You rejected this order', text: order.statusReason ?? '' }
    case 'DELIVERY_FAILED':
      return { title: 'The delivery failed', text: `${order.statusReason ?? 'The partner could not deliver it.'} The items come back to the store.` }
  }
}

/** What happens with the money, from the store's side. */
export function vendorPaymentLine(order: Pick<Order, 'paymentMethod' | 'paymentStatus' | 'amounts'>): string {
  const total = formatPrice(order.amounts.totalAmount)
  switch (order.paymentStatus) {
    case 'PENDING':
      return 'The payment is not settled yet'
    case 'CASH_ON_DELIVERY':
      return `Cash on delivery: the delivery partner collects ${total}`
    case 'PAID':
      return order.paymentMethod === 'COD' ? `${total} collected in cash` : `Paid online: ${total}`
    case 'FAILED':
      return 'The online payment failed'
    case 'CANCELLED':
      return 'Nothing to collect'
    case 'REFUNDED':
      return `${total} refunded to the customer`
  }
}

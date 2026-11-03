import type { ChangedBy, Order, OrderPaymentStatus, OrderStatus, TimelineEntry } from '../../api/types'
import type { BadgeTone } from '../../components/ui/Badge'
import { formatDateTime, formatPrice } from '../../utils/format'

/* How an order's status is shown: names, colours, the progress steps, and how often the page asks for news. */

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PENDING: 'Placed',
  CONFIRMED: 'Confirmed',
  ACCEPTED: 'Accepted by the store',
  PACKED: 'Packed',
  READY_FOR_PICKUP: 'Ready for pickup',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  DELIVERY_FAILED: 'Delivery failed',
  CANCELLED: 'Cancelled',
  REJECTED: 'Rejected by the store',
}

export const ORDER_STATUS_TONES: Record<OrderStatus, BadgeTone> = {
  PENDING: 'new',
  CONFIRMED: 'sale',
  ACCEPTED: 'sale',
  PACKED: 'sale',
  READY_FOR_PICKUP: 'sale',
  OUT_FOR_DELIVERY: 'brand',
  DELIVERED: 'brand',
  DELIVERY_FAILED: 'hot',
  CANCELLED: 'hot',
  REJECTED: 'hot',
}

export const CHANGED_BY_LABELS: Record<ChangedBy, string> = {
  CUSTOMER: 'you',
  VENDOR: 'the store',
  ADMIN: 'customer support',
  DELIVERY_PARTNER: 'the delivery partner',
  SYSTEM: 'Fresh Grocery',
}

/** The normal way of an order, shown as progress steps. */
export const PROGRESS_STEPS: OrderStatus[] = ['PENDING', 'CONFIRMED', 'ACCEPTED', 'PACKED', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED']

/** Short names under the progress steps. */
export const STEP_LABELS: Partial<Record<OrderStatus, string>> = {
  PENDING: 'Placed',
  CONFIRMED: 'Confirmed',
  ACCEPTED: 'Accepted',
  PACKED: 'Packed',
  READY_FOR_PICKUP: 'Ready for pickup',
  OUT_FOR_DELIVERY: 'On the way',
  DELIVERED: 'Delivered',
}

type StatusMessageInput = Pick<Order, 'status' | 'storeName' | 'statusReason' | 'deliveryPartnerName' | 'deliveredAt'>

/** The headline of the order page: where the order is now, in the customer's words. */
export function statusMessage(order: StatusMessageInput): { title: string; text: string } {
  const partner = order.deliveryPartnerName
  switch (order.status) {
    case 'PENDING':
      return { title: 'Confirming your order', text: 'We are checking that every item is in stock and settling the payment. This takes a few seconds.' }
    case 'CONFIRMED':
      return { title: 'Order confirmed', text: `${order.storeName} will accept it shortly.` }
    case 'ACCEPTED':
      return { title: 'The store is preparing your order', text: `${order.storeName} is picking and packing your items.` }
    case 'PACKED':
      return { title: 'Your items are packed', text: 'A delivery partner will pick them up soon.' }
    case 'READY_FOR_PICKUP':
      return { title: 'Waiting for pickup', text: partner ? `${partner} will pick up your order from the store.` : 'We are finding a delivery partner for your order.' }
    case 'OUT_FOR_DELIVERY':
      return { title: 'On the way', text: `${partner ?? 'Your delivery partner'} is bringing your order.` }
    case 'DELIVERED':
      return { title: 'Delivered', text: `Delivered${order.deliveredAt ? ` on ${formatDateTime(order.deliveredAt)}` : ''}. Enjoy your groceries!` }
    case 'CANCELLED':
      return { title: 'Order cancelled', text: order.statusReason ?? 'This order was cancelled.' }
    case 'REJECTED':
      return { title: 'The store could not take your order', text: order.statusReason ?? `${order.storeName} rejected this order.` }
    case 'DELIVERY_FAILED':
      return { title: 'The order could not be delivered', text: order.statusReason ?? 'The delivery partner could not deliver this order.' }
  }
}

/** The end states outside the normal way. */
const STOPPED: OrderStatus[] = ['CANCELLED', 'REJECTED', 'DELIVERY_FAILED']

/** True when nothing will happen to the order any more. */
export function isFinished(status: OrderStatus): boolean {
  return status === 'DELIVERED' || STOPPED.includes(status)
}

export function isStopped(status: OrderStatus): boolean {
  return STOPPED.includes(status)
}

/** True while the order has a delivery to follow on the map: the store has handed it over (or is about to). */
export function hasLiveMap(status: OrderStatus): boolean {
  return status === 'READY_FOR_PICKUP' || status === 'OUT_FOR_DELIVERY'
}

const PENDING_POLL_MS = 3000
const ACTIVE_POLL_MS = 30_000

/** A paid order that was cancelled, rejected or not delivered: payment-service refunds it a moment later. */
function waitsForRefund(order: Pick<Order, 'status' | 'paymentStatus'>): boolean {
  return isStopped(order.status) && order.paymentStatus === 'PAID'
}

/**
 * How often the order page asks for news (milliseconds), or false to stop:
 * every 3 s while the order is PENDING (stock and payment take a few seconds) or a refund is on its way,
 * every 30 s while the store and the delivery partner work on it, never once it is finished.
 */
export function pollInterval(order: Pick<Order, 'status' | 'paymentStatus'> | undefined): number | false {
  if (order === undefined) return false
  if (order.status === 'PENDING' || waitsForRefund(order)) return PENDING_POLL_MS
  return isFinished(order.status) ? false : ACTIVE_POLL_MS
}

/** Order.hasInvoice: the tax invoice exists once the store accepted the order (not for cancelled or failed ones). */
export function hasInvoice(status: OrderStatus): boolean {
  return ['ACCEPTED', 'PACKED', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED'].includes(status)
}

/** One line about the payment, e.g. "Cash on delivery: pay ₹324 at the door" or "Paid online". */
export function paymentSummary(order: Pick<Order, 'status' | 'paymentMethod' | 'paymentStatus' | 'amounts'>): string {
  const total = formatPrice(order.amounts.totalAmount)
  const paid = order.paymentMethod === 'COD' ? `Paid ${total} in cash` : `Paid ${total} online`
  const texts: Record<OrderPaymentStatus, string> = {
    PENDING: order.paymentMethod === 'COD' ? `Cash on delivery: ${total}` : `Pay online: ${total}`,
    CASH_ON_DELIVERY: `Cash on delivery: pay ${total} at the door`,
    PAID: waitsForRefund(order) ? `${paid}. The refund is on its way.` : paid,
    FAILED: 'The online payment failed',
    CANCELLED: 'Nothing to pay',
    REFUNDED: `${total} refunded`,
  }
  return texts[order.paymentStatus]
}

/**
 * The note under a step of the history, for the store and the admins. The backend writes the notes for the customer
 * ("The store accepted your order"); staff see the notes that say more than the step (how the customer pays, why an
 * order stopped) and the partner's name.
 */
export function staffTimelineNote(entry: TimelineEntry): string | null {
  if (entry.status === 'ACCEPTED' || entry.status === 'PACKED') return null
  if (entry.status === 'OUT_FOR_DELIVERY') return entry.note?.replace('On the way to you', 'On the way to the customer') ?? null
  return entry.note
}

export const PAYMENT_METHOD_LABELS: Record<Order['paymentMethod'], string> = {
  COD: 'Cash on delivery',
  ONLINE: 'Online payment',
}

/** The payment of an order in one or two words, for its badge. */
export const ORDER_PAYMENT_LABELS: Record<OrderPaymentStatus, string> = {
  PENDING: 'Not paid yet',
  CASH_ON_DELIVERY: 'Pay on delivery',
  PAID: 'Paid',
  FAILED: 'Payment failed',
  CANCELLED: 'Not charged',
  REFUNDED: 'Refunded',
}

export const ORDER_PAYMENT_TONES: Record<OrderPaymentStatus, BadgeTone> = {
  PENDING: 'new',
  CASH_ON_DELIVERY: 'new',
  PAID: 'brand',
  FAILED: 'hot',
  CANCELLED: 'neutral',
  REFUNDED: 'sale',
}

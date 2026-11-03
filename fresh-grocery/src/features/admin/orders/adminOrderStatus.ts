import type { ChangedBy, Order, OrderStatus } from '../../../api/types'
import { formatDateTime, formatPrice } from '../../../utils/format'

/* How an order looks to the platform team: the tabs of the list, who did a step, and where the order is stuck. */

/**
 * The tabs of the order list (the backend filters on one status). All orders first: an admin mostly looks one up.
 * group: the tab bar draws a thin line between all orders | the ones on their way | the finished ones.
 */
export const ADMIN_ORDER_TABS: { key: string; label: string; status?: OrderStatus; group: 'all' | 'open' | 'finished' }[] = [
  { key: 'all', label: 'All', group: 'all' },
  { key: 'pending', label: 'Not confirmed', status: 'PENDING', group: 'open' },
  { key: 'waiting', label: 'Waiting for the store', status: 'CONFIRMED', group: 'open' },
  { key: 'accepted', label: 'Accepted', status: 'ACCEPTED', group: 'open' },
  { key: 'packed', label: 'Packed', status: 'PACKED', group: 'open' },
  { key: 'ready', label: 'Ready for pickup', status: 'READY_FOR_PICKUP', group: 'open' },
  { key: 'on-the-way', label: 'On the way', status: 'OUT_FOR_DELIVERY', group: 'open' },
  { key: 'delivered', label: 'Delivered', status: 'DELIVERED', group: 'finished' },
  { key: 'failed', label: 'Delivery failed', status: 'DELIVERY_FAILED', group: 'finished' },
  { key: 'cancelled', label: 'Cancelled', status: 'CANCELLED', group: 'finished' },
  { key: 'rejected', label: 'Rejected', status: 'REJECTED', group: 'finished' },
]

export const ADMIN_CHANGED_BY_LABELS: Record<ChangedBy, string> = {
  CUSTOMER: 'the customer',
  VENDOR: 'the store',
  ADMIN: 'an admin',
  DELIVERY_PARTNER: 'the delivery partner',
  SYSTEM: 'Fresh Grocery',
}

type MessageInput = Pick<Order, 'status' | 'storeName' | 'statusReason' | 'cancelledBy' | 'deliveryPartnerName' | 'deliveredAt' | 'paymentMethod'>

/** The headline of the admin's order page: where the order is, and who has to act. */
export function adminStatusMessage(order: MessageInput): { title: string; text: string } {
  const partner = order.deliveryPartnerName
  switch (order.status) {
    case 'PENDING':
      return {
        title: 'Not confirmed yet',
        text:
          order.paymentMethod === 'ONLINE'
            ? 'Waiting for the online payment (at most 15 minutes, then the order is cancelled by itself).'
            : 'The stock is being reserved; this takes a few seconds.',
      }
    case 'CONFIRMED':
      return { title: 'Waiting for the store', text: `${order.storeName} has not accepted it yet.` }
    case 'ACCEPTED':
      return { title: 'The store is packing it', text: `${order.storeName} accepted it.` }
    case 'PACKED':
      return { title: 'Packed', text: `${order.storeName} has not handed it over yet.` }
    case 'READY_FOR_PICKUP':
      return partner
        ? { title: 'Waiting for pickup', text: `${partner} will pick it up.` }
        : { title: 'Waiting for a delivery partner', text: 'No partner has it yet. A waiting delivery can be assigned under Deliveries.' }
    case 'OUT_FOR_DELIVERY':
      return { title: 'On the way', text: `${partner ?? 'The delivery partner'} is delivering it.` }
    case 'DELIVERED':
      return { title: 'Delivered', text: `Delivered${order.deliveredAt ? ` on ${formatDateTime(order.deliveredAt)}` : ''}.` }
    case 'CANCELLED': {
      const by = order.cancelledBy ? ADMIN_CHANGED_BY_LABELS[order.cancelledBy] : 'someone'
      return { title: 'Cancelled', text: `Cancelled by ${by}${order.statusReason ? `: “${order.statusReason}”` : ''}.` }
    }
    case 'REJECTED':
      return { title: 'Rejected by the store', text: order.statusReason ? `“${order.statusReason}”` : '' }
    case 'DELIVERY_FAILED':
      return { title: 'The delivery failed', text: order.statusReason ? `“${order.statusReason}”` : 'The partner could not deliver it.' }
  }
}

/** What happens with the money. A paid order that stopped is refunded by payment-service a moment later. */
export function adminPaymentLine(order: Pick<Order, 'status' | 'paymentMethod' | 'paymentStatus' | 'amounts'>): string {
  const total = formatPrice(order.amounts.totalAmount)
  const stopped = ['CANCELLED', 'REJECTED', 'DELIVERY_FAILED'].includes(order.status)
  switch (order.paymentStatus) {
    case 'PENDING':
      return order.paymentMethod === 'ONLINE' ? `Waiting for the online payment of ${total}` : 'The payment is not settled yet'
    case 'CASH_ON_DELIVERY':
      return `Cash on delivery: the delivery partner collects ${total}`
    case 'PAID':
      if (stopped) return `Paid ${total} online; the refund is on its way`
      return order.paymentMethod === 'COD' ? `${total} collected in cash` : `Paid online: ${total}`
    case 'FAILED':
      return 'The online payment failed'
    case 'CANCELLED':
      return 'Nothing to pay'
    case 'REFUNDED':
      return `${total} refunded to the customer`
  }
}

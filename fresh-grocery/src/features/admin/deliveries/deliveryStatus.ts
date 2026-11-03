import type { DeliveryIssue, DeliveryStatus } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  WAITING_FOR_PARTNER: 'Waiting for a partner',
  ASSIGNED: 'Assigned',
  ACCEPTED: 'Accepted by the partner',
  PICKED_UP: 'Picked up',
  OUT_FOR_DELIVERY: 'On the way',
  DELIVERED: 'Delivered',
  FAILED: 'Failed',
  RETURNED: 'Returned to the store',
  CANCELLED: 'Cancelled',
}

export const DELIVERY_STATUS_TONES: Record<DeliveryStatus, BadgeTone> = {
  WAITING_FOR_PARTNER: 'new',
  ASSIGNED: 'sale',
  ACCEPTED: 'sale',
  PICKED_UP: 'sale',
  OUT_FOR_DELIVERY: 'brand',
  DELIVERED: 'brand',
  FAILED: 'hot',
  RETURNED: 'neutral',
  CANCELLED: 'neutral',
}

export const DELIVERY_ISSUE_LABELS: Record<DeliveryIssue, string> = {
  CUSTOMER_UNAVAILABLE: 'Customer not available',
  ADDRESS_WRONG: 'Wrong address',
  ITEM_DAMAGED: 'Item damaged',
  OTHER: 'Other problem',
}

/**
 * The tabs of the delivery list: the ones nobody took first (they are the work), then the way of a delivery.
 * group: the tab bar draws a thin line between the deliveries on their way | the finished ones | all.
 */
export const DELIVERY_TABS: { key: string; label: string; status?: DeliveryStatus; group: 'open' | 'finished' | 'all' }[] = [
  { key: 'waiting', label: 'Waiting for a partner', status: 'WAITING_FOR_PARTNER', group: 'open' },
  { key: 'assigned', label: 'Assigned', status: 'ASSIGNED', group: 'open' },
  { key: 'accepted', label: 'Accepted', status: 'ACCEPTED', group: 'open' },
  { key: 'picked-up', label: 'Picked up', status: 'PICKED_UP', group: 'open' },
  { key: 'on-the-way', label: 'On the way', status: 'OUT_FOR_DELIVERY', group: 'open' },
  { key: 'delivered', label: 'Delivered', status: 'DELIVERED', group: 'finished' },
  { key: 'failed', label: 'Failed', status: 'FAILED', group: 'finished' },
  { key: 'returned', label: 'Returned', status: 'RETURNED', group: 'finished' },
  { key: 'cancelled', label: 'Cancelled', status: 'CANCELLED', group: 'finished' },
  { key: 'all', label: 'All', group: 'all' },
]

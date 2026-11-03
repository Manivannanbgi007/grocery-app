import type { OrderStatus } from '../../../api/types'

/** The tabs of the order queue (the backend filters on one status). The first three show how many need work. */
export interface OrderTab {
  key: string
  label: string
  /** no status = all orders */
  status?: OrderStatus
  /** shows a counter (orders that wait for the store) */
  counted?: boolean
  /** what the tab says when it is empty */
  empty: string
  /** the tab bar draws a thin line between the store's work | the deliveries | the finished orders | all */
  group: 'work' | 'delivery' | 'finished' | 'all'
}

export const ORDER_TABS: OrderTab[] = [
  { key: 'new', label: 'New', status: 'CONFIRMED', counted: true, empty: 'No new orders right now. New orders show up here by themselves.', group: 'work' },
  { key: 'accepted', label: 'Accepted', status: 'ACCEPTED', counted: true, empty: 'No accepted orders waiting to be packed.', group: 'work' },
  { key: 'packed', label: 'Packed', status: 'PACKED', counted: true, empty: 'No packed orders waiting to be handed over.', group: 'work' },
  { key: 'ready', label: 'Ready for pickup', status: 'READY_FOR_PICKUP', empty: 'No orders waiting for a delivery partner.', group: 'delivery' },
  { key: 'on-the-way', label: 'On the way', status: 'OUT_FOR_DELIVERY', empty: 'No orders on the way.', group: 'delivery' },
  { key: 'delivered', label: 'Delivered', status: 'DELIVERED', empty: 'No delivered orders yet.', group: 'finished' },
  { key: 'cancelled', label: 'Cancelled', status: 'CANCELLED', empty: 'No cancelled orders.', group: 'finished' },
  { key: 'rejected', label: 'Rejected', status: 'REJECTED', empty: 'No rejected orders.', group: 'finished' },
  { key: 'all', label: 'All', empty: 'No orders yet.', group: 'all' },
]

/** "/vendor/orders?tab=packed&page=2" (the "New" tab and page 1 are left out). */
export function vendorOrdersLink(tabKey: string, page = 1): string {
  const params = new URLSearchParams()
  if (tabKey !== 'new') params.set('tab', tabKey)
  if (page > 1) params.set('page', String(page))
  const query = params.toString()
  return query ? `/vendor/orders?${query}` : '/vendor/orders'
}

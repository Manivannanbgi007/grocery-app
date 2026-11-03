import type { AppNotification, UserRole } from '../../api/types'

/**
 * Where a notification leads. About an order it opens that order (referenceId is its id): the customer's order page,
 * or for a vendor ("New order #12", "Order #12 was cancelled") the order in the vendor panel. A partner's open the
 * delivery panel. null: it leads nowhere (a welcome, a password reset).
 */
export function notificationLink(notification: AppNotification, role: UserRole): string | null {
  // A partner's "New delivery" (its reference is the order, not the delivery) and the KYC result open today's tasks
  if (role === 'DELIVERY_PARTNER') return notification.type === 'NEW_DELIVERY' || notification.type === 'PARTNER_KYC_REVIEWED' ? '/delivery' : null
  if (notification.referenceId === null) return null
  if (role === 'VENDOR') {
    // "Low stock: product #12" is about a product
    if (notification.type === 'LOW_STOCK') return `/vendor/products/${notification.referenceId}`
    return /^(NEW_ORDER|ORDER_)/.test(notification.type) ? `/vendor/orders/${notification.referenceId}` : null
  }
  return /^(ORDER_|DELIVERY_PARTNER_|PAYMENT_|REFUND_)/.test(notification.type) ? `/orders/${notification.referenceId}` : null
}

/** The page with all notifications of a role (admins have no notifications). */
export function notificationsPageOf(role: UserRole): string | null {
  if (role === 'CUSTOMER') return '/account/notifications'
  if (role === 'VENDOR') return '/vendor/notifications'
  if (role === 'DELIVERY_PARTNER') return '/delivery/notifications'
  return null
}

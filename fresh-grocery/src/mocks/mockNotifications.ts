import type { AppNotification } from '../api/types'
import { MockApiError } from './mockApiError'

/*
 * In-app notifications for the mock API (in memory). notification-service creates them from events; here the mock
 * order saga calls notify() at the same moments (order confirmed, cancelled, payment failed, refund), with the same
 * titles and texts (NotificationTemplates).
 */

let notifications: AppNotification[] = []
let nextNotificationId = 1

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * 60 * 60 * 1000).toISOString()
}

function seedNotifications(): AppNotification[] {
  const seed = (id: number, type: string, title: string, message: string, referenceId: number | null, read: boolean, createdAt: string, userId = 4): AppNotification => ({
    id,
    userId,
    type,
    title,
    message,
    referenceId,
    read,
    emailStatus: 'SENT',
    createdAt,
  })
  // The seed orders (see mockOrders.ts): customer1's 901 delivered 3 days ago and 902 cancelled 2 days ago;
  // 903 of customer3 waiting for Fresh Mart (vendor1, user 3) since 20 minutes
  return [
    seed(1, 'WELCOME', 'Welcome to Grocery, Priya Sharma!', 'Your account is ready. Fresh groceries from stores near you are a few taps away.', null, true, hoursAgo(24 * 30)),
    seed(2, 'ORDER_CONFIRMED', 'Order #901 confirmed', 'Please pay ₹177 in cash on delivery. We will tell you when the store accepts it.', 901, true, hoursAgo(72)),
    seed(3, 'ORDER_DELIVERED', 'Order #901 delivered', 'Enjoy your groceries! Order total: ₹177.', 901, false, hoursAgo(71)),
    seed(4, 'ORDER_CANCELLED', 'Order #902 cancelled', 'Reason: Ordered by mistake.', 902, false, hoursAgo(48)),
    seed(5, 'NEW_ORDER', 'New order #903', 'A new order of ₹196 (COD) is waiting. Accept or reject it in your store panel.', 903, false, hoursAgo(1 / 3), 3),
  ]
}

notifications = seedNotifications()
nextNotificationId = 100

/** For tests: back to the seed notifications. */
export function resetMockNotifications(): void {
  notifications = seedNotifications()
  nextNotificationId = 100
}

/** Adds an unread notification for the user (called by the mock order saga). */
export function notify(userId: number, type: string, title: string, message: string, referenceId: number | null = null): void {
  notifications.push({ id: nextNotificationId++, userId, type, title, message, referenceId, read: false, emailStatus: 'SENT', createdAt: new Date().toISOString() })
}

/** The admin's notification log: everyone's, newest first, filtered by user, type and email status. */
export function searchAllNotifications(filter: { userId: number | null; type: string | null; emailStatus: string | null }): AppNotification[] {
  return notifications
    .filter((notification) => (filter.userId === null || notification.userId === filter.userId) && (!filter.type || notification.type === filter.type) && (!filter.emailStatus || notification.emailStatus === filter.emailStatus))
    .sort((a, b) => b.id - a.id)
}

/** The user's notifications, newest first. */
export function listNotifications(userId: number, unreadOnly: boolean): AppNotification[] {
  return notifications.filter((notification) => notification.userId === userId && (!unreadOnly || !notification.read)).sort((a, b) => b.id - a.id)
}

export function countUnread(userId: number): number {
  return listNotifications(userId, true).length
}

export function markRead(userId: number, notificationId: number): AppNotification {
  const notification = notifications.find((candidate) => candidate.id === notificationId && candidate.userId === userId)
  if (!notification) throw new MockApiError(404, `Notification not found with id: ${notificationId}`)
  notification.read = true
  return notification
}

/** Answers how many were marked. */
export function markAllRead(userId: number): number {
  const unread = listNotifications(userId, true)
  for (const notification of unread) notification.read = true
  return unread.length
}

import { apiRequest } from './client'
import type { AppNotification, PageResponse } from './types'

/*
 * The logged-in user's in-app notifications (notification-service): the bell in the header.
 * They are created in the background from the order events (confirmed, packed, delivered, refunded ...).
 */

/** Newest first. `page` starts at 0. */
export function getMyNotifications(params: { unreadOnly?: boolean; page?: number; size?: number }, signal?: AbortSignal): Promise<PageResponse<AppNotification>> {
  return apiRequest<PageResponse<AppNotification>>('/api/v1/notifications', { query: { ...params }, signal })
}

export function getUnreadCount(signal?: AbortSignal): Promise<number> {
  return apiRequest<{ count: number }>('/api/v1/notifications/unread-count', { signal }).then((result) => result.count)
}

export function markNotificationRead(notificationId: number): Promise<AppNotification> {
  return apiRequest<AppNotification>(`/api/v1/notifications/${notificationId}/read`, { method: 'PATCH' })
}

/** Answers how many were marked. */
export function markAllNotificationsRead(): Promise<number> {
  return apiRequest<{ count: number }>('/api/v1/notifications/read-all', { method: 'POST' }).then((result) => result.count)
}

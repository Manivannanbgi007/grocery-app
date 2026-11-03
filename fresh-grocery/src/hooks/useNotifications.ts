import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getMyNotifications, getUnreadCount, markAllNotificationsRead, markNotificationRead } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../features/auth/authContext'

/** The bell asks for new notifications once a minute (only while the tab is visible). */
const UNREAD_POLL_MS = 60 * 1000
/** How many notifications the bell menu shows. */
const LATEST_COUNT = 6
/** Rows per page of the notifications page. */
export const NOTIFICATIONS_PAGE_SIZE = 20

/** The number on the bell (nothing is loaded for a guest). */
export function useUnreadCount() {
  const { user } = useAuth()
  return useQuery({
    queryKey: queryKeys.unreadCount(user?.id ?? 0),
    queryFn: ({ signal }) => getUnreadCount(signal),
    enabled: user !== null,
    refetchInterval: UNREAD_POLL_MS,
  })
}

/** The latest notifications (or the latest unread ones), loaded when the bell menu opens. */
export function useLatestNotifications(enabled: boolean, unreadOnly = false) {
  const { user } = useAuth()
  return useQuery({
    queryKey: queryKeys.latestNotifications(user?.id ?? 0, unreadOnly),
    queryFn: ({ signal }) => getMyNotifications({ unreadOnly: unreadOnly || undefined, size: LATEST_COUNT }, signal).then((page) => page.content),
    enabled: enabled && user !== null,
    staleTime: 0,
  })
}

/** One page (1-based) of all notifications, or of the unread ones: the notifications page. */
export function useNotificationPage(unreadOnly: boolean, page: number, size = NOTIFICATIONS_PAGE_SIZE) {
  const { user } = useAuth()
  return useQuery({
    queryKey: queryKeys.notificationPage(user?.id ?? 0, unreadOnly, page, size),
    queryFn: ({ signal }) => getMyNotifications({ unreadOnly: unreadOnly || undefined, page: page - 1, size }, signal),
    enabled: user !== null,
    placeholderData: keepPreviousData,
  })
}

/** Marking notifications as read; afterwards the count and the list are loaded again. */
export function useNotificationChanges() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const reload = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications(user?.id ?? 0) })
  return {
    markRead: useMutation({ mutationFn: (notificationId: number) => markNotificationRead(notificationId), onSuccess: reload }),
    markAllRead: useMutation({ mutationFn: markAllNotificationsRead, onSuccess: reload }),
  }
}

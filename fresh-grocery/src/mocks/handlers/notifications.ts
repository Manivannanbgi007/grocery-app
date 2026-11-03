import { delay, http, type HttpResponseResolver } from 'msw'
import type { DemoUser } from '../data/users'
import { MockApiError } from '../mockApiError'
import { userFromRequest } from '../mockAuth'
import { countUnread, listNotifications, markAllRead, markRead } from '../mockNotifications'
import { fail, intParam, ok, paginate } from './respond'

/* Mock of notification-service: the bell menu. Every logged-in user has notifications (not only customers). */

function asUser(action: (user: DemoUser, request: Request, params: Record<string, string | readonly string[] | undefined>) => { data: unknown; message: string }): HttpResponseResolver {
  return async ({ request, params }) => {
    await delay()
    const user = userFromRequest(request)
    if (!user) return fail(401, 'Please log in to access this resource', request)
    try {
      const { data, message } = action(user, request, params)
      return ok(data, message)
    } catch (error) {
      if (error instanceof MockApiError) return fail(error.status, error.message, request)
      throw error
    }
  }
}

export const notificationHandlers = [
  http.get(
    '*/api/v1/notifications',
    asUser((user, request) => {
      const params = new URL(request.url).searchParams
      const list = listNotifications(user.id, params.get('unreadOnly') === 'true')
      return { data: paginate(list, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Notifications fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/notifications/unread-count',
    asUser((user) => ({ data: { count: countUnread(user.id) }, message: 'Unread notifications counted' })),
  ),

  http.patch(
    '*/api/v1/notifications/:notificationId/read',
    asUser((user, _request, params) => ({ data: markRead(user.id, Number(params.notificationId)), message: 'Notification marked as read' })),
  ),

  http.post(
    '*/api/v1/notifications/read-all',
    asUser((user) => ({ data: { count: markAllRead(user.id) }, message: 'All notifications marked as read' })),
  ),
]

import { Bell, BellOff, CheckCheck } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import type { AppNotification, UserRole } from '../../api/types'
import { useAuth } from '../../features/auth/authContext'
import { notificationLink, notificationsPageOf } from '../../features/notifications/notificationLinks'
import { NotificationRow } from '../../features/notifications/NotificationRow'
import { useDismiss } from '../../hooks/useDismiss'
import { useLatestNotifications, useNotificationChanges, useUnreadCount } from '../../hooks/useNotifications'
import { cn } from '../../utils/cn'
import { ErrorState } from '../ui/ErrorState'
import { Skeleton } from '../ui/Skeleton'

/**
 * The bell (logged-in users only): the unread count, and a card with the latest notifications (all or only unread),
 * "Mark as read" per notification, "Mark all as read" and the way to the page with all of them.
 * Admins have none (notification-service sends nothing to them), so they get no bell.
 */
export function NotificationBell() {
  const { user, admin } = useAuth()
  return user && !admin ? <BellMenu role={user.role} /> : null
}

function BellMenu({ role }: { role: UserRole }) {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuId = useId()
  const { data: unread = 0 } = useUnreadCount()
  const latest = useLatestNotifications(open, unreadOnly)
  const { markRead, markAllRead } = useNotificationChanges()
  const allPage = notificationsPageOf(role)

  useDismiss(containerRef, open, (reason) => {
    setOpen(false)
    if (reason === 'escape') buttonRef.current?.focus()
  })

  function openNotification(notification: AppNotification) {
    setOpen(false)
    if (!notification.read) markRead.mutate(notification.id)
    const link = notificationLink(notification, role)
    if (link) navigate(link)
  }

  let list
  if (latest.isPending) {
    list = (
      <div className="space-y-2 p-3" role="status" aria-label="Loading the notifications">
        <Skeleton className="h-14" />
        <Skeleton className="h-14" />
      </div>
    )
  } else if (latest.isError) {
    list = <ErrorState compact title="Could not load your notifications" error={latest.error} onRetry={() => void latest.refetch()} />
  } else if (latest.data.length === 0) {
    list = (
      <div className="flex flex-col items-center gap-2 px-4 py-8 text-center text-sm">
        <BellOff className="size-8 text-muted" aria-hidden="true" />
        {unreadOnly ? "You're all caught up: no unread notifications." : 'No notifications yet. News about your orders will show up here.'}
      </div>
    )
  } else {
    list = (
      <ul aria-label="Latest notifications" className="max-h-96 divide-y divide-line overflow-y-auto">
        {latest.data.map((notification) => (
          <NotificationRow key={notification.id} notification={notification} onOpen={openNotification} onMarkRead={(item) => markRead.mutate(item.id)} compact />
        ))}
      </ul>
    )
  }

  const filterButton = (active: boolean) =>
    cn('rounded-lg px-3 py-1 text-xs font-bold transition-colors', active ? 'bg-card text-brand-text shadow-sm' : 'text-heading hover:text-brand-text')

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((isOpen) => !isOpen)}
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={`Notifications, ${unread} unread`}
        className="relative flex text-heading hover:text-brand-text"
      >
        <Bell className="size-6" strokeWidth={1.75} aria-hidden="true" />
        {unread > 0 && (
          <span className="absolute -top-2 -right-2.5 flex size-5 items-center justify-center rounded-full bg-hot text-[0.65rem] font-bold text-white">
            {unread > 99 ? '99+' : unread}
          </span>
        )}
      </button>

      {open && (
        // Phones: the width of the screen (the bell is not at its right edge); bigger screens: under the bell
        <section
          id={menuId}
          aria-labelledby={`${menuId}-title`}
          className="fixed inset-x-4 top-16 z-50 overflow-hidden rounded-2xl border border-line bg-card shadow-xl sm:absolute sm:inset-x-auto sm:top-full sm:right-0 sm:mt-3 sm:w-96"
        >
          <div className="flex items-center justify-between gap-2 px-4 pt-4 pb-3">
            <h2 id={`${menuId}-title`} className="flex items-center gap-2 text-base">
              Notifications
              {unread > 0 && <span className="rounded-full bg-hot px-2 py-0.5 text-xs font-bold text-white">{unread} new</span>}
            </h2>
            {unread > 0 && (
              <button
                type="button"
                onClick={() => markAllRead.mutate()}
                disabled={markAllRead.isPending}
                className="flex items-center gap-1 text-xs font-bold text-brand-text hover:underline disabled:opacity-50"
              >
                <CheckCheck className="size-4" aria-hidden="true" />
                Mark all as read
              </button>
            )}
          </div>
          <div role="group" aria-label="Show" className="mx-4 mb-2 inline-flex rounded-xl bg-surface p-1">
            <button type="button" aria-pressed={!unreadOnly} onClick={() => setUnreadOnly(false)} className={filterButton(!unreadOnly)}>
              All
            </button>
            <button type="button" aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)} className={filterButton(unreadOnly)}>
              Unread
            </button>
          </div>
          <div className="border-t border-line">{list}</div>
          {allPage && (
            <Link to={allPage} onClick={() => setOpen(false)} className="block border-t border-line px-4 py-3 text-center text-sm font-bold text-brand-text hover:bg-brand-light">
              View all notifications
            </Link>
          )}
        </section>
      )}
    </div>
  )
}

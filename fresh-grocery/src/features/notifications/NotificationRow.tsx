import { Check } from 'lucide-react'
import type { AppNotification } from '../../api/types'
import { cn } from '../../utils/cn'
import { formatRelative } from '../../utils/dates'
import { formatDateTime } from '../../utils/format'
import { notificationLook, TONE_CLASSES } from './notificationLooks'

interface NotificationRowProps {
  notification: AppNotification
  /** opens what the notification is about (and marks it as read) */
  onOpen: (notification: AppNotification) => void
  onMarkRead: (notification: AppNotification) => void
  /** the bell's card: the message in two lines at most */
  compact?: boolean
  /** 0, 1, 2 …: only for the look, the rows rise in one after the other */
  order?: number
}

/**
 * One notification: an icon for its kind, the title (bold while unread), the message, "5 min ago" (the exact time on
 * hover), a dot while unread, and a "Mark as read" button next to it (two buttons side by side, never one in another).
 */
export function NotificationRow({ notification, onOpen, onMarkRead, compact = false, order = 0 }: NotificationRowProps) {
  const { icon: Icon, tone } = notificationLook(notification.type)
  const exactTime = formatDateTime(notification.createdAt)
  const unread = !notification.read

  return (
    // Rises in (50 ms after the row above it; "reduce motion" turns that off); an unread one has a light green background
    <li className={cn('flex items-start gap-1 transition-colors motion-safe:animate-rise', unread && 'bg-brand-light/50')} style={{ animationDelay: `${order * 50}ms` }}>
      <button
        type="button"
        onClick={() => onOpen(notification)}
        // One clear sentence for screen readers ("Unread: Order #901 delivered. Enjoy your groceries! (26 Oct 2026, 3:45 pm)")
        aria-label={`${unread ? 'Unread: ' : ''}${notification.title}. ${notification.message} (${exactTime})`}
        className={cn('flex min-w-0 flex-1 gap-3 py-3 pl-4 text-left hover:bg-brand-light', compact ? 'pr-1' : 'pr-2')}
      >
        <span aria-hidden="true" className={cn('grid size-9 shrink-0 place-items-center rounded-xl shadow-sm', TONE_CLASSES[tone])}>
          <Icon className="size-4.5" />
        </span>
        <span className="min-w-0 flex-1 text-sm">
          <span className="flex items-start gap-2">
            <span className={cn('min-w-0 flex-1 text-heading', unread && 'font-bold')}>{notification.title}</span>
            {unread && <span aria-hidden="true" className="mt-1.5 size-2 shrink-0 rounded-full bg-brand" />}
          </span>
          <span className={cn('block', compact && 'line-clamp-2')}>{notification.message}</span>
          <time dateTime={notification.createdAt} title={exactTime} className="mt-0.5 block text-xs">
            {formatRelative(notification.createdAt)}
          </time>
        </span>
      </button>
      {unread && (
        <button
          type="button"
          onClick={() => onMarkRead(notification)}
          aria-label={`Mark as read: ${notification.title}`}
          title="Mark as read"
          className="mt-3 mr-2 grid size-8 shrink-0 place-items-center rounded-full text-heading hover:bg-card hover:text-brand-text"
        >
          <Check className="size-4" aria-hidden="true" />
        </button>
      )}
    </li>
  )
}

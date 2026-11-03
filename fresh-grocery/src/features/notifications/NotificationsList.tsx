import { CheckCheck } from 'lucide-react'
import { useNavigate } from 'react-router'
import type { AppNotification, UserRole } from '../../api/types'
import { StatusTabs } from '../../components/panel/StatusTabs'
import { EmptyState } from '../../components/ui/EmptyState'
import { ToneBar } from '../../components/ui/ToneIcon'
import { toneCard } from '../../components/ui/tones'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { TablePagination } from '../../components/ui/TablePagination'
import { NOTIFICATIONS_PAGE_SIZE, useNotificationChanges, useNotificationPage, useUnreadCount } from '../../hooks/useNotifications'
import { useUrlFilters } from '../../hooks/useUrlFilters'
import { cn } from '../../utils/cn'
import { notificationLink } from './notificationLinks'
import { NotificationRow } from './NotificationRow'

/**
 * Every notification of the logged-in user, newest first: All / Unread (?show=unread), page controls above and below,
 * "Mark as read" per row and "Mark all as read". The shop's account area and the vendor and delivery panels show it.
 */
export function NotificationsList({ role }: { role: UserRole }) {
  const navigate = useNavigate()
  const { page, pageSize, filter, linkWith, goToPage, setPageSize } = useUrlFilters(NOTIFICATIONS_PAGE_SIZE)
  const unreadOnly = filter('show') === 'unread'
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useNotificationPage(unreadOnly, page, pageSize)
  const { data: unread = 0 } = useUnreadCount()
  const { markRead, markAllRead } = useNotificationChanges()

  function openNotification(notification: AppNotification) {
    if (!notification.read) markRead.mutate(notification.id)
    const link = notificationLink(notification, role)
    if (link) navigate(link)
  }

  let content
  if (isPending) {
    content = (
      <div className="space-y-2" role="status" aria-label="Loading the notifications">
        <Skeleton className="h-20" />
        <Skeleton className="h-20" />
        <Skeleton className="h-20" />
      </div>
    )
  } else if (isError) {
    content = <ErrorState title="Could not load your notifications" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon="🔔"
        title={unreadOnly ? "You're all caught up" : 'No notifications yet'}
        message={unreadOnly ? 'Every notification has been read.' : 'News about your orders and your account will show up here.'}
        className="rounded-3xl bg-linear-to-br from-warn-light via-card to-sale-light"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['notification', 'notifications']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        {/* A yellow card (yellow: things to look at); its rows rise in one after the other */}
        <div className={toneCard('yellow')}>
          <ToneBar tone="yellow" />
          <ul aria-label="Notifications" className={cn('divide-y divide-line', isPlaceholderData && 'opacity-60')}>
            {data.content.map((notification, index) => (
              <NotificationRow key={notification.id} notification={notification} onOpen={openNotification} onMarkRead={(item) => markRead.mutate(item.id)} order={index} />
            ))}
          </ul>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['notification', 'notifications']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-x-4">
        <StatusTabs
          label="Show"
          active={unreadOnly ? 'unread' : 'all'}
          tabs={[
            { key: 'all', label: 'All', to: linkWith('show', '') },
            { key: 'unread', label: 'Unread', to: linkWith('show', 'unread'), count: unread },
          ]}
        />
        {unread > 0 && (
          <button
            type="button"
            onClick={() => markAllRead.mutate()}
            disabled={markAllRead.isPending}
            className="mb-5 flex items-center gap-1.5 rounded-full bg-brand-light px-4 py-2 text-sm font-bold text-brand-text transition-colors hover:bg-brand hover:text-white disabled:opacity-50"
          >
            <CheckCheck className="size-4" aria-hidden="true" />
            Mark all as read
          </button>
        )}
      </div>
      {content}
    </>
  )
}

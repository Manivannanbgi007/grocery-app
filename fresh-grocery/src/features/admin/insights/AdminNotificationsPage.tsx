import { Bell, Bike, CheckCheck, CreditCard, Inbox, MailCheck, MailMinus, MailX, Package, PackageX, Store, UserRound, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterBar } from '../../../components/panel/FilterBar'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { PageHeader } from '../../../components/panel/PageHeader'
import { SearchBox } from '../../../components/panel/SearchBox'
import { CELL, HEAD_CELL, ROW_ICON, TABLE_CARD, TABLE_HEAD } from '../../../components/panel/tableClasses'
import { StatCard } from '../../../components/sales/StatCard'
import type { BadgeTone } from '../../../components/ui/Badge'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatDateTime } from '../../../utils/format'
import { NotificationsIllustration } from '../illustrations/NotificationsIllustration'
import { NOTIFICATIONS_PER_PAGE, useNotificationLog } from '../marketing/useAdminMarketing'

/** NotificationType of notification-service, in the order of its comments. */
const TYPES = [
  'WELCOME',
  'PASSWORD_RESET',
  'STORE_REGISTERED',
  'STORE_APPROVED',
  'STORE_STATUS_CHANGED',
  'ORDER_CONFIRMED',
  'NEW_ORDER',
  'ORDER_ACCEPTED',
  'ORDER_REJECTED',
  'ORDER_PACKED',
  'ORDER_OUT_FOR_DELIVERY',
  'ORDER_DELIVERED',
  'ORDER_CANCELLED',
  'ORDER_DELIVERY_FAILED',
  'DELIVERY_PARTNER_ASSIGNED',
  'NEW_DELIVERY',
  'PARTNER_KYC_REVIEWED',
  'PAYMENT_FAILED',
  'REFUND_COMPLETED',
  'LOW_STOCK',
]

const EMAIL_STATUSES: { value: string; label: string; tone: BadgeTone; icon: LucideIcon }[] = [
  { value: 'SENT', label: 'Email sent', tone: 'brand', icon: MailCheck },
  { value: 'FAILED', label: 'Email failed', tone: 'hot', icon: MailX },
  { value: 'NOT_SENT', label: 'No email', tone: 'neutral', icon: MailMinus },
]

/** "ORDER_OUT_FOR_DELIVERY" -> "Order out for delivery" */
const typeLabel = (type: string) => type.charAt(0) + type.slice(1).toLowerCase().replaceAll('_', ' ')

/** An icon for the kind of message: an order, a delivery, a store, money, stock, or the account itself. */
function typeIcon(type: string): LucideIcon {
  if (type.startsWith('ORDER') || type === 'NEW_ORDER') return Package
  if (type.includes('DELIVERY') || type.startsWith('PARTNER')) return Bike
  if (type.startsWith('STORE')) return Store
  if (type.startsWith('PAYMENT') || type.startsWith('REFUND')) return CreditCard
  if (type === 'LOW_STOCK') return PackageX
  return UserRound
}

/**
 * Every notification that was sent (in the app, and by email when there is an address), newest first. The failed
 * emails are the ones to look at. /admin/notifications?email=FAILED&type=ORDER_CONFIRMED&user=4
 */
export function AdminNotificationsPage() {
  const { page, pageSize, filter, idFilter, setFilter, linkWith, goToPage, setPageSize } = useUrlFilters(NOTIFICATIONS_PER_PAGE)
  const emailStatus = EMAIL_STATUSES.find((status) => status.value === filter('email'))?.value
  const type = TYPES.find((candidate) => candidate === filter('type'))
  const userId = idFilter('user')
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useNotificationLog({ emailStatus, type, userId }, page, pageSize)
  const filtered = Boolean(emailStatus || type || userId)
  // The cards count with the type and account filters that are set (the total of a one-line page each)
  const all = useNotificationLog({ type, userId }, 1, 1).data?.totalElements
  const sent = useNotificationLog({ emailStatus: 'SENT', type, userId }, 1, 1).data?.totalElements
  const failed = useNotificationLog({ emailStatus: 'FAILED', type, userId }, 1, 1).data?.totalElements
  const noEmail = useNotificationLog({ emailStatus: 'NOT_SENT', type, userId }, 1, 1).data?.totalElements

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the notifications" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon={<NotificationsIllustration className="h-32 w-auto" />}
        title={filtered ? 'No notifications match these filters' : 'No notifications yet'}
        action={filtered ? <ButtonLink to="/admin/notifications">Clear the filters</ButtonLink> : undefined}
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['notification', 'notifications']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn(TABLE_CARD, isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[56rem] text-left text-sm">
            <caption className="sr-only">Notifications, newest first ({data.totalElements})</caption>
            <thead className={TABLE_HEAD}>
              <tr>
                <th scope="col" className={HEAD_CELL}>Message</th>
                <th scope="col" className={HEAD_CELL}>To</th>
                <th scope="col" className={HEAD_CELL}>Sent</th>
                <th scope="col" className={HEAD_CELL}>Seen</th>
                <th scope="col" className={HEAD_CELL}>Email</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((notification) => {
                const email = EMAIL_STATUSES.find((status) => status.value === notification.emailStatus)
                const TypeIcon = typeIcon(notification.type)
                return (
                  <tr key={notification.id}>
                    <td className={CELL}>
                      <div className="flex gap-3">
                        <span className={cn(ROW_ICON, notification.read ? 'bg-surface text-body' : 'bg-sale-light text-sale-text')}>
                          <TypeIcon className="size-5" aria-hidden="true" />
                        </span>
                        <div className="min-w-0">
                          <span className="block text-xs">{typeLabel(notification.type)}</span>
                          <span className="block font-bold text-heading">{notification.title}</span>
                          <span className="block">{notification.message}</span>
                        </div>
                      </div>
                    </td>
                    <td className={CELL}>
                      <Link to={`/admin/accounts/${notification.userId}`} className="whitespace-nowrap hover:text-brand-text hover:underline">
                        Account #{notification.userId}
                      </Link>
                    </td>
                    <td className={cn(CELL, 'whitespace-nowrap')}>{formatDateTime(notification.createdAt)}</td>
                    <td className={CELL}>
                      {/* Whether the person opened it in the app: a blue dot until they do */}
                      {notification.read ? (
                        <StatusBadge tone="neutral" icon={CheckCheck}>
                          Read
                        </StatusBadge>
                      ) : (
                        <StatusBadge tone="sale">Not read yet</StatusBadge>
                      )}
                    </td>
                    <td className={CELL}>
                      {email && (
                        <StatusBadge tone={email.tone} icon={email.icon}>
                          {email.label}
                        </StatusBadge>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['notification', 'notifications']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Notifications" />
      <PageHeader
        title="Notifications"
        icon={Bell}
        accent="blue"
        description="Every message the platform sent, in the app and by email. Failed emails are the ones to look at."
        illustration={<NotificationsIllustration />}
      />
      {/* The numbers per email status; each card shows those notifications (the type and account filters stay) */}
      <section aria-label="Notifications in numbers" className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <StatCard label="All notifications" value={all ?? null} hint="in the app" icon={Inbox} accent="blue" to={linkWith('email', '')} />
        <StatCard label="Emails sent" value={sent ?? null} hint="reached the mail server" icon={MailCheck} accent="green" to={linkWith('email', 'SENT')} />
        <StatCard label="Emails failed" value={failed ?? null} hint="worth a look" icon={MailX} accent={failed ? 'pink' : 'green'} to={linkWith('email', 'FAILED')} />
        <StatCard label="No email" value={noEmail ?? null} hint="no address, or not needed" icon={MailMinus} accent="orange" to={linkWith('email', 'NOT_SENT')} />
      </section>
      <FilterBar>
        <FilterSelect label="Email" allLabel="Every email status" value={emailStatus ?? ''} options={EMAIL_STATUSES.map(({ value, label }) => ({ value, label }))} onChange={(value) => setFilter('email', value)} />
        <FilterSelect label="Type" allLabel="Every type" value={type ?? ''} options={TYPES.map((value) => ({ value, label: typeLabel(value) }))} onChange={(value) => setFilter('type', value)} />
        <SearchBox label="Notifications of one account" placeholder="Account #" value={userId ? String(userId) : ''} onSearch={(value) => setFilter('user', value.replace(/\D/g, ''))} />
      </FilterBar>
      {content}
    </>
  )
}

import { Link } from 'react-router'
import type { AuditEntry } from '../../../../api/types'
import { PageTitle } from '../../../../components/layout/PageTitle'
import { FilterSelect } from '../../../../components/panel/FilterSelect'
import { SearchBox } from '../../../../components/panel/SearchBox'
import { ButtonLink } from '../../../../components/ui/Button'
import { DateField } from '../../../../components/ui/DateField'
import { EmptyState } from '../../../../components/ui/EmptyState'
import { ErrorState } from '../../../../components/ui/ErrorState'
import { TablePagination } from '../../../../components/ui/TablePagination'
import { LoadingState } from '../../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../../hooks/useUrlFilters'
import { cn } from '../../../../utils/cn'
import { daysBefore, indiaDay, startOfIndiaDay } from '../../../../utils/dates'
import { formatDateTime } from '../../../../utils/format'
import { useStoreOptions } from '../../stores/useAdminStores'
import { AUDIT_SOURCES, eventName, eventTypesOf, orderIdOf, readableDetails, sourceLabel } from './auditEntries'
import { useAuditLog } from './useAuditLog'

const LINK = 'font-bold text-brand-text hover:underline'

/** One entry: what happened, where it comes from, who and what it is about, and the whole event when opened. */
function AuditEntryItem({ entry }: { entry: AuditEntry }) {
  const orderId = orderIdOf(entry.details)
  return (
    <li className="p-4 sm:p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 className="font-heading text-base">{eventName(entry.eventType)}</h2>
        <p className="text-sm whitespace-nowrap">{formatDateTime(entry.occurredAt)}</p>
      </div>
      <p className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-sm">
        <span>{sourceLabel(entry.source)}</span>
        {entry.aggregateId && <span>Reference {entry.aggregateId}</span>}
        {orderId !== null && (
          <Link to={`/admin/orders/${orderId}`} className={LINK}>
            Order #{orderId}
          </Link>
        )}
        {entry.userId !== null && (
          <Link to={`/admin/accounts/${entry.userId}`} className={LINK}>
            Account #{entry.userId}
          </Link>
        )}
        {entry.storeId !== null && (
          <Link to={`/admin/stores/${entry.storeId}`} className={LINK}>
            Store #{entry.storeId}
          </Link>
        )}
      </p>
      <details className="mt-2 text-sm">
        <summary className="cursor-pointer font-bold text-heading">Details</summary>
        <pre className="mt-2 overflow-x-auto rounded-lg bg-surface p-3 text-xs whitespace-pre-wrap break-all text-heading">{readableDetails(entry.details)}</pre>
      </details>
    </li>
  )
}

/**
 * Everything that happened on the platform, newest first: the events of the services and the platform changes.
 * /admin/audit-log?source=grocery.order.events&type=OrderCancelled&store=1&day=2026-10-26&ref=79&user=4
 */
export function AdminAuditLogPage() {
  const { page, pageSize, filter, idFilter, setFilter, setFilters, goToPage, setPageSize } = useUrlFilters()
  const source = filter('source')
  const eventType = filter('type')
  const day = filter('day')
  const reference = filter('ref')
  const userId = idFilter('user')
  const storeId = idFilter('store')
  const { data: stores = [] } = useStoreOptions()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAuditLog(
    {
      source: source || undefined,
      eventType: eventType || undefined,
      aggregateId: reference || undefined,
      userId,
      storeId,
      // One India day: from its first moment up to the next day's (not included)
      from: day ? startOfIndiaDay(day) : undefined,
      to: day ? startOfIndiaDay(daysBefore(day, -1)) : undefined,
    },
    page,
    pageSize,
  )
  const filtered = Boolean(source || eventType || day || reference || userId || storeId)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-96" />
  } else if (isError) {
    content = <ErrorState title="Could not load the audit log" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon="📜"
        title={filtered ? 'No entries match these filters' : 'Nothing recorded yet'}
        action={filtered ? <ButtonLink to="/admin/audit-log">Clear the filters</ButtonLink> : undefined}
        className="rounded-2xl bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['entry', 'entries']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <ul aria-label="Audit entries" className={cn('divide-y divide-line rounded-2xl border border-line bg-card', isPlaceholderData && 'opacity-60')}>
          {data.content.map((entry) => (
            <AuditEntryItem key={entry.id} entry={entry} />
          ))}
        </ul>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['entry', 'entries']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Audit log" />
      <h1 className="mb-2 text-3xl">Audit log</h1>
      <p className="mb-6 max-w-3xl text-sm">
        The events of every service (orders, payments, deliveries, stores, stock, accounts) and the changes to settings, feature flags and banners. New admins, role
        changes, accounts switched off and commission changes are not recorded: those services send no event for them.
      </p>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        {/* Another source has other event types: the event type filter is cleared with it */}
        <FilterSelect label="Source" allLabel="All sources" value={source} options={AUDIT_SOURCES.map(({ value, label }) => ({ value, label }))} onChange={(value) => setFilters({ source: value, type: '' })} />
        <FilterSelect label="Event type" allLabel="All events" value={eventType} options={eventTypesOf(source).map((type) => ({ value: type, label: eventName(type) }))} onChange={(value) => setFilter('type', value)} />
        <FilterSelect label="Store" allLabel="All stores" value={storeId ? String(storeId) : ''} options={stores.map((store) => ({ value: String(store.id), label: store.name }))} onChange={(value) => setFilter('store', value)} />
        <DateField label="Day" hideLabel size="sm" placeholder="Any day" clearable value={day} max={indiaDay()} onChange={(chosen) => setFilter('day', chosen)} className="w-44" />
      </div>
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <SearchBox label="Search by reference" placeholder="Reference, e.g. an order id" value={reference} onSearch={(value) => setFilter('ref', value)} />
        <SearchBox label="Search by account number" placeholder="Account number, e.g. 4" value={userId ? String(userId) : ''} onSearch={(value) => setFilter('user', value.replace(/\D/g, ''))} />
      </div>
      {content}
    </>
  )
}

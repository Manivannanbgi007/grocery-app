import { BellRing, CircleAlert, Package, RefreshCw } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { StorefrontIllustration } from '../../../components/auth/illustrations/StorefrontIllustration'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PageHeader } from '../../../components/panel/PageHeader'
import { StatusTabs } from '../../../components/panel/StatusTabs'
import { Appear } from '../../../components/ui/Appear'
import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useNow } from '../../../hooks/useNow'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatTime } from '../../../utils/format'
import { OrderFlow } from './OrderFlow'
import { ORDER_TABS, vendorOrdersLink } from './orderTabs'
import { useOrderStreamState, type OrderStreamState } from './useStoreOrderStream'
import { QUEUE_POLL_MS, useRefreshStoreOrders, useStoreOrderCount, useStoreOrders } from './useVendorOrders'
import { VendorOrderCard } from './VendorOrderCard'
import { VENDOR_STATUS_TONES } from './vendorOrderStatus'

/** The dot of the live sign: green while live (with a ripple), amber while the live updates are down, grey while connecting. */
const STREAM_DOTS: Record<OrderStreamState, string> = {
  live: 'bg-brand',
  connecting: 'bg-line',
  reconnecting: 'bg-warn',
  failed: 'bg-warn',
}

/** Cards in the shape of the order cards, with a light sweeping over them, while the first list loads. */
function QueueSkeleton() {
  return (
    <div role="status" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <span className="sr-only">Loading the orders…</span>
      {[0, 1, 2].map((index) => (
        <div key={index} className="space-y-4 rounded-2xl border border-line bg-card p-5">
          <div className="flex gap-3">
            <Skeleton className="size-11 rounded-xl" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-5 w-32" />
              <Skeleton className="h-3 w-24" />
            </div>
          </div>
          <Skeleton className="h-8 w-28" />
          <Skeleton className="h-1.5 w-full rounded-full" />
          <Skeleton className="h-9 w-full rounded-xl" />
        </div>
      ))}
    </div>
  )
}

/** The big round icon of the empty "New" tab while live: it listens, so it ripples (a new order can come any moment). */
function ListeningIcon() {
  return (
    <span className="relative grid size-20 place-items-center rounded-full bg-new-light text-warn-text">
      <span className="absolute inset-0 rounded-full bg-new/20 motion-safe:animate-ping" />
      <BellRing className="relative size-9" aria-hidden="true" />
    </span>
  )
}

/**
 * The order queue: the store's work step by step (OrderFlow), one tab per status (New first, it is the store's to-do
 * list), and the orders as cards, newest first, each with its next step. It is live: the server reports every change and
 * new orders appear by themselves (useStoreOrderStream), sliding in like the cards when the page opens. While the live
 * updates are down, it checks every 15 s, says when it last checked, and "Check now" asks at once.
 */
export function VendorOrdersPage() {
  const [searchParams] = useSearchParams()
  const tab = ORDER_TABS.find((candidate) => candidate.key === searchParams.get('tab')) ?? ORDER_TABS[0]
  const { page, pageSize, goToPage, setPageSize } = useUrlFilters()
  const { data, isPending, isError, error, isFetching, dataUpdatedAt, isPlaceholderData } = useStoreOrders(tab.status, page, pageSize)
  const refresh = useRefreshStoreOrders()
  const stream = useOrderStreamState()
  const live = stream === 'live'
  const updatedAt = dataUpdatedAt > 0 ? formatTime(new Date(dataUpdatedAt).toISOString()) : null
  // "Waiting 12 min" on the new orders keeps counting
  const now = useNow(30_000)
  // The orders at each step (one tiny request each); the flow and the tabs show the same numbers
  const { data: newCount } = useStoreOrderCount('CONFIRMED')
  const { data: acceptedCount } = useStoreOrderCount('ACCEPTED')
  const { data: packedCount } = useStoreOrderCount('PACKED')
  const { data: readyCount } = useStoreOrderCount('READY_FOR_PICKUP')
  const tabCounts: Record<string, number | undefined> = { new: newCount, accepted: acceptedCount, packed: packedCount }

  let content
  if (isPending) {
    content = <QueueSkeleton />
  } else if (data === undefined) {
    // Only a failed first load: a failed refresh keeps the last list on screen (see the notice below)
    content = <ErrorState title="Could not load the orders" error={error} onRetry={() => void refresh()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon={tab.key === 'new' && live ? <ListeningIcon /> : <StorefrontIllustration className="h-32 w-auto" />}
        title={tab.empty}
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['order', 'orders']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <ul aria-label={`${tab.label} orders, newest first (${data.totalElements})`} className={cn('grid gap-4 transition-opacity sm:grid-cols-2 xl:grid-cols-3', isPlaceholderData && 'opacity-60')}>
          {data.content.map((order, index) => (
            <VendorOrderCard key={order.id} order={order} position={index} now={now} />
          ))}
        </ul>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['order', 'orders']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <div className="space-y-6">
      <PageTitle title="Orders" />
      <Appear order={0}>
        <PageHeader
          title="Orders"
          icon={Package}
          accent="orange"
          description="Your store's orders, newest first. Accept new ones, pack them and hand them over: each card has its next step."
          illustration={<StorefrontIllustration />}
          actions={
            // Live: nothing to do, new orders appear by themselves. Otherwise when the queue last checked (not read aloud
            // each time: it changes every 15 s) and a button to check now; its icon turns while a check is running
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <span className="inline-flex items-center gap-2 rounded-full border border-line bg-card/80 px-3 py-1.5 backdrop-blur">
                <span aria-hidden="true" className="relative flex size-2.5">
                  {live && !isError && <span className="absolute inline-flex size-full rounded-full bg-brand opacity-75 motion-safe:animate-ping" />}
                  <span className={cn('relative inline-flex size-2.5 rounded-full', isError ? 'bg-warn' : STREAM_DOTS[stream])} />
                </span>
                {live ? (
                  <span>
                    <strong className="font-bold text-brand-text">Live</strong> · new orders appear by themselves
                  </span>
                ) : updatedAt ? (
                  <span>
                    Updated {updatedAt} · checks every {QUEUE_POLL_MS / 1000} s
                  </span>
                ) : (
                  <span>Connecting…</span>
                )}
              </span>
              {!live && (
                <Button variant="outline" size="sm" onClick={() => void refresh()} disabled={isFetching} className="rounded-full">
                  <RefreshCw className={cn('size-4', isFetching && 'motion-safe:animate-spin')} aria-hidden="true" />
                  Check now
                </Button>
              )}
            </div>
          }
        />
      </Appear>

      {/* A refresh that failed (e.g. a service restarting): the last list stays, and the queue tries again by itself */}
      {isError && data !== undefined && (
        <div role="status" className="flex flex-wrap items-center gap-3 rounded-xl border border-warn/40 bg-warn-light px-4 py-3 text-sm text-heading">
          <CircleAlert className="size-5 shrink-0 text-warn-text" aria-hidden="true" />
          <p className="min-w-0 flex-1">
            Could not refresh the orders{error.message ? ` (${error.message})` : ''}. Showing the orders from {updatedAt}; trying again by itself.
          </p>
          <Button variant="outline" size="sm" onClick={() => void refresh()} disabled={isFetching}>
            Try now
          </Button>
        </div>
      )}

      <Appear order={1}>
        <OrderFlow counts={[newCount, acceptedCount, packedCount, readyCount]} activeTab={tab.key} />
      </Appear>
      <Appear order={2}>
        {/* Each tab has its status's colour; lines part the store's work | the deliveries | the finished orders | all */}
        <StatusTabs
          label="Order status"
          active={tab.key}
          tabs={ORDER_TABS.map((candidate) => ({
            key: candidate.key,
            label: candidate.label,
            to: vendorOrdersLink(candidate.key),
            count: candidate.counted ? tabCounts[candidate.key] : undefined,
            tone: candidate.status ? VENDOR_STATUS_TONES[candidate.status] : undefined,
            group: candidate.group,
          }))}
        />
        {content}
      </Appear>
    </div>
  )
}

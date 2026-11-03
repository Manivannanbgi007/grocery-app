import { CircleX, ListChecks, Navigation, Package, PackageCheck, type LucideIcon } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import type { OrderStatus, OrderSummary } from '../../api/types'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageHero } from '../../components/layout/PageHero'
import { PageTitle } from '../../components/layout/PageTitle'
import { Badge, type BadgeTone } from '../../components/ui/Badge'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { TablePagination } from '../../components/ui/TablePagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { TONES, type Tone } from '../../components/ui/tones'
import { useUrlFilters } from '../../hooks/useUrlFilters'
import { cn } from '../../utils/cn'
import { formatDateTime, formatPrice } from '../../utils/format'
import { RequireCustomer } from '../auth/RequireCustomer'
import { OpenOrders } from './OpenOrders'
import { hasLiveMap, ORDER_STATUS_LABELS, ORDER_STATUS_TONES, PAYMENT_METHOD_LABELS } from './orderStatus'
import { SpendingCard } from './SpendingCard'
import { ORDERS_PER_PAGE, useMyOrders, useOpenOrders } from './useOrders'

/*
 * The look (only the look): green, the colour of orders and money, with a colour per filter and per order status. The
 * blocks and the order cards rise in one after the other; the movement is motion-safe:animate-..., so "reduce motion"
 * turns it off.
 */

/**
 * The filters above the list. The backend filters on one status, so these are the ones customers look for. `icon` and
 * `tone` are only the look of the filter's pill.
 */
const FILTERS: { label: string; status?: OrderStatus; empty: string; icon: LucideIcon; tone: Tone }[] = [
  { label: 'All orders', empty: 'No orders yet', icon: ListChecks, tone: 'green' },
  { label: 'Delivered', status: 'DELIVERED', empty: 'No delivered orders', icon: PackageCheck, tone: 'blue' },
  { label: 'Cancelled', status: 'CANCELLED', empty: 'No cancelled orders', icon: CircleX, tone: 'pink' },
]

/** An order card's colours by its status badge's colour: the stripe on its left and its icon tile (Tailwind needs the whole names) */
const CARD_COLOURS: Record<BadgeTone, { stripe: string; tile: string }> = {
  brand: { stripe: 'bg-brand', tile: 'bg-brand-light text-brand-text' },
  sale: { stripe: 'bg-sale', tile: 'bg-sale-light text-sale-text' },
  new: { stripe: 'bg-new', tile: 'bg-new-light text-warn-text' },
  hot: { stripe: 'bg-hot', tile: 'bg-hot/10 text-hot-text' },
  neutral: { stripe: 'bg-line', tile: 'bg-surface text-heading' },
}

/** "/orders?status=DELIVERED": the filter, page and page size live in the URL, like the shop's filters (a new filter starts on page 1). */
function ordersLink(status: OrderStatus | undefined): string {
  return status ? `/orders?status=${status}` : '/orders'
}

/** The order history of the logged-in customer, newest first. */
export function OrdersPage() {
  return (
    <RequireCustomer>
      <OrdersContent />
    </RequireCustomer>
  )
}

/**
 * One order of the list: a stripe on the left and an icon tile in its status's colour, the number, status and total,
 * the details line and the buttons. It rises in (`index` sets when) and lifts a little under the mouse.
 */
function OrderCard({ order, index }: { order: OrderSummary; index: number }) {
  const colours = CARD_COLOURS[ORDER_STATUS_TONES[order.status]]
  return (
    <li
      className="relative flex gap-4 overflow-hidden rounded-3xl border border-line bg-card p-5 pl-6 transition duration-200 hover:-translate-y-0.5 hover:shadow-lg motion-safe:animate-rise"
      style={{ animationDelay: `${index * 60}ms` }}
    >
      <span aria-hidden="true" className={cn('absolute inset-y-0 left-0 w-1.5', colours.stripe)} />
      <span aria-hidden="true" className={cn('hidden size-12 shrink-0 place-items-center rounded-2xl sm:grid', colours.tile)}>
        <Package className="size-6" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          <h2 className="text-lg">
            <Link to={`/orders/${order.id}`} className="hover:text-brand-text hover:underline">
              Order #{order.id}
            </Link>
          </h2>
          <Badge tone={ORDER_STATUS_TONES[order.status]}>{ORDER_STATUS_LABELS[order.status]}</Badge>
          <span className="ml-auto font-heading text-xl font-bold text-heading">{formatPrice(order.totalAmount)}</span>
        </div>
        <p className="mt-1 text-sm">
          {formatDateTime(order.placedAt)} · {order.storeName} · {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'} · {PAYMENT_METHOD_LABELS[order.paymentMethod]}
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          <ButtonLink to={`/orders/${order.id}`} variant="light" size="sm" aria-label={`View order #${order.id}`}>
            View details
          </ButtonLink>
          {hasLiveMap(order.status) && (
            <ButtonLink to={`/orders/${order.id}/track`} size="sm">
              <Navigation className="size-4" aria-hidden="true" />
              Track on the map{' '}
              <span className="sr-only">(order #{order.id})</span>
            </ButtonLink>
          )}
        </div>
      </div>
    </li>
  )
}

function OrdersContent() {
  const [searchParams] = useSearchParams()
  const filter = FILTERS.find((candidate) => candidate.status === searchParams.get('status')) ?? FILTERS[0]
  const { page, pageSize, goToPage, setPageSize } = useUrlFilters(ORDERS_PER_PAGE)
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useMyOrders(filter.status, page, pageSize)
  const { openOrders } = useOpenOrders()

  let content
  if (isPending) {
    content = (
      <div className="space-y-4">
        {Array.from({ length: 3 }, (_, index) => (
          <Skeleton key={index} className="h-32 rounded-3xl" />
        ))}
      </div>
    )
  } else if (isError) {
    content = <ErrorState title="Could not load your orders" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <div className="rounded-3xl border border-line bg-linear-to-br from-brand-light via-card to-sale-light motion-safe:animate-rise">
        <EmptyState
          icon="📦"
          title={filter.empty}
          message={filter.status ? undefined : 'Your orders will be listed here once you have placed one.'}
          action={filter.status ? <ButtonLink to="/orders">Show all orders</ButtonLink> : <ButtonLink to="/shop">Start shopping</ButtonLink>}
        />
      </div>
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['order', 'orders']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <ul aria-label="Your orders" className={cn('space-y-4', isPlaceholderData && 'opacity-60')}>
          {data.content.map((order, index) => (
            <OrderCard key={order.id} order={order} index={index} />
          ))}
        </ul>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['order', 'orders']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-8" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="My orders" />
      <BreadcrumbBar crumbs={[{ label: 'My orders' }]} />
      <div className="page-container mt-8 max-w-4xl">
        <PageHero icon={Package} tone="green" title="My orders" subtitle="Follow your open orders, see your spending and every order you placed." />
        {openOrders.length > 0 && <OpenOrders orders={openOrders} title="On its way" description="Follow your open orders from the store to your door." />}
        <SpendingCard />
        <nav aria-label="Filter orders" className="mb-6">
          <ul className="flex flex-wrap gap-2">
            {FILTERS.map((candidate) => {
              const { icon: Icon, tone } = candidate
              const active = candidate === filter
              return (
                <li key={candidate.label}>
                  {/* The chosen filter is a full-colour pill; the others are light in their colour and fill under the mouse */}
                  <Link
                    to={ordersLink(candidate.status)}
                    aria-current={active ? 'page' : undefined}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-full px-4 py-2 text-sm font-bold transition duration-200',
                      active ? cn('shadow-md', TONES[tone].solid) : cn(TONES[tone].soft, TONES[tone].hover),
                    )}
                  >
                    <Icon className="size-4" aria-hidden="true" />
                    {candidate.label}
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>
        {content}
      </div>
    </>
  )
}

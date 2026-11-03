import { ArrowRight, Package } from 'lucide-react'
import { Link } from 'react-router'
import { Badge, type BadgeTone } from '../../../components/ui/Badge'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { cn } from '../../../utils/cn'
import { formatShortDay, indiaDay } from '../../../utils/dates'
import { formatPrice, formatTime } from '../../../utils/format'
import { ORDER_STATUS_LABELS, ORDER_STATUS_TONES } from '../../orders/orderStatus'
import { useAdminOrders } from '../orders/useAdminOrders'

const SHOWN = 5

/** The order's icon in a light shade of its status colour (the badge beside it names the status). */
const ICON_CLASSES: Record<BadgeTone, string> = {
  brand: 'bg-brand-light text-brand-text',
  hot: 'bg-hot/10 text-hot-text',
  sale: 'bg-sale-light text-sale-text',
  new: 'bg-new-light text-new',
  neutral: 'bg-surface text-heading',
}

/** "Today, 3:45 pm" or "26 Oct, 3:45 pm" (India time). */
function whenPlaced(placedAt: string): string {
  const day = indiaDay(placedAt)
  return `${day === indiaDay() ? 'Today' : formatShortDay(day)}, ${formatTime(placedAt)}`
}

/**
 * The newest orders of the platform as a feed: the first page of the order list (the same request as the Orders page).
 * The whole row opens the order (the order number is the link; its click area covers the row).
 */
export function LatestOrdersCard() {
  const { data, isPending, isError, error, refetch } = useAdminOrders({}, 1)

  let content
  if (isPending) content = <LoadingState className="min-h-72" />
  else if (isError) content = <ErrorState compact title="Could not load the orders" error={error} onRetry={() => void refetch()} />
  else if (data.content.length === 0) content = <p className="text-sm">No orders yet.</p>
  else {
    content = (
      <ul aria-labelledby="latest-orders-title" className="-mx-2 space-y-1">
        {data.content.slice(0, SHOWN).map((order) => (
          <li key={order.id} className="group relative flex items-center gap-3 rounded-xl px-2 py-2.5 transition-colors hover:bg-surface">
            <span className={cn('grid size-10 shrink-0 place-items-center rounded-xl transition-transform group-hover:scale-110', ICON_CLASSES[ORDER_STATUS_TONES[order.status]])}>
              <Package className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              {/* after:inset-0 stretches the link's click area over the whole row */}
              <Link to={`/admin/orders/${order.id}`} className="font-heading font-bold text-heading after:absolute after:inset-0 after:rounded-xl group-hover:text-brand-text">
                Order #{order.id}
              </Link>
              <p className="truncate text-xs">
                {order.storeName} · {whenPlaced(order.placedAt)}
              </p>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-1">
              <span className="font-bold text-heading tabular-nums">{formatPrice(order.totalAmount)}</span>
              <Badge tone={ORDER_STATUS_TONES[order.status]}>{ORDER_STATUS_LABELS[order.status]}</Badge>
            </div>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <section aria-labelledby="latest-orders-title" className="h-full rounded-2xl border border-line bg-card p-5 sm:p-6">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 id="latest-orders-title" className="text-xl">
          Latest orders
        </h2>
        <Link to="/admin/orders" className="group flex items-center gap-1 text-sm font-bold text-brand-text hover:underline">
          All orders
          <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
        </Link>
      </div>
      {content}
    </section>
  )
}

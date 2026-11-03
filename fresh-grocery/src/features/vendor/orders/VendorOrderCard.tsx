import {
  Banknote,
  BellRing,
  Bike,
  ChevronRight,
  CircleCheck,
  CircleX,
  Clock,
  CreditCard,
  Hourglass,
  PackageCheck,
  PackageOpen,
  ShoppingBasket,
  type LucideIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { OrderStatus, OrderSummary } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'
import { Appear } from '../../../components/ui/Appear'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { cn } from '../../../utils/cn'
import { formatRelative } from '../../../utils/dates'
import { formatDateTime, formatPrice } from '../../../utils/format'
import { StepButton } from './StepButton'
import { VENDOR_STATUS_LABELS, VENDOR_STATUS_TONES } from './vendorOrderStatus'

/** A new order that waited this long for the store gets an amber "Waiting 12 min". */
const LONG_WAIT_MINUTES = 10

/** The icon of each status, in its chip. */
const STATUS_ICONS: Record<OrderStatus, LucideIcon> = {
  PENDING: Hourglass,
  CONFIRMED: BellRing,
  ACCEPTED: PackageOpen,
  PACKED: PackageCheck,
  READY_FOR_PICKUP: Clock,
  OUT_FOR_DELIVERY: Bike,
  DELIVERED: CircleCheck,
  DELIVERY_FAILED: CircleX,
  CANCELLED: CircleX,
  REJECTED: CircleX,
}

/** The chip's gradient per tone (the status's colour, as in its badge). */
const CHIPS: Record<BadgeTone, string> = {
  brand: 'from-brand to-brand-dark shadow-brand/30',
  sale: 'from-sale to-sale-text shadow-sale/30',
  new: 'from-new to-hot shadow-new/30',
  hot: 'from-hot to-hot-text shadow-hot/30',
  neutral: 'from-ink-soft to-ink shadow-ink/20',
}

/** What the store does now, for the statuses without a step button. */
const NO_STEP_TEXTS: Partial<Record<OrderStatus, string>> = {
  PENDING: 'Not confirmed yet: do not prepare it',
  READY_FOR_PICKUP: 'Waiting for the delivery partner',
  OUT_FOR_DELIVERY: 'On the way to the customer',
  DELIVERED: 'Delivered: nothing more to do',
  DELIVERY_FAILED: 'The items come back to the store',
  CANCELLED: 'Cancelled: do not pack it',
  REJECTED: 'You rejected this order',
}

/** The way of an order, from the store's "New" to "Delivered": the card's progress bar. */
const FLOW: OrderStatus[] = ['CONFIRMED', 'ACCEPTED', 'PACKED', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED']

/** "Placed 5 min ago", "Placed yesterday, 10:10 pm", "Placed 26 Oct, 3:45 pm" (formatRelative in a sentence). */
function placedText(placedAt: string, now: number): string {
  const when = formatRelative(placedAt, now)
  return `Placed ${when.charAt(0).toLowerCase()}${when.slice(1)}`
}

/** A small label with an icon, e.g. "4 items" or "Cash on delivery". */
function Chip({ icon: Icon, className, children }: { icon: LucideIcon; className?: string; children: ReactNode }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full bg-surface px-2.5 py-1 text-xs font-bold text-heading', className)}>
      <Icon className="size-3.5 shrink-0" aria-hidden="true" />
      {children}
    </span>
  )
}

/**
 * How far the order got, as six small bars that fill up one after the other when the card appears (decoration: the
 * badge says the status). Orders that stopped or are not confirmed yet have none.
 */
function FlowBar({ status }: { status: OrderStatus }) {
  const reached = FLOW.indexOf(status)
  if (reached < 0) return null
  return (
    <div className="mt-4">
      <div aria-hidden="true" className="flex gap-1">
        {FLOW.map((step, index) => (
          <span key={step} className="h-1.5 flex-1 overflow-hidden rounded-full bg-line">
            {index <= reached && <span className="block h-full origin-left rounded-full bg-brand motion-safe:animate-grow-x" style={{ animationDelay: `${200 + index * 90}ms` }} />}
          </span>
        ))}
      </div>
      <p className="mt-1.5 text-xs">
        Step {reached + 1} of {FLOW.length}
      </p>
    </div>
  )
}

interface VendorOrderCardProps {
  order: OrderSummary
  /** its place on the page (0, 1, 2 …): the cards come in one after the other */
  position: number
  /** now, for "Waiting 12 min" (the page updates it every half minute) */
  now: number
}

/**
 * One order of the queue as a card: its number and status, how long it waits (new orders) or when it came, the total,
 * the items and the payment, how far it got, and the store's next step as a button. The order's number is the card's
 * link, stretched over the whole card (its ::after), so a click anywhere opens the order; the step button sits above it.
 * A new order glows softly until the store takes it.
 */
export function VendorOrderCard({ order, position, now }: VendorOrderCardProps) {
  const tone = VENDOR_STATUS_TONES[order.status]
  const Icon = STATUS_ICONS[order.status]
  const isNew = order.status === 'CONFIRMED'
  const waitedMinutes = Math.max(0, Math.floor((now - Date.parse(order.placedAt)) / 60_000))
  const noStep = NO_STEP_TEXTS[order.status]

  return (
    <li className="h-full">
      <Appear order={Math.min(position, 8)} className="h-full">
        <article
          className={cn(
            'group relative flex h-full flex-col rounded-2xl border bg-card p-4 transition duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-heading/10 motion-reduce:transition-none sm:p-5',
            isNew ? 'border-new/60' : 'border-line hover:border-brand-soft',
          )}
        >
          {/* A new order's soft glow: it waits for the store */}
          {isNew && <span aria-hidden="true" className="pointer-events-none absolute -inset-px rounded-2xl ring-2 ring-new/40 motion-safe:animate-pulse" />}

          <div className="flex items-start gap-3">
            <span className={cn('grid size-11 shrink-0 place-items-center rounded-xl bg-linear-to-br text-white shadow-lg transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110 motion-reduce:transition-none', CHIPS[tone])}>
              <Icon className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <Link
                to={`/vendor/orders/${order.id}`}
                className="font-heading text-lg font-bold text-heading transition-colors after:absolute after:inset-0 after:rounded-2xl group-hover:text-brand-text"
              >
                Order #{order.id}
              </Link>
              <p className="mt-0.5 text-xs" title={formatDateTime(order.placedAt)}>
                {isNew ? (
                  <span className={cn('inline-flex items-center gap-1 font-bold', waitedMinutes >= LONG_WAIT_MINUTES ? 'text-warn-text' : 'text-heading')}>
                    <Clock className="size-3.5" aria-hidden="true" />
                    {waitedMinutes < 1 ? 'Just came in' : `Waiting ${waitedMinutes} min`}
                  </span>
                ) : (
                  placedText(order.placedAt, now)
                )}
              </p>
            </div>
            <StatusBadge tone={tone} pulse={isNew}>
              {VENDOR_STATUS_LABELS[order.status]}
            </StatusBadge>
          </div>

          <div className="mt-4 flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="text-xs">Total</p>
              <p className="font-heading text-2xl font-bold text-heading tabular-nums">{formatPrice(order.totalAmount)}</p>
            </div>
            <div className="flex flex-wrap justify-end gap-2">
              <Chip icon={ShoppingBasket}>
                {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}
              </Chip>
              {order.paymentMethod === 'COD' ? (
                <Chip icon={Banknote}>Cash on delivery</Chip>
              ) : (
                <Chip icon={CreditCard}>{order.paymentStatus === 'PAID' ? 'Paid online' : 'Online payment'}</Chip>
              )}
            </div>
          </div>

          <FlowBar status={order.status} />

          {/* The next step, above the card's link (z-10); without one, what happens now, and the arrow to the order */}
          <div className="mt-auto pt-4">
            <div className="flex items-center gap-3 border-t border-line pt-4">
              {noStep ? (
                <p className="min-w-0 flex-1 text-sm">{noStep}</p>
              ) : (
                <div className="relative z-10 flex-1">
                  <StepButton order={order} size="sm" className="w-full" />
                </div>
              )}
              <ChevronRight className="size-5 shrink-0 text-muted transition duration-300 group-hover:translate-x-1 group-hover:text-brand-text motion-reduce:transition-none" aria-hidden="true" />
            </div>
          </div>
        </article>
      </Appear>
    </li>
  )
}

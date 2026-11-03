import { AlertTriangle, Banknote, Bike, Camera, ChevronRight, CircleCheck, CircleX, Clock, CreditCard, MapPin, MessageSquareQuote, Store, Timer, Undo2, type LucideIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import type { Delivery } from '../../../api/types'
import { Appear } from '../../../components/ui/Appear'
import { StarRating } from '../../../components/ui/StarRating'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { cn } from '../../../utils/cn'
import { formatPrice, formatTime } from '../../../utils/format'
import { ISSUE_LABELS, TASK_STATUS_LABELS, TASK_STATUS_TONES } from '../deliveryTexts'
import { roadTime } from './historyDays'

/** The look of a finished delivery: its icon chip and its dot on the timeline (delivered, back at the store, stopped). */
function lookOf(delivery: Delivery): { icon: LucideIcon; chip: string; dot: string } {
  if (delivery.status === 'DELIVERED') return { icon: CircleCheck, chip: 'from-brand to-brand-dark shadow-brand/30', dot: 'bg-brand' }
  if (delivery.status === 'RETURNED') return { icon: Undo2, chip: 'from-ink-soft to-ink shadow-ink/20', dot: 'bg-muted' }
  return { icon: CircleX, chip: 'from-hot to-hot-text shadow-hot/30', dot: 'bg-hot' }
}

/** A small grey label with an icon, e.g. "Paid online" or "12 min on the road". */
function Chip({ icon: Icon, className, children }: { icon: LucideIcon; className?: string; children: ReactNode }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full bg-surface px-2.5 py-1 text-xs font-bold text-heading', className)}>
      <Icon className="size-3.5 shrink-0" aria-hidden="true" />
      {children}
    </span>
  )
}

/** How the order was paid, from the partner's side: cash they collected, or nothing to collect. */
function paymentChip(delivery: Delivery) {
  if (delivery.paymentMethod === 'ONLINE') return <Chip icon={CreditCard}>Paid online</Chip>
  const cash = formatPrice(delivery.amountToCollect)
  return <Chip icon={Banknote}>{delivery.status === 'DELIVERED' ? `Cash ${cash} collected` : `Cash order (${cash}), not collected`}</Chip>
}

/**
 * The road from the store to the customer. A delivered order's scooter rides it (after `delay` ms) and stays at the
 * customer, with the road green behind it. To ride again, the card gives the road a new React key: React builds it anew
 * and the animation starts over. An order that never got there keeps a dashed road with the scooter at the store.
 * With "reduce motion" the road shows its end at once.
 */
function Road({ delivered, delay }: { delivered: boolean; delay: number }) {
  const timing = { animationDelay: `${delay}ms` }
  return (
    <span aria-hidden="true" className="relative h-5 min-w-8 flex-1 sm:min-w-12">
      <span className="absolute inset-x-0 top-1/2 border-t-2 border-dashed border-line" />
      {delivered && <span className="absolute top-1/2 left-0 h-0.5 w-full rounded-full bg-brand motion-safe:animate-road" style={timing} />}
      <Bike
        className={cn('absolute top-0 size-5 rounded bg-surface text-sale-text', delivered ? 'left-[calc(100%-1.25rem)] motion-safe:animate-ride' : 'left-0')}
        style={delivered ? timing : undefined}
      />
    </span>
  )
}

interface HistoryCardProps {
  delivery: Delivery
  /** its place on the page (0, 1, 2 …): the cards come in one after the other */
  order: number
}

/**
 * One finished delivery on the history's timeline: a dot on the line, and a card with the order, its status and time,
 * the payout, the road from the store to the customer, and chips (payment, time on the road, proof photo, rating).
 * The whole card opens the delivery. When the card appears, a delivered order's scooter rides the road (Road); on hover
 * or keyboard focus it rides again, the card lifts, its icon tilts and the arrow slides (all motion-safe).
 */
export function HistoryCard({ delivery, order }: HistoryCardProps) {
  const look = lookOf(delivery)
  const Icon = look.icon
  const onTheRoad = roadTime(delivery)
  // How often the scooter rode again (pointer or keyboard focus on the card); 0 = the first ride, when the card appears
  const [rides, setRides] = useState(0)
  const rideAgain = () => setRides((count) => count + 1)

  return (
    // group: hovering anywhere on the row moves the dot and the card together
    <li className="group relative pl-11 sm:pl-14" onMouseEnter={rideAgain} onFocus={rideAgain}>
      {/* Long lists: the delay stops growing after the first cards, so the last ones do not wait */}
      <Appear order={Math.min(order, 8)}>
        {/* The dot on the timeline (the line itself is drawn by the day's list) */}
        <span
          aria-hidden="true"
          className={cn(
            'absolute top-7 left-3 size-4 rounded-full ring-4 ring-page transition-transform duration-300 group-hover:scale-125 sm:left-4',
            look.dot,
          )}
        />
        <Link
          to={`/delivery/deliveries/${delivery.id}`}
          className="block rounded-2xl border border-line bg-card p-4 transition duration-300 group-hover:-translate-y-0.5 group-hover:border-brand-soft group-hover:shadow-xl group-hover:shadow-heading/10 motion-reduce:transition-none sm:p-5"
        >
          <div className="flex items-start gap-3 sm:gap-4">
            <span
              className={cn(
                'grid size-11 shrink-0 place-items-center rounded-xl bg-linear-to-br text-white shadow-lg transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110 motion-reduce:transition-none',
                look.chip,
              )}
            >
              <Icon className="size-5" aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                <span className="font-heading text-base font-bold text-heading sm:text-lg">Order #{delivery.orderId}</span>
                <StatusBadge tone={TASK_STATUS_TONES[delivery.status]}>{TASK_STATUS_LABELS[delivery.status]}</StatusBadge>
              </div>
              <p className="mt-1 flex items-center gap-1.5 text-xs">
                <Clock className="size-3.5" aria-hidden="true" />
                {delivery.deliveredAt ? `Delivered at ${formatTime(delivery.deliveredAt)}` : `Started at ${formatTime(delivery.createdAt)}`}
              </p>
            </div>
            <div className="shrink-0 text-right">
              <p className="font-heading text-xl font-bold text-brand-text tabular-nums">{delivery.partnerPayout !== null ? formatPrice(delivery.partnerPayout) : '–'}</p>
              <p className="text-xs">{delivery.partnerPayout !== null ? 'earned' : 'no payout'}</p>
            </div>
            <ChevronRight
              className="mt-2.5 hidden size-5 shrink-0 text-muted transition duration-300 group-hover:translate-x-1 group-hover:text-brand-text motion-reduce:transition-none sm:block"
              aria-hidden="true"
            />
          </div>

          {/* The store, the road, the customer. Phones: long names take a second line instead of being cut off */}
          <div className="mt-4 flex items-center gap-2 rounded-xl bg-surface px-2.5 py-2.5 text-sm sm:gap-3 sm:px-3">
            <span className="flex min-w-0 items-center gap-1.5">
              <Store className="size-4 shrink-0 text-new" aria-hidden="true" />
              <span className="leading-tight font-bold break-words text-heading sm:truncate" title={delivery.storeName}>
                {delivery.storeName}
              </span>
            </span>
            <span className="sr-only">to</span>
            <Road key={rides} delivered={delivery.status === 'DELIVERED'} delay={rides === 0 ? Math.min(order, 8) * 70 + 400 : 0} />
            <span className="flex min-w-0 items-center gap-1.5">
              <MapPin className="size-4 shrink-0 text-brand-text" aria-hidden="true" />
              <span className="leading-tight font-bold break-words text-heading sm:truncate" title={delivery.customerName}>
                {delivery.customerName}
              </span>
            </span>
          </div>

          <div className="mt-3 flex flex-wrap items-center gap-2">
            {paymentChip(delivery)}
            {onTheRoad && <Chip icon={Timer}>{onTheRoad} on the road</Chip>}
            {delivery.proofPhotoUrl && <Chip icon={Camera}>Proof photo</Chip>}
            {delivery.issue && (
              <Chip icon={AlertTriangle} className="bg-hot/10 text-hot-text">
                {ISSUE_LABELS[delivery.issue]}
              </Chip>
            )}
            {delivery.customerRating !== null && (
              <span className="inline-flex items-center rounded-full bg-accent/15 px-2.5 py-1">
                <StarRating value={delivery.customerRating} />
              </span>
            )}
          </div>
          {delivery.customerComment && (
            <p className="mt-3 flex items-start gap-2 text-sm italic">
              <MessageSquareQuote className="mt-0.5 size-4 shrink-0 text-accent" aria-hidden="true" />
              <span className="line-clamp-2">“{delivery.customerComment}”</span>
            </p>
          )}
        </Link>
      </Appear>
    </li>
  )
}

import { Banknote, Bike, CalendarDays, CircleCheck, CreditCard, IndianRupee, MapPin, Navigation, Phone, ShoppingBag, ShoppingBasket, Store, Wallet, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { useLocation, useParams } from 'react-router'
import { isApiError } from '../../api/client'
import type { Order } from '../../api/types'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { Appear } from '../../components/ui/Appear'
import { Badge } from '../../components/ui/Badge'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { TONES, type Tone } from '../../components/ui/tones'
import { useFeatureFlag } from '../../hooks/useFeatureFlag'
import { addressLines } from '../../utils/address'
import { cn } from '../../utils/cn'
import { formatIndiaDate, formatPrice, formatTime, phoneHref } from '../../utils/format'
import { RequireCustomer } from '../auth/RequireCustomer'
import type { PlacedOrderState } from '../checkout/CheckoutPage'
import { CancelOrderButton } from './CancelOrderButton'
import { DeliveryRatingPanel } from './DeliveryRatingPanel'
import { InvoiceButton } from './InvoiceButton'
import { OnlinePaymentPanel } from './OnlinePaymentPanel'
import { OrderAmounts } from './OrderAmounts'
import { OrderItemsList } from './OrderItemsList'
import { OrderProgress } from './OrderProgress'
import {
  hasInvoice,
  hasLiveMap,
  isStopped,
  ORDER_PAYMENT_LABELS,
  ORDER_PAYMENT_TONES,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TONES,
  PAYMENT_METHOD_LABELS,
  paymentSummary,
  statusMessage,
} from './orderStatus'
import { OrderTimeline } from './OrderTimeline'
import { useOrder } from './useOrders'

/*
 * The look of this page. It is colourful, but every colour means a part of the order:
 *   green = the order and its money, blue = when and the delivery, orange = the products and the store, violet = payment.
 * (TONES in components/ui/tones.ts, shared with the other customer pages; the progress steps have their own colours,
 * see OrderProgress.tsx.)
 *
 * The movement (all written as motion-safe:animate-..., so "reduce motion" turns it off; the animations are in
 * styles/index.css):
 * - the blocks rise in one after the other when the page opens (<Appear order={0|1|2…}>, 70 ms apart);
 * - the top card has a floating bag icon, four soft coloured circles that drift behind it (animate-blob) and four fact
 *   tiles that rise in one by one;
 * - while an order is on its way, its status has a small dot that pulses (animate-ping);
 * - "Thank you for your order!" has floating confetti dots, and its tick jumps in (animate-pop), like a paid order's.
 */


/** One order: confirmation right after placing it, then tracking. Only for the customer who placed it. */
export function OrderPage() {
  const { orderId } = useParams()
  const id = Number(orderId)
  return <RequireCustomer>{Number.isInteger(id) && id > 0 ? <OrderContent orderId={id} /> : <OrderNotFound />}</RequireCustomer>
}

function OrderNotFound() {
  return (
    <>
      <PageTitle title="Order not found" />
      <BreadcrumbBar crumbs={[{ label: 'My orders', to: '/orders' }, { label: 'Not found' }]} />
      <EmptyState
        icon="📦"
        title="We could not find this order"
        message="It may belong to another account. Your own orders are listed under My orders."
        action={<ButtonLink to="/orders">My orders</ButtonLink>}
      />
    </>
  )
}

function OrderContent({ orderId }: { orderId: number }) {
  const { data: order, isPending, isError, error, refetch } = useOrder(orderId)
  const justPlaced = (useLocation().state as PlacedOrderState | null)?.placed === true

  if (isError && isApiError(error, 404)) return <OrderNotFound />

  let content
  if (isPending) {
    // Grey blocks in the shape of the page while it loads
    content = (
      <div className="space-y-6">
        <Skeleton className="h-48 rounded-3xl" />
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
          <Skeleton className="h-64 rounded-3xl" />
          <Skeleton className="h-64 rounded-3xl" />
        </div>
      </div>
    )
  } else if (isError) {
    content = <ErrorState title="Could not load this order" error={error} onRetry={() => void refetch()} />
  } else {
    content = <OrderDetails order={order} justPlaced={justPlaced} />
  }

  return (
    <>
      <PageTitle title={`Order #${orderId}`} />
      <BreadcrumbBar crumbs={[{ label: 'My orders', to: '/orders' }, { label: `Order #${orderId}` }]} />
      <div className="page-container mt-6">{content}</div>
    </>
  )
}

/** The confetti of the "thank you" banner: where each dot is, its colour and size, and when it starts floating */
const CONFETTI = [
  { place: 'top-4 right-[12%]', look: 'size-3 bg-accent', delay: '0s' },
  { place: 'top-10 right-[30%]', look: 'size-2 bg-hot', delay: '-2s' },
  { place: 'bottom-5 right-[20%]', look: 'size-2.5 bg-sale', delay: '-4s' },
  { place: 'bottom-8 right-[40%]', look: 'size-2 bg-white/80', delay: '-1s' },
  { place: 'top-6 right-[48%]', look: 'size-1.5 bg-new', delay: '-3s' },
]

/**
 * Right after placing the order: a green-to-blue "thank you" banner with confetti dots that float. The tick jumps in
 * (animate-pop), and a light ring pings around it three times.
 */
function ThankYou({ order }: { order: Order }) {
  return (
    <div role="status" className="relative mb-6 flex items-center gap-5 overflow-hidden rounded-3xl bg-linear-to-br from-brand via-brand-dark to-deep-blue p-6 text-white shadow-lg shadow-brand/25 sm:p-8">
      {/* Two big see-through circles and the confetti: decoration only */}
      <span aria-hidden="true" className="absolute -top-16 -right-10 size-48 rounded-full bg-white/10" />
      <span aria-hidden="true" className="absolute right-28 -bottom-24 size-40 rounded-full bg-white/5" />
      {CONFETTI.map((dot) => (
        <span key={dot.place} aria-hidden="true" className={cn('absolute rounded-full motion-safe:animate-float', dot.place, dot.look)} style={{ animationDelay: dot.delay }} />
      ))}

      <span aria-hidden="true" className="relative grid size-14 shrink-0 place-items-center">
        <span className="absolute inset-0 rounded-full bg-white/40 motion-safe:animate-[ping_1.5s_cubic-bezier(0,0,0.2,1)_3]" />
        <span className="relative grid size-14 place-items-center rounded-full bg-white text-brand shadow-md motion-safe:animate-pop">
          <CircleCheck className="size-8" />
        </span>
      </span>
      <div className="relative min-w-0">
        <h2 className="text-2xl text-white sm:text-3xl">Thank you for your order!</h2>
        <p className="mt-1 text-white/90">
          {order.paymentMethod === 'COD'
            ? `Order #${order.id} is placed. Please keep ${formatPrice(order.amounts.totalAmount)} ready in cash for the delivery.`
            : `Order #${order.id} is placed. Please pay below to send it to ${order.storeName}.`}
        </p>
      </div>
    </div>
  )
}

/** A small dot that pulses in the colour of the text around it: "this is happening now". Decoration only. */
function LiveDot() {
  return (
    <span aria-hidden="true" className="relative flex size-2">
      <span className="absolute inline-flex size-full rounded-full bg-current opacity-75 motion-safe:animate-ping" />
      <span className="relative inline-flex size-2 rounded-full bg-current" />
    </span>
  )
}

/** A round icon in a light circle of the card's colour, at the start of a card's title */
function TitleIcon({ icon: Icon, tone }: { icon: LucideIcon; tone: Tone }) {
  return (
    <span aria-hidden="true" className={cn('grid size-9 shrink-0 place-items-center rounded-xl', TONES[tone].soft)}>
      <Icon className="size-5" />
    </span>
  )
}

/**
 * A box of the side column (payment, delivery, the store) in its own colour: a coloured bar along the top, a light
 * wash of the colour in the top-left corner and the title's icon. It lifts a little under the mouse.
 */
function InfoCard({ title, icon, tone, children }: { title: string; icon: LucideIcon; tone: Tone; children: ReactNode }) {
  return (
    <section
      className={cn(
        'relative overflow-hidden rounded-3xl border border-line bg-card bg-linear-to-br via-card to-card p-6 text-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-lg',
        TONES[tone].tint,
      )}
    >
      <span aria-hidden="true" className={cn('absolute inset-x-0 top-0 h-1', TONES[tone].bar)} />
      <h2 className="mb-4 flex items-center gap-3 text-lg">
        <TitleIcon icon={icon} tone={tone} />
        {title}
      </h2>
      {children}
    </section>
  )
}

/** A phone link as a small pill in the card's colour (it fills with the colour under the mouse) */
function callLink(tone: Tone) {
  return cn('mt-3 inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 font-bold transition-colors', TONES[tone].soft, TONES[tone].hover)
}

interface SummaryFactProps {
  label: string
  icon: LucideIcon
  /** the tile's colour */
  tone: Tone
  /** 0, 1, 2, 3: the tiles rise in one after the other */
  order: number
  /** the total: a solid green tile with white text */
  highlight?: boolean
  children: ReactNode
}

/** One fact of the summary card: a tile in its own colour with a round icon, a small title and the value under it. */
function SummaryFact({ label, icon: Icon, tone, order, highlight = false, children }: SummaryFactProps) {
  return (
    <div
      className={cn(
        'min-w-0 rounded-2xl p-4 transition duration-200 hover:-translate-y-0.5 hover:shadow-md motion-safe:animate-rise',
        highlight
          ? 'bg-linear-to-br from-brand to-brand-dark text-white shadow-md shadow-brand/25'
          : cn('bg-linear-to-br via-card/80 to-card/80 ring-1 backdrop-blur-sm', TONES[tone].tint, TONES[tone].ring),
      )}
      style={{ animationDelay: `${200 + order * 90}ms` }}
    >
      <dt className={cn('flex items-center gap-2 text-xs font-bold tracking-wide uppercase', highlight && 'text-white/85')}>
        <span aria-hidden="true" className={cn('grid size-7 place-items-center rounded-full shadow-sm', highlight ? 'bg-white/20' : TONES[tone].solid)}>
          <Icon className="size-3.5" />
        </span>
        {label}
      </dt>
      <dd className={cn('mt-2 font-heading font-bold', highlight ? 'text-white' : 'text-heading')}>{children}</dd>
    </div>
  )
}

/**
 * The top of the page: the order number with its status, where it was placed, the actions the order allows now (each
 * one only when the backend's rules allow it), and the facts at a glance in four coloured tiles.
 */
function OrderSummary({ order }: { order: Order }) {
  const units = order.items.reduce((sum, item) => sum + item.quantity, 0)
  const showActions = hasLiveMap(order.status) || hasInvoice(order.status) || order.cancellable
  // Only the look: the status dot pulses while the order is still on its way (not when delivered, cancelled …)
  const onItsWay = !isStopped(order.status) && order.status !== 'DELIVERED'

  return (
    // isolate + overflow-hidden: the drifting circles stay behind the content and inside the card
    <section
      aria-labelledby="order-title"
      className="relative isolate overflow-hidden rounded-3xl border border-line bg-linear-to-br from-sale-light via-card to-violet-light p-5 shadow-sm sm:p-8"
    >
      {/* Four soft coloured circles (green, blue, peach, violet) that drift slowly behind the content. Decoration only;
          a negative delay starts each one at another point of its path. */}
      <span aria-hidden="true" className="absolute -top-24 -right-16 -z-10 size-72 rounded-full bg-glow-1 opacity-70 blur-3xl motion-safe:animate-blob" />
      <span
        aria-hidden="true"
        className="absolute -bottom-28 left-1/3 -z-10 size-64 rounded-full bg-glow-2 opacity-70 blur-3xl motion-safe:animate-blob"
        style={{ animationDelay: '-7s' }}
      />
      <span
        aria-hidden="true"
        className="absolute -top-20 left-1/4 -z-10 size-56 rounded-full bg-glow-3 opacity-60 blur-3xl motion-safe:animate-blob"
        style={{ animationDelay: '-3s' }}
      />
      <span
        aria-hidden="true"
        className="absolute -right-10 -bottom-24 -z-10 size-60 rounded-full bg-glow-4 opacity-70 blur-3xl motion-safe:animate-blob"
        style={{ animationDelay: '-10s' }}
      />

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-center gap-4">
          {/* The order's picture: a shopping bag in a green-to-blue tile that floats up and down */}
          <span aria-hidden="true" className="grid size-14 shrink-0 place-items-center rounded-2xl bg-linear-to-br from-brand to-deep-blue text-white shadow-lg shadow-brand/30 motion-safe:animate-float sm:size-16">
            <ShoppingBag className="size-7 sm:size-8" />
          </span>
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-3">
              <h1 id="order-title" className="text-3xl md:text-4xl">
                Order #{order.id}
              </h1>
              <Badge tone={ORDER_STATUS_TONES[order.status]} className="gap-1.5 px-3 py-1 text-sm shadow-sm">
                {onItsWay && <LiveDot />}
                {ORDER_STATUS_LABELS[order.status]}
              </Badge>
            </div>
            <p className="mt-1.5 text-sm">
              Ordered from <strong className="text-heading">{order.storeName}</strong>
            </p>
          </div>
        </div>
        {showActions && (
          <div className="flex flex-wrap gap-3">
            {hasLiveMap(order.status) && (
              <ButtonLink to={`/orders/${order.id}/track`} className="shadow-md shadow-brand/25">
                <Navigation className="size-4" aria-hidden="true" />
                Track on the map
              </ButtonLink>
            )}
            {hasInvoice(order.status) && <InvoiceButton orderId={order.id} />}
            {order.cancellable && <CancelOrderButton order={order} />}
          </div>
        )}
      </div>

      <dl className="mt-6 grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <SummaryFact label="Placed on" icon={CalendarDays} tone="blue" order={0}>
          <time dateTime={order.placedAt}>
            <span className="block">{formatIndiaDate(order.placedAt)}</span>
            <span className="text-sm font-normal text-body">{formatTime(order.placedAt)}</span>
          </time>
        </SummaryFact>
        <SummaryFact label="Items" icon={ShoppingBasket} tone="orange" order={1}>
          {units} {units === 1 ? 'item' : 'items'}
        </SummaryFact>
        <SummaryFact label="Payment" icon={order.paymentMethod === 'COD' ? Banknote : CreditCard} tone="violet" order={2}>
          <span className="flex flex-wrap items-center gap-2">
            {PAYMENT_METHOD_LABELS[order.paymentMethod]}
            <Badge tone={ORDER_PAYMENT_TONES[order.paymentStatus]}>{ORDER_PAYMENT_LABELS[order.paymentStatus]}</Badge>
          </span>
        </SummaryFact>
        <SummaryFact label="Total" icon={IndianRupee} tone="green" order={3} highlight>
          <span className="text-2xl">{formatPrice(order.amounts.totalAmount)}</span>
        </SummaryFact>
      </dl>
    </section>
  )
}

/**
 * How the order is paid (violet): the method, a status badge and one sentence ("Cash on delivery: pay ₹324 at the
 * door"). A paid order shows the sentence in green with a tick that jumps in.
 */
function PaymentCard({ order }: { order: Order }) {
  const MethodIcon = order.paymentMethod === 'COD' ? Banknote : CreditCard
  const paid = order.paymentStatus === 'PAID'
  return (
    <InfoCard title="Payment" icon={Wallet} tone="violet">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="flex items-center gap-2 font-bold text-heading">
          <MethodIcon className="size-4 text-violet-text" aria-hidden="true" />
          {PAYMENT_METHOD_LABELS[order.paymentMethod]}
        </p>
        <Badge tone={ORDER_PAYMENT_TONES[order.paymentStatus]}>{ORDER_PAYMENT_LABELS[order.paymentStatus]}</Badge>
      </div>
      <div className={cn('mt-3 flex items-center gap-3 rounded-2xl px-4 py-3 text-heading', paid ? 'bg-brand-light' : 'bg-surface')}>
        {paid && <CircleCheck className="size-6 shrink-0 text-brand-text motion-safe:animate-pop" aria-hidden="true" />}
        <p className={cn(paid && 'font-bold')}>{paymentSummary(order)}</p>
      </div>
    </InfoCard>
  )
}

/** Who gets it and where, the customer's note, and the delivery partner once there is one (blue). */
function DeliveryCard({ order }: { order: Order }) {
  const address = order.deliveryAddress
  const partner = order.deliveryPartnerName
  return (
    <InfoCard title="Delivery" icon={MapPin} tone="blue">
      <p className="font-bold text-heading">{address.contactName}</p>
      <p>{address.contactPhone}</p>
      <div className="mt-2">
        {addressLines(address).map((line) => (
          <p key={line}>{line}</p>
        ))}
      </div>
      {order.customerNote && <p className="mt-3 rounded-2xl bg-sale-light px-4 py-3 text-heading">Your note: “{order.customerNote}”</p>}
      {partner && (
        <div className="mt-4 flex items-start gap-3 rounded-2xl bg-sale-light p-3">
          <span className={cn('grid size-10 shrink-0 place-items-center rounded-full shadow-md', TONES.blue.solid)} aria-hidden="true">
            <Bike className="size-5" />
          </span>
          <div className="min-w-0">
            <p className="text-xs">Delivery partner</p>
            <p className="font-bold text-heading">{partner}</p>
            {order.deliveryPartnerPhone && (
              <a href={phoneHref(order.deliveryPartnerPhone)} className={callLink('blue')}>
                <Phone className="size-4" aria-hidden="true" />
                Call {partner.split(' ')[0]}: {order.deliveryPartnerPhone}
              </a>
            )}
          </div>
        </div>
      )}
    </InfoCard>
  )
}

function OrderDetails({ order, justPlaced }: { order: Order; justPlaced: boolean }) {
  const message = statusMessage(order)
  const stopped = isStopped(order.status)
  const reviewsOn = useFeatureFlag('PRODUCT_REVIEWS')
  // Only the look: the title's dot pulses while the order is still on its way
  const onItsWay = !stopped && order.status !== 'DELIVERED'

  return (
    <>
      {justPlaced && (
        <Appear>
          <ThankYou order={order} />
        </Appear>
      )}
      <Appear order={1}>
        <OrderSummary order={order} />
      </Appear>

      <div className="mt-6 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="space-y-6">
          {/* Where the order is now, its steps with their times, and the full history (open by itself for a stopped order) */}
          <Appear order={2}>
            <section
              aria-labelledby="order-status-title"
              className={cn('relative overflow-hidden rounded-3xl p-5 sm:p-6', stopped ? 'border border-hot/40 bg-hot/5' : 'border border-line bg-card')}
            >
              {/* A bar along the top: the colours of the steps, moving slowly while the order goes on; pink when it stopped */}
              <span
                aria-hidden="true"
                className={cn(
                  'absolute inset-x-0 top-0 h-1.5',
                  stopped ? 'bg-hot' : 'bg-linear-to-r from-new via-violet to-brand bg-[length:200%_100%] motion-safe:animate-gradient-drift',
                )}
              />
              <h2 id="order-status-title" className={cn('flex items-center gap-2.5 text-2xl', stopped && 'text-hot-text')}>
                {onItsWay && (
                  <span className="text-brand">
                    <LiveDot />
                  </span>
                )}
                {message.title}
              </h2>
              <p className="mt-1 text-heading">{message.text}</p>
              {!stopped && (
                <div className="mt-6">
                  <OrderProgress status={order.status} timeline={order.timeline} />
                </div>
              )}
              <OrderTimeline timeline={order.timeline} folded defaultOpen={stopped} />
            </section>
          </Appear>

          {order.paymentMethod === 'ONLINE' && order.status === 'PENDING' && (
            <Appear order={3}>
              <OnlinePaymentPanel order={order} />
            </Appear>
          )}

          {/* Products of a delivered order can be reviewed while the super admin's PRODUCT_REVIEWS flag is on */}
          <Appear order={4}>
            <OrderItemsList items={order.items} reviewOrderId={order.status === 'DELIVERED' && reviewsOn ? order.id : undefined} />
          </Appear>
          {order.status === 'DELIVERED' && (
            <Appear order={5}>
              <DeliveryRatingPanel order={order} />
            </Appear>
          )}
        </div>

        <aside className="space-y-4">
          <Appear order={3}>
            <OrderAmounts order={order} paymentLine={null} />
          </Appear>
          <Appear order={4}>
            <PaymentCard order={order} />
          </Appear>
          <Appear order={5}>
            <DeliveryCard order={order} />
          </Appear>
          <Appear order={6}>
            <InfoCard title="Sold by" icon={Store} tone="orange">
              <p className="font-bold text-heading">{order.storeName}</p>
              <p>{order.storeAddress}</p>
              <a href={phoneHref(order.storePhone)} className={callLink('orange')}>
                <Phone className="size-4" aria-hidden="true" />
                Call the store: {order.storePhone}
              </a>
            </InfoCard>
          </Appear>
        </aside>
      </div>
    </>
  )
}

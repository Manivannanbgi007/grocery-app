import { useQueryClient } from '@tanstack/react-query'
import { Bike, Clock, MapPinOff, Phone, Route, TriangleAlert, WifiOff } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'
import { useParams } from 'react-router'
import { isApiError } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { Order, OrderStatus, TrackingSnapshot } from '../../api/types'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { LazyTrackingMap } from '../../components/map/LazyTrackingMap'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { LoadingState } from '../../components/ui/LoadingState'
import { Skeleton } from '../../components/ui/Skeleton'
import { TRACKING_CONFIG } from '../../config/tracking'
import { useNow } from '../../hooks/useNow'
import { distanceMeters, formatAge, formatDistance } from '../../utils/geo'
import { phoneHref } from '../../utils/format'
import { useAuth } from '../auth/authContext'
import { RequireCustomer } from '../auth/RequireCustomer'
import { VEHICLE_NAMES } from '../delivery/deliveryTexts'
import { OrderProgress } from '../orders/OrderProgress'
import { useOrder } from '../orders/useOrders'
import { ConnectionStatus } from './ConnectionStatus'
import { arrivalText, orderStepOf, partnerFirstName, trackingHeadline } from './trackingTexts'
import { useTrackingStream, type StreamState } from './useTrackingStream'

/** Order statuses in which a delivery exists, so there is something to track. */
const TRACKABLE: OrderStatus[] = ['READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED']

/** /orders/:orderId/track - follow the delivery of one of the customer's orders on the map, live. */
export function OrderTrackingPage() {
  const id = Number(useParams().orderId)
  return (
    <RequireCustomer>
      <PageTitle title={`Track order #${id}`} />
      <BreadcrumbBar crumbs={[{ label: 'My orders', to: '/orders' }, { label: `Order #${id}`, to: `/orders/${id}` }, { label: 'Live tracking' }]} />
      <div className="page-container mt-10">{Number.isInteger(id) && id > 0 ? <TrackingContent key={id} orderId={id} /> : <OrderNotFound />}</div>
    </RequireCustomer>
  )
}

function OrderNotFound() {
  return (
    <EmptyState icon="📦" title="We could not find this order" message="It may belong to another account. Your own orders are listed under My orders." action={<ButtonLink to="/orders">My orders</ButtonLink>} />
  )
}

function TrackingContent({ orderId }: { orderId: number }) {
  const { data: order, isPending, isError, error, refetch } = useOrder(orderId)
  const trackable = order !== undefined && TRACKABLE.includes(order.status)
  const { snapshot, state } = useTrackingStream(orderId, trackable)
  useOrderFollowsTheDelivery(orderId, snapshot)

  if (isPending) return <LoadingState className="min-h-[28rem]" />
  if (isError) return isApiError(error, 404) ? <OrderNotFound /> : <ErrorState title="Could not load this order" error={error} onRetry={() => void refetch()} />

  return (
    <>
      <div className="mb-6 flex flex-wrap items-center gap-x-4 gap-y-2">
        <h1 className="text-3xl md:text-4xl">Order #{order.id}</h1>
        {trackable && <ConnectionStatus state={state} />}
      </div>
      {trackable ? <LiveTracking order={order} snapshot={snapshot} state={state} /> : <NotOnTheWayYet order={order} />}
    </>
  )
}

/** When the delivery's status changes, the order (its progress bar) is loaded again: order-service moves along with it. */
function useOrderFollowsTheDelivery(orderId: number, snapshot: TrackingSnapshot | null) {
  const queryClient = useQueryClient()
  const { customer } = useAuth()
  const status = snapshot?.status
  useEffect(() => {
    if (status) void queryClient.invalidateQueries({ queryKey: queryKeys.order(customer?.id ?? 0, orderId) })
  }, [status, orderId, customer?.id, queryClient])
}

/** Before the store hands the order over there is no delivery, so nothing to follow yet. */
function NotOnTheWayYet({ order }: { order: Order }) {
  const stopped = order.status === 'CANCELLED' || order.status === 'REJECTED'
  return (
    <section className="max-w-3xl rounded-2xl border border-line bg-card p-6">
      <h2 className="text-2xl">{stopped ? 'This order will not be delivered' : 'Live tracking starts soon'}</h2>
      <p className="mt-1 mb-5 text-heading">
        {stopped
          ? 'It was cancelled, so there is no delivery to follow.'
          : `You can follow your order on the map as soon as ${order.storeName} hands it to a delivery partner. This page updates by itself.`}
      </p>
      {!stopped && <OrderProgress status={order.status} />}
      <ButtonLink to={`/orders/${order.id}`} variant="outline" className="mt-6">
        Back to the order
      </ButtonLink>
    </section>
  )
}

function LiveTracking({ order, snapshot, state }: { order: Order; snapshot: TrackingSnapshot | null; state: StreamState }) {
  const now = useNow()

  if (!snapshot) {
    if (state === 'failed') return <ErrorState title="Live tracking is not available" error={new Error('Please log in again and open the order.')} />
    return (
      <div className="grid gap-6 lg:grid-cols-[1fr_24rem]">
        <Skeleton className="h-[28rem]" />
        <p className="text-heading">{state === 'waiting' ? 'Waiting for the store to hand over your order…' : 'Connecting to live tracking…'}</p>
      </div>
    )
  }

  const headline = trackingHeadline(snapshot)
  const location = snapshot.live ? snapshot.location : null
  const stale = location !== null && now - new Date(location.receivedAt).getTime() > TRACKING_CONFIG.staleLocationMs
  const nextStop = snapshot.status === 'ACCEPTED' ? 'PICKUP' : 'DESTINATION'
  const target = nextStop === 'PICKUP' ? snapshot.pickup.location : snapshot.destination.location
  const arrival = snapshot.live ? arrivalText(snapshot.route, snapshot.pickup.name, now) : null
  const name = partnerFirstName(snapshot)

  return (
    <div className="grid items-start gap-6 lg:grid-cols-[1fr_24rem]">
      <div>
        <LazyTrackingMap
          className="h-[22rem] md:h-[30rem]"
          ariaLabel="Delivery map: the store, your address and the delivery partner"
          pickup={snapshot.pickup.location}
          pickupLabel={`${snapshot.pickup.name} (pickup)`}
          destination={snapshot.destination.location}
          destinationLabel="Your address"
          partner={location}
          partnerLabel={`${snapshot.partner?.name ?? 'Delivery partner'}, ${location ? formatAge(location.receivedAt, now) : ''}`}
          partnerStale={stale}
          nextStop={nextStop}
          route={snapshot.live ? snapshot.route : null}
        />
        {(!snapshot.pickup.location || !snapshot.destination.location) && (
          <p className="mt-2 flex items-center gap-2 text-sm">
            <MapPinOff className="size-4 shrink-0" aria-hidden="true" />
            {!snapshot.destination.location ? 'Your address has no map position, so it is not on the map.' : 'The store has no map position, so it is not on the map.'}
          </p>
        )}
      </div>

      <aside className="space-y-4">
        <section aria-labelledby="tracking-headline" className="rounded-2xl bg-brand-light p-5">
          <h2 id="tracking-headline" className="text-2xl" aria-live="polite">
            {headline.title}
          </h2>
          <p className="mt-1 text-heading">{headline.text}</p>
          {arrival && (
            <p className="mt-3 flex items-center gap-2 font-bold text-heading">
              <Clock className="size-5 text-brand-text" aria-hidden="true" />
              {arrival}
            </p>
          )}
          {!arrival && snapshot.live && location && target && (
            <p className="mt-3 flex items-center gap-2 text-sm text-heading">
              <Route className="size-4 shrink-0 text-brand-text" aria-hidden="true" />
              {name} is about {formatDistance(distanceMeters(location, target))} away {nextStop === 'PICKUP' ? 'from the store' : 'from you'} (straight line)
            </p>
          )}
        </section>

        {state === 'reconnecting' && (
          <Notice icon={<WifiOff className="size-5 shrink-0" aria-hidden="true" />}>The live connection was lost. Trying again… The map shows the last known state.</Notice>
        )}
        {snapshot.live && !location && <Notice icon={<Clock className="size-5 shrink-0" aria-hidden="true" />}>Waiting for {name}’s location…</Notice>}
        {stale && location && (
          <Notice icon={<TriangleAlert className="size-5 shrink-0" aria-hidden="true" />}>
            Last position {formatAge(location.receivedAt, now)}. {name}’s phone may have lost its signal; the map moves on when it is back.
          </Notice>
        )}

        {snapshot.partner && (
          <section className="rounded-2xl border border-line bg-card p-5 text-sm">
            <h2 className="mb-2 flex items-center gap-2 text-lg">
              <Bike className="size-5 text-brand" aria-hidden="true" />
              Your delivery partner
            </h2>
            <p className="font-bold text-heading">
              {snapshot.partner.name}
              {snapshot.partner.vehicleType && <span className="font-normal"> · {VEHICLE_NAMES[snapshot.partner.vehicleType]}</span>}
            </p>
            {location && <p className="mt-1">Location updated {formatAge(location.receivedAt, now)}</p>}
            {!['DELIVERED', 'CANCELLED', 'RETURNED'].includes(snapshot.status) && (
              <a href={phoneHref(snapshot.partner.phone)} className="mt-3 inline-flex items-center gap-1.5 font-bold text-brand-text hover:underline">
                <Phone className="size-4" aria-hidden="true" />
                Call {name}: {snapshot.partner.phone}
              </a>
            )}
          </section>
        )}

        <section className="rounded-2xl border border-line bg-card p-5">
          <h2 className="mb-3 text-lg">Order progress</h2>
          <OrderProgress status={orderStepOf(order.status, snapshot.status)} vertical />
          <ButtonLink to={`/orders/${order.id}`} variant="outline" className="mt-5 w-full">
            Order details
          </ButtonLink>
        </section>
      </aside>
    </div>
  )
}

function Notice({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <p role="status" className="flex gap-3 rounded-2xl border border-accent/60 bg-accent/10 p-4 text-sm text-heading">
      {icon}
      <span>{children}</span>
    </p>
  )
}

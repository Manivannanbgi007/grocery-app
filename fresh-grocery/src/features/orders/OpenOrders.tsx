import { Bike, Navigation, Store } from 'lucide-react'
import { useId } from 'react'
import type { OrderSummary } from '../../api/types'
import { Badge } from '../../components/ui/Badge'
import { ButtonLink } from '../../components/ui/Button'
import { formatDateTime } from '../../utils/format'
import { hasLiveMap, ORDER_STATUS_LABELS, ORDER_STATUS_TONES } from './orderStatus'

interface OpenOrdersProps {
  orders: OrderSummary[]
  title: string
  /** a line under the title */
  description?: string
}

/**
 * The customer's open orders, each with the way to its tracking page: "Track on the map" once the store hands it over,
 * "Follow the order" while it is still at the store (the tracking page then shows the progress and starts the map later).
 * On My orders ("On its way") and on /orders/track when there is more than one.
 */
export function OpenOrders({ orders, title, description }: OpenOrdersProps) {
  const titleId = useId()
  return (
    // Blue, the colour of delivery: a light blue-to-violet card; its scooter floats up and down and the orders rise in one
    // after the other (motion-safe: not when the system asks for less motion)
    <section aria-labelledby={titleId} className="mb-8 rounded-3xl border border-sale/30 bg-linear-to-br from-sale-light via-card to-violet-light p-5 shadow-sm sm:p-6">
      <h2 id={titleId} className="flex items-center gap-3 text-xl">
        <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-xl bg-linear-to-br from-sale to-violet text-white shadow-md shadow-sale/30 motion-safe:animate-float">
          <Bike className="size-5" />
        </span>
        {title}
      </h2>
      {description && <p className="mt-1 text-sm text-heading">{description}</p>}
      <ul className="mt-4 space-y-3">
        {orders.map((order, index) => {
          const onTheMap = hasLiveMap(order.status)
          const Icon = onTheMap ? Bike : Store
          return (
            <li
              key={order.id}
              className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-2xl bg-card p-4 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:shadow-md motion-safe:animate-rise"
              style={{ animationDelay: `${index * 70}ms` }}
            >
              <span className="grid size-11 shrink-0 place-items-center rounded-full bg-sale-light text-sale-text" aria-hidden="true">
                <Icon className="size-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-heading font-bold text-heading">
                  Order #{order.id}
                  <Badge tone={ORDER_STATUS_TONES[order.status]}>{ORDER_STATUS_LABELS[order.status]}</Badge>
                </p>
                <p className="mt-0.5 text-sm">
                  {order.storeName} · placed {formatDateTime(order.placedAt)}
                </p>
              </div>
              <ButtonLink to={`/orders/${order.id}/track`} variant={onTheMap ? 'primary' : 'outline'} size="sm" className="w-full sm:w-auto">
                {onTheMap && <Navigation className="size-4" aria-hidden="true" />}
                {/* The space stays outside the hidden part: inside it would be dropped from the name */}
                {onTheMap ? 'Track on the map' : 'Follow the order'}{' '}
                <span className="sr-only">(order #{order.id})</span>
              </ButtonLink>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

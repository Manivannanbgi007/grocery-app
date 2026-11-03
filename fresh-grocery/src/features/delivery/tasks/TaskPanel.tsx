import { Banknote, Map, Phone, Store, User, Wallet } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Delivery } from '../../../api/types'
import { KpiTile } from '../../../components/sales/KpiTile'
import { Badge } from '../../../components/ui/Badge'
import { StarRating } from '../../../components/ui/StarRating'
import { StepBar } from '../../../components/ui/StepBar'
import { cn } from '../../../utils/cn'
import { formatPrice, phoneHref } from '../../../utils/format'
import { directionsLink, sharesLocation, TASK_STATUS_LABELS, TASK_STATUS_TONES, TASK_STEPS, taskMessage, taskStep } from '../deliveryTexts'
import { LiveLocationPanel } from '../tracking/LiveLocationPanel'
import { ProofPhoto } from './ProofPhoto'
import { TaskActions } from './TaskActions'

interface StopProps {
  icon: ReactNode
  /** the colours of the round icon */
  iconClassName: string
  label: string
  name: string
  address: string
  phone: string
  callLabel: string
  /** the last stop has no dashed line to a next one */
  last?: boolean
}

/** A stop of the trip: the store (pick up) or the customer (drop off), with a call button. */
function Stop({ icon, iconClassName, label, name, address, phone, callLabel, last = false }: StopProps) {
  return (
    <li className="relative flex gap-4">
      {/* The route: a dashed line from this stop's icon down to the next one */}
      {!last && <span aria-hidden="true" className="absolute top-12 -bottom-4 left-5 border-l-2 border-dashed border-line" />}
      <span className={cn('grid size-10 shrink-0 place-items-center rounded-full', iconClassName)}>{icon}</span>
      <div className="min-w-0 flex-1">
        <p className="text-xs font-bold tracking-wide uppercase">{label}</p>
        <p className="font-heading text-lg font-bold text-heading">{name}</p>
        <p className="text-sm">{address}</p>
        <a
          href={phoneHref(phone)}
          className="mt-2 inline-flex items-center gap-2 rounded-full bg-surface px-3 py-1.5 text-sm font-bold text-brand-text transition-colors hover:bg-brand-light"
        >
          <Phone className="size-4" aria-hidden="true" />
          {callLabel}: {phone}
        </a>
      </div>
    </li>
  )
}

/** What the cash tile says: to collect, collected, or not collected (a delivery that stopped). */
function cashLabel(delivery: Delivery): string {
  if (delivery.amountToCollect === 0) return 'Payment'
  if (delivery.status === 'DELIVERED') return 'Collected in cash'
  if (delivery.status === 'FAILED' || delivery.status === 'RETURNED' || delivery.status === 'CANCELLED') return 'Cash (not collected)'
  return 'Collect in cash'
}

/**
 * Everything the partner needs for one delivery, on one screen: what to do now, how far the delivery is and the next
 * button; the trip from the store to the customer (call, directions); the cash to collect; the proof photo.
 */
export function TaskPanel({ delivery }: { delivery: Delivery }) {
  const message = taskMessage(delivery)
  const stopped = delivery.status === 'FAILED' || delivery.status === 'CANCELLED'
  const step = taskStep(delivery.status)
  const titleId = `task-${delivery.id}-title`

  return (
    <div className="space-y-4">
      <section aria-labelledby={titleId} className={cn('rounded-2xl border p-5 shadow-sm sm:p-6', stopped ? 'border-hot/40 bg-hot/5' : 'border-brand/30 bg-brand-light')}>
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <span className="text-sm font-bold text-heading">Order #{delivery.orderId}</span>
          <Badge tone={TASK_STATUS_TONES[delivery.status]}>{TASK_STATUS_LABELS[delivery.status]}</Badge>
        </div>
        <h2 id={titleId} className="text-2xl">
          {message.title}
        </h2>
        <p className="mt-1 text-heading">{message.text}</p>
        {step !== null && (
          <div className="mt-5 rounded-xl bg-card px-2 py-4 sm:px-4">
            <StepBar label="Delivery progress" steps={TASK_STEPS} current={step} />
          </div>
        )}
        <div className="mt-5 empty:hidden">
          <TaskActions delivery={delivery} />
        </div>
      </section>

      {/* accepted, picked up, on the way: the customer follows the trip on a map */}
      {sharesLocation(delivery.status) && <LiveLocationPanel delivery={delivery} />}

      <section aria-labelledby={`${titleId}-trip`} className="rounded-2xl border border-line bg-card p-5 shadow-sm sm:p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <h2 id={`${titleId}-trip`} className="text-lg">
            The trip
          </h2>
          <a
            href={directionsLink(delivery.storeAddress, delivery.customerAddress)}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-full border border-brand px-4 py-2 font-heading text-sm font-bold text-brand-text transition-colors hover:bg-brand-light"
          >
            <Map className="size-4" aria-hidden="true" />
            Directions (Google Maps)
          </a>
        </div>
        <ol className="space-y-6">
          <Stop
            icon={<Store className="size-5" aria-hidden="true" />}
            iconClassName="bg-brand-light text-brand-text"
            label="Pick up at"
            name={delivery.storeName}
            address={delivery.storeAddress}
            phone={delivery.storePhone}
            callLabel="Call the store"
          />
          <Stop
            icon={<User className="size-5" aria-hidden="true" />}
            iconClassName="bg-new-light text-heading"
            label="Deliver to"
            name={delivery.customerName}
            address={delivery.customerAddress}
            phone={delivery.customerPhone}
            callLabel={`Call ${delivery.customerName.split(' ')[0]}`}
            last
          />
        </ol>
      </section>

      <section aria-label="Money" className="grid grid-cols-2 gap-3">
        <KpiTile label={cashLabel(delivery)} value={delivery.amountToCollect > 0 ? formatPrice(delivery.amountToCollect) : 'Paid online'} icon={Banknote} accent="orange" />
        <KpiTile
          label={delivery.status === 'DELIVERED' ? 'You earned' : 'You earn'}
          value={delivery.partnerPayout !== null ? formatPrice(delivery.partnerPayout) : 'Your zone’s rate'}
          icon={Wallet}
          accent="green"
        />
      </section>

      <ProofPhoto delivery={delivery} />

      {delivery.customerRating !== null && (
        <section aria-label="The customer's rating" className="rounded-2xl border border-line bg-card p-5 shadow-sm">
          <StarRating value={delivery.customerRating} className="mb-2" />
          <p className="text-sm">
            The customer rated this delivery <strong className="text-heading">{delivery.customerRating} of 5</strong>
            {delivery.customerComment && `: “${delivery.customerComment}”`}
          </p>
        </section>
      )}
    </div>
  )
}

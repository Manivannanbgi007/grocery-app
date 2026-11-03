import { ArrowRight, CircleCheck, IdCard, Store, Truck, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { Skeleton } from '../../../components/ui/Skeleton'
import { cn } from '../../../utils/cn'
import { useDeliveryCount, usePartnerCount, useStoreCount } from '../useWorkCounts'

interface AttentionTileProps {
  to: string
  icon: LucideIcon
  label: string
  hint: string
  /** undefined while loading or when the number could not be loaded */
  count: number | undefined
  isPending: boolean
}

/**
 * Work for the platform team, as a tile that opens the list where it is done. Amber with a pulsing dot and "Review now"
 * while work waits, green with "All clear" when there is none (the colour never stands alone: the number and the words
 * say the same). It lifts on hover.
 */
function AttentionTile({ to, icon: Icon, label, hint, count, isPending }: AttentionTileProps) {
  const waiting = count !== undefined && count > 0
  return (
    <Link
      to={to}
      className={cn(
        'group relative flex h-full items-center gap-4 overflow-hidden rounded-2xl border p-4 transition duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-heading/10 sm:p-5',
        waiting ? 'border-warn/40 bg-warn-light' : 'border-line bg-card',
      )}
    >
      <span className="relative shrink-0">
        <span
          className={cn(
            'grid size-12 place-items-center rounded-2xl bg-linear-to-br text-white shadow-lg transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110',
            waiting ? 'from-warn to-warn-text shadow-warn/30' : 'from-brand to-brand-dark shadow-brand/30',
          )}
        >
          <Icon className="size-6" aria-hidden="true" />
        </span>
        {waiting && (
          <span aria-hidden="true" className="absolute -top-1 -right-1 flex size-3.5">
            <span className="absolute inline-flex size-full rounded-full bg-hot opacity-75 motion-safe:animate-ping" />
            <span className="relative inline-flex size-3.5 rounded-full border-2 border-card bg-hot" />
          </span>
        )}
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-heading font-bold text-heading">{label}</p>
        <p className="text-sm">{hint}</p>
        {count !== undefined && (
          <p className={cn('mt-1.5 flex items-center gap-1.5 text-sm font-bold', waiting ? 'text-warn-text' : 'text-brand-text')}>
            {waiting ? (
              <>
                Review now
                <ArrowRight className="size-4 transition-transform group-hover:translate-x-1" aria-hidden="true" />
              </>
            ) : (
              <>
                <CircleCheck className="size-4" aria-hidden="true" />
                All clear
              </>
            )}
          </p>
        )}
      </div>
      {isPending ? (
        <Skeleton className="h-10 w-10" />
      ) : (
        <span className={cn('font-heading text-4xl leading-none font-bold tabular-nums', waiting ? 'text-warn-text' : 'text-heading')}>{count ?? '–'}</span>
      )}
    </Link>
  )
}

/** The work that waits: stores to approve, partners' KYC to review, deliveries nobody took (asked again every minute). */
export function AttentionTiles() {
  const storesWaiting = useStoreCount('PENDING_APPROVAL')
  const kycWaiting = usePartnerCount('PENDING')
  const deliveriesWaiting = useDeliveryCount('WAITING_FOR_PARTNER')

  return (
    <section aria-labelledby="attention-title">
      <h2 id="attention-title" className="mb-3 text-xl">
        What needs your attention
      </h2>
      {/* @4xl: three in a row once the content is 896 px wide (a container query: the menu takes room too); narrower,
          one under the other, so the words do not wrap onto many lines */}
      <div className="grid gap-3 sm:gap-4 @4xl:grid-cols-3">
        <AttentionTile to="/admin/stores" icon={Store} label="Stores to approve" hint="new stores waiting for approval" count={storesWaiting.data} isPending={storesWaiting.isPending} />
        <AttentionTile to="/admin/partners" icon={IdCard} label="KYC to review" hint="new delivery partners" count={kycWaiting.data} isPending={kycWaiting.isPending} />
        <AttentionTile
          to="/admin/deliveries"
          icon={Truck}
          label="Deliveries without a partner"
          hint="nobody took them yet"
          count={deliveriesWaiting.data}
          isPending={deliveriesWaiting.isPending}
        />
      </div>
    </section>
  )
}

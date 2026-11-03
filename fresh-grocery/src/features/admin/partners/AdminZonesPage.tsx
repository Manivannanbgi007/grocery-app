import { Building2, CircleCheck, IndianRupee, Map as MapIcon, MapPin, Pencil, Plus } from 'lucide-react'
import { useState } from 'react'
import type { DeliveryZone } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PageHeader } from '../../../components/panel/PageHeader'
import { KpiTile } from '../../../components/sales/KpiTile'
import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { cn } from '../../../utils/cn'
import { formatPrice } from '../../../utils/format'
import { ZonesIllustration } from '../illustrations/ZonesIllustration'
import { useZones } from './useAdminPartners'
import { ZoneForm } from './ZoneForm'

/** The zones at a glance, worked out from the list on this page (no extra request). */
function ZoneNumbers({ zones }: { zones: DeliveryZone[] }) {
  const inUse = zones.filter((zone) => zone.active).length
  const pincodes = zones.reduce((sum, zone) => sum + zone.pincodes.length, 0)
  const averagePayout = zones.length > 0 ? zones.reduce((sum, zone) => sum + zone.partnerPayout, 0) / zones.length : null
  return (
    <section aria-label="Delivery zones in numbers" className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
      <KpiTile label="Zones" value={String(zones.length)} hint="in every city" icon={MapIcon} accent="green" />
      <KpiTile label="In use" value={String(inUse)} hint="their partners get deliveries" icon={CircleCheck} accent="blue" />
      <KpiTile label="PIN codes" value={String(pincodes)} hint="served by the zones" icon={MapPin} accent="orange" />
      <KpiTile label="Average payout" value={averagePayout === null ? '–' : formatPrice(Math.round(averagePayout * 100) / 100)} hint="a partner earns per delivery" icon={IndianRupee} accent="pink" />
    </section>
  )
}

/** The delivery zones: which PIN codes a group of partners serves, and what a partner earns per delivery there. */
export function AdminZonesPage() {
  const { data: zones, isPending, isError, error, refetch } = useZones()
  /** the zone being changed, 'new' while adding one, or null */
  const [editing, setEditing] = useState<number | 'new' | null>(null)

  let content
  if (isPending) content = <LoadingState className="min-h-64" />
  else if (isError) content = <ErrorState title="Could not load the zones" error={error} onRetry={() => void refetch()} />
  else if (zones.length === 0 && editing !== 'new') {
    content = (
      <EmptyState
        icon={<ZonesIllustration className="h-32 w-auto" />}
        title="No delivery zones yet"
        message="Without a zone, no partner gets the deliveries of a store."
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    content = (
      <>
        <ZoneNumbers zones={zones} />
        <ul aria-label="Delivery zones" className="grid gap-4 lg:grid-cols-2">
          {zones.map((zone) => (
            <li
              key={zone.id}
              className={cn('group relative overflow-hidden rounded-2xl border border-line bg-card p-6 transition duration-300', editing !== zone.id && 'hover:-translate-y-1 hover:shadow-xl hover:shadow-heading/10')}
            >
              {editing === zone.id ? (
                <>
                  <h2 className="mb-4 text-xl">Change {zone.name}</h2>
                  <ZoneForm zone={zone} onDone={() => setEditing(null)} />
                </>
              ) : (
                <>
                  {/* A soft glow in the corner (decoration) */}
                  <span aria-hidden="true" className="absolute -top-12 -right-12 size-32 rounded-full bg-brand/15 blur-2xl transition-transform duration-500 group-hover:scale-125" />
                  <div className="relative mb-3 flex flex-wrap items-center gap-3">
                    <span className="grid size-11 place-items-center rounded-xl bg-linear-to-br from-brand to-brand-dark text-white shadow-lg shadow-brand/30">
                      <MapPin className="size-5" aria-hidden="true" />
                    </span>
                    <h2 className="text-xl">{zone.name}</h2>
                    <StatusBadge tone={zone.active ? 'brand' : 'neutral'}>{zone.active ? 'In use' : 'Not in use'}</StatusBadge>
                  </div>
                  <div className="relative flex flex-wrap gap-x-5 gap-y-1 text-sm">
                    <span className="flex items-center gap-1.5">
                      <Building2 className="size-4 text-brand-text" aria-hidden="true" />
                      {zone.city}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <IndianRupee className="size-4 text-brand-text" aria-hidden="true" />a partner earns <strong className="text-heading">{formatPrice(zone.partnerPayout)}</strong> per delivery
                    </span>
                  </div>
                  <ul aria-label={`PIN codes of ${zone.name}`} className="relative my-4 flex flex-wrap gap-2">
                    {zone.pincodes.map((pincode) => (
                      <li key={pincode} className="flex items-center gap-1 rounded-full bg-brand-light px-3 py-1 font-heading text-sm font-bold text-brand-text">
                        <MapPin className="size-3" aria-hidden="true" />
                        {pincode}
                      </li>
                    ))}
                  </ul>
                  <Button variant="light" size="sm" onClick={() => setEditing(zone.id)} aria-label={`Change zone ${zone.name}`} className="relative">
                    <Pencil className="size-3.5" aria-hidden="true" />
                    Change
                  </Button>
                </>
              )}
            </li>
          ))}
        </ul>
      </>
    )
  }

  return (
    <>
      <PageTitle title="Delivery zones" />
      <PageHeader
        title="Delivery zones"
        icon={MapIcon}
        accent="green"
        description="A store's deliveries go to the online partners of the zone that has the store's PIN code."
        illustration={<ZonesIllustration />}
        actions={
          editing !== 'new' && (
            <Button onClick={() => setEditing('new')}>
              <Plus className="size-4" aria-hidden="true" />
              New zone
            </Button>
          )
        }
      />
      {editing === 'new' && (
        <section aria-labelledby="new-zone-title" className="mb-6 rounded-2xl border border-brand/40 bg-card p-6 shadow-lg shadow-brand/10 motion-safe:animate-rise">
          <h2 id="new-zone-title" className="mb-4 text-xl">
            New zone
          </h2>
          <ZoneForm onDone={() => setEditing(null)} />
        </section>
      )}
      {content}
    </>
  )
}

import { Bike, Package, PackageX, Store, Timer, Users, type LucideIcon } from 'lucide-react'
import type { AdminDashboard } from '../../../api/types'
import { cn } from '../../../utils/cn'
import { formatDateTime } from '../../../utils/format'

/** The ring's radius makes its circumference exactly 100, so the green part's length is simply the percentage. */
const RADIUS = 100 / (2 * Math.PI)

/** "67%" in a ring: the customers who ordered in the last 30 days, of all customers (the words under it say the same). */
function ActiveCustomersRing({ active, total }: { active: number; total: number }) {
  const share = total > 0 ? Math.round((active / total) * 100) : 0
  return (
    <div className="flex flex-col items-center text-center">
      <div className="relative size-36">
        {/* -rotate-90: the ring starts at 12 o'clock */}
        <svg viewBox="0 0 42 42" aria-hidden="true" className="size-full -rotate-90">
          <circle cx="21" cy="21" r={RADIUS} fill="none" strokeWidth="5" className="stroke-brand-light" />
          {share > 0 && (
            <circle cx="21" cy="21" r={RADIUS} fill="none" strokeWidth="5" strokeLinecap="round" strokeDasharray={`${share} ${100 - share}`} className="stroke-brand motion-safe:animate-draw" />
          )}
        </svg>
        <span aria-hidden="true" className="absolute inset-0 grid place-items-center font-heading text-3xl font-bold text-heading">
          {share}%
        </span>
      </div>
      <p className="mt-3 font-heading font-bold text-heading">Active customers</p>
      <p className="text-xs">
        {active} of {total} ordered in the last 30 days
      </p>
    </div>
  )
}

interface FigureProps {
  label: string
  value: string
  hint?: string
  icon: LucideIcon
  /** amber: something to look at, e.g. products that are out of stock */
  warn?: boolean
}

/** One small number with its icon. The label comes right before the value (the tests read it that way). */
function Figure({ label, value, hint, icon: Icon, warn = false }: FigureProps) {
  return (
    <div className="flex items-center gap-3 rounded-2xl bg-surface p-3">
      <span className={cn('grid size-10 shrink-0 place-items-center rounded-xl', warn ? 'bg-warn-light text-warn-text' : 'bg-card text-brand-text')}>
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="text-xs leading-tight">{label}</p>
        <p className="font-heading text-lg font-bold text-heading tabular-nums">{value}</p>
        {hint && <p className="text-xs leading-tight">{hint}</p>}
      </div>
    </div>
  )
}

/** "The platform": everything so far, and the share of active customers as a ring. */
export function PlatformCard({ dashboard }: { dashboard: AdminDashboard }) {
  return (
    // A container: the ring sits beside the figures when the card is wide enough, above them when not
    <section aria-labelledby="platform-title" className="@container h-full rounded-2xl border border-line bg-card p-5 sm:p-6">
      <h2 id="platform-title" className="text-xl">
        The platform
      </h2>
      <p className="text-sm">All stores, partners and customers together</p>
      <div className="mt-5 grid items-center gap-6 @xl:grid-cols-[auto_minmax(0,1fr)]">
        <ActiveCustomersRing active={dashboard.activeCustomers} total={dashboard.totalCustomers} />
        <div className="grid grid-cols-2 gap-3">
          <Figure label="Total orders" value={dashboard.totalOrders.toLocaleString('en-IN')} icon={Package} />
          <Figure label="Customers" value={dashboard.totalCustomers.toLocaleString('en-IN')} icon={Users} />
          <Figure label="Active stores" value={String(dashboard.activeStores)} icon={Store} />
          <Figure label="Active delivery partners" value={String(dashboard.activeDeliveryPartners)} hint="KYC approved" icon={Bike} />
          <Figure label="Out-of-stock products" value={String(dashboard.outOfStockProducts)} icon={PackageX} warn={dashboard.outOfStockProducts > 0} />
          <Figure
            label="Average delivery time"
            value={dashboard.averageDeliveryMinutes === null ? '–' : `${dashboard.averageDeliveryMinutes} min`}
            hint="order to door, 30 days"
            icon={Timer}
          />
        </div>
      </div>
      <p className="mt-5 text-xs">Numbers of {formatDateTime(dashboard.generatedAt)}; they follow the orders a moment later.</p>
    </section>
  )
}

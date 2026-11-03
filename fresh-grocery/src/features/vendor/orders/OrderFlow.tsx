import { BellRing, Bike, ChevronRight, PackageCheck, PackageOpen, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { useCountUp } from '../../../hooks/useCountUp'
import { cn } from '../../../utils/cn'
import { vendorOrdersLink } from './orderTabs'

interface FlowStage {
  /** the queue's tab with these orders */
  tabKey: string
  label: string
  hint: string
  icon: LucideIcon
  /** the icon chip's gradient */
  chip: string
  /** the store's own work: while orders wait here, the stage says so (a pulsing "Waiting") */
  work: boolean
}

/** The way of an order through the store, as the store sees it: from a new order to the delivery partner's pickup. */
const STAGES: FlowStage[] = [
  { tabKey: 'new', label: 'To accept', hint: 'new orders: accept or reject', icon: BellRing, chip: 'from-new to-hot shadow-new/30', work: true },
  { tabKey: 'accepted', label: 'To pack', hint: 'accepted: pick and pack', icon: PackageOpen, chip: 'from-sale to-sale-text shadow-sale/30', work: true },
  { tabKey: 'packed', label: 'To hand over', hint: 'packed: mark ready for pickup', icon: PackageCheck, chip: 'from-sale to-sale-text shadow-sale/30', work: true },
  { tabKey: 'ready', label: 'Waiting for pickup', hint: 'a delivery partner comes for them', icon: Bike, chip: 'from-brand to-brand-dark shadow-brand/30', work: false },
]

function Stage({ stage, count, active, last }: { stage: FlowStage; count: number | undefined; active: boolean; last: boolean }) {
  const shown = useCountUp(count ?? 0)
  const waiting = stage.work && (count ?? 0) > 0
  const Icon = stage.icon
  return (
    <li className="relative">
      <Link
        to={vendorOrdersLink(stage.tabKey)}
        aria-current={active ? 'page' : undefined}
        className={cn(
          'group relative flex h-full flex-col overflow-hidden rounded-2xl border bg-card p-3.5 transition duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-heading/10 motion-reduce:transition-none sm:p-5',
          active ? 'border-brand ring-2 ring-brand/25' : 'border-line hover:border-brand-soft',
        )}
      >
        {/* A soft light in the corner: amber while orders wait for the store */}
        <span aria-hidden="true" className={cn('absolute -top-12 -right-12 size-32 rounded-full blur-2xl transition-transform duration-500 group-hover:scale-125', waiting ? 'bg-warn/30' : 'bg-brand/10')} />
        <div className="relative flex items-start justify-between gap-2">
          <span
            className={cn(
              'grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br text-white shadow-lg transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110 sm:size-11',
              stage.chip,
            )}
          >
            <Icon className="size-4 sm:size-5" aria-hidden="true" />
          </span>
          {waiting && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-warn-light px-2 py-0.5 text-xs font-bold text-warn-text">
              <span aria-hidden="true" className="relative flex size-2">
                <span className="absolute inline-flex size-full rounded-full bg-warn opacity-75 motion-safe:animate-ping" />
                <span className="relative inline-flex size-2 rounded-full bg-warn" />
              </span>
              Waiting
            </span>
          )}
        </div>
        <p className="relative mt-3 text-sm font-bold text-heading sm:mt-4">{stage.label}</p>
        <p className="relative font-heading text-2xl font-bold text-heading tabular-nums sm:text-3xl">{count === undefined ? '–' : Math.round(shown)}</p>
        {/* Phones: the label says enough, the cards stay small */}
        <p className="relative mt-0.5 hidden text-xs sm:block">{stage.hint}</p>
      </Link>
      {/* The arrow to the next stage (wide screens, where the stages stand in one row); it keeps nudging onwards */}
      {!last && (
        <span
          aria-hidden="true"
          className="absolute top-1/2 -right-[1.375rem] z-10 hidden size-7 -translate-y-1/2 place-items-center rounded-full border border-line bg-card text-brand-text shadow-sm lg:grid"
        >
          <ChevronRight className="size-4 motion-safe:animate-nudge" />
        </span>
      )}
    </li>
  )
}

interface OrderFlowProps {
  /** the number of orders in each stage, in the order of STAGES (undefined while loading) */
  counts: (number | undefined)[]
  /** the queue's tab shown now: its stage is marked */
  activeTab: string
}

/**
 * The store's work at a glance: how many orders wait at each step, from a new order to the pickup, in four cards that
 * open their tab. The numbers count up; a step with orders waiting for the store says "Waiting" with a pulsing dot.
 */
export function OrderFlow({ counts, activeTab }: OrderFlowProps) {
  return (
    <ol aria-label="Your orders, step by step" className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
      {STAGES.map((stage, index) => (
        <Stage key={stage.tabKey} stage={stage} count={counts[index]} active={stage.tabKey === activeTab} last={index === STAGES.length - 1} />
      ))}
    </ol>
  )
}

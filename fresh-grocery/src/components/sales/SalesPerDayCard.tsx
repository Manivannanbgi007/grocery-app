import { Activity, CalendarCheck, CalendarClock, ChartSpline, ChevronDown, Table2, Trophy, type LucideIcon } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'
import type { SalesDay } from '../../api/types'
import { useCountUp } from '../../hooks/useCountUp'
import { cn } from '../../utils/cn'
import { formatWeekdayDay } from '../../utils/dates'
import { formatPrice } from '../../utils/format'
import { Spinner } from '../ui/Spinner'
import { ToneBar, ToneIcon } from '../ui/ToneIcon'
import { toneCard, type Tone } from '../ui/tones'
import { SalesChange } from './SalesChange'
import { SalesChart } from './SalesChart'
import { salesInsightsOf, type SalesInsights } from './salesInsights'
import { SalesNumbersTable } from './SalesNumbersTable'
import { periodLabel } from './salesPeriod'

interface InsightTileProps {
  icon: LucideIcon
  tone: Tone
  label: string
  /** the number: it counts up when it appears and from the old to the new one when the period changes */
  value: number
  format: (value: number) => string
  /** a short line under the number */
  hint: ReactNode
}

/** One quick fact above the chart, e.g. "Best day ₹10,631 · Fri, 30 Oct". */
function InsightTile({ icon, tone, label, value, format, hint }: InsightTileProps) {
  const shown = useCountUp(value)
  return (
    <div className="flex min-w-0 items-start gap-2.5 rounded-xl border border-line bg-card/80 p-3">
      <ToneIcon icon={icon} tone={tone} className="size-8 rounded-lg" />
      <div className="min-w-0">
        <p className="text-xs">{label}</p>
        {/* While counting: whole numbers only (no flickering paise); the exact value at the end. Long text wraps rather
            than being cut off: a cut-off amount ("₹96" of "₹964") would be wrong. */}
        <p className="font-heading text-base font-bold [overflow-wrap:anywhere] text-heading tabular-nums">{format(shown === value ? value : Math.round(shown))}</p>
        <p className="text-xs [overflow-wrap:anywhere]">{hint}</p>
      </div>
    </div>
  )
}

/** The four quick facts: the daily average, the best day, the days with sales and the last day (with its change). */
function InsightTiles({ insights, dayCount }: { insights: SalesInsights; dayCount: number }) {
  const { dailyAverage, bestDay, daysWithSales, lastDay } = insights
  return (
    <div className="mb-5 grid grid-cols-2 gap-2 @2xl:grid-cols-4">
      <InsightTile icon={Activity} tone="blue" label="Daily average" value={dailyAverage} format={formatPrice} hint={`over ${dayCount} days`} />
      {bestDay && <InsightTile icon={Trophy} tone="yellow" label="Best day" value={bestDay.sales} format={formatPrice} hint={formatWeekdayDay(bestDay.date)} />}
      <InsightTile icon={CalendarCheck} tone="green" label="Days with sales" value={daysWithSales} format={(count) => `${count} of ${dayCount}`} hint="at least one sale" />
      {lastDay && (
        <InsightTile
          icon={CalendarClock}
          tone="violet"
          label="Last day"
          value={lastDay.day.sales}
          format={formatPrice}
          hint={
            <>
              {formatWeekdayDay(lastDay.day.date)} · <SalesChange change={lastDay.change} />
            </>
          }
        />
      )}
    </div>
  )
}

/** In place of the chart when no day had a sale: why the chart is empty. */
function NoSales({ orders }: { orders: number }) {
  return (
    <div className="flex min-h-56 flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-line px-4 py-8 text-center">
      <ToneIcon icon={ChartSpline} tone="blue" className="size-12 rounded-2xl" />
      <p className="font-heading font-bold text-heading">No sales in this period</p>
      <p className="max-w-xs text-sm">{orders === 0 ? 'No orders were placed on these days.' : 'The orders of these days were cancelled or are not confirmed yet.'}</p>
    </div>
  )
}

interface SalesPerDayCardProps {
  days: SalesDay[]
  /** true while another period loads: a small "Updating…" note in the corner (the old numbers stay until then) */
  updating?: boolean
  className?: string
}

/**
 * "Sales per day": the period, four quick facts, the trend as a chart and, under "Show the numbers", the same numbers
 * as a table. One day gets no quick facts (the totals above say it all); a period without sales gets a message instead
 * of a flat chart.
 */
export function SalesPerDayCard({ days, updating = false, className }: SalesPerDayCardProps) {
  const [showNumbers, setShowNumbers] = useState(false)
  const numbersId = useId()
  const insights = salesInsightsOf(days)
  const first = days[0]?.date
  const last = days[days.length - 1]?.date

  return (
    <section aria-labelledby="sales-per-day-title" className={toneCard('blue', cn('@container min-w-0 rounded-2xl p-4 shadow-sm shadow-heading/5 sm:p-6', className))}>
      <ToneBar tone="blue" />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <ToneIcon icon={ChartSpline} tone="blue" solid />
        <div className="min-w-0 flex-1">
          <h3 id="sales-per-day-title" className="text-lg">
            Sales per day
          </h3>
          {first && last && (
            <p className="text-xs">
              {periodLabel(first, last)} · {days.length} {days.length === 1 ? 'day' : 'days'}
            </p>
          )}
        </div>
        {updating && (
          <span role="status" className="flex items-center gap-1.5 rounded-full bg-sale-light px-3 py-1 text-xs font-bold text-sale-text">
            <Spinner size="sm" />
            Updating…
          </span>
        )}
      </div>

      {insights.bestDay === null ? (
        <NoSales orders={days.reduce((sum, day) => sum + day.orders, 0)} />
      ) : (
        <>
          {days.length > 1 && <InsightTiles insights={insights} dayCount={days.length} />}
          <SalesChart days={days} />
          <button
            type="button"
            aria-expanded={showNumbers}
            aria-controls={numbersId}
            onClick={() => setShowNumbers(!showNumbers)}
            className="mt-5 flex w-full items-center justify-between gap-3 rounded-xl border border-line bg-surface/70 px-4 py-2.5 text-sm font-bold text-heading transition-colors hover:border-brand-soft hover:bg-brand-light hover:text-brand-text"
          >
            <span className="flex items-center gap-2">
              <Table2 className="size-4 text-brand-text" aria-hidden="true" />
              {showNumbers ? 'Hide the numbers' : 'Show the numbers'}
            </span>
            <ChevronDown className={cn('size-4 shrink-0 transition-transform duration-300', showNumbers && 'rotate-180')} aria-hidden="true" />
          </button>
          {/* The table slides in under the button (motion-safe: not with "reduce motion") */}
          {showNumbers && (
            <div id={numbersId} className="mt-3 motion-safe:animate-rise">
              <SalesNumbersTable days={days} />
            </div>
          )}
        </>
      )}
    </section>
  )
}

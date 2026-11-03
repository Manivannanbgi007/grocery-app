import { Trophy } from 'lucide-react'
import type { SalesDay } from '../../api/types'
import { cn } from '../../utils/cn'
import { formatWeekdayDay } from '../../utils/dates'
import { formatPrice } from '../../utils/format'
import { SalesChange } from './SalesChange'
import { changeFromDayBefore, salesInsightsOf } from './salesInsights'

const CELL = 'px-3 py-2 text-right'

/**
 * "Show the numbers": the chart's numbers as a table, one line per day. The best day is marked, each day shows its
 * change from the day before and a thin bar of its sales next to the best day's, and the totals stay at the bottom.
 * A long period scrolls inside the table (the header and the totals stay in view). A phone gets the day, the orders
 * and the sales only (the chart's tooltip has the change).
 */
export function SalesNumbersTable({ days }: { days: SalesDay[] }) {
  const best = salesInsightsOf(days).bestDay
  const totalOrders = days.reduce((sum, day) => sum + day.orders, 0)
  const totalCancelled = days.reduce((sum, day) => sum + day.cancelledOrders, 0)
  const totalSales = Math.round(days.reduce((sum, day) => sum + day.sales, 0) * 100) / 100

  return (
    <div className="max-h-96 overflow-auto rounded-xl border border-line bg-card">
      <table className="w-full text-sm tabular-nums">
        <caption className="sr-only">Sales per day</caption>
        <thead className="sticky top-0 z-10 bg-surface text-xs text-heading">
          <tr>
            <th scope="col" className="px-3 py-2 text-left">
              Day
            </th>
            <th scope="col" className={CELL}>
              Orders
            </th>
            <th scope="col" className={cn(CELL, 'hidden sm:table-cell')}>
              Cancelled
            </th>
            <th scope="col" className={CELL}>
              Sales
            </th>
            <th scope="col" className={cn(CELL, 'hidden sm:table-cell')}>
              Change
            </th>
          </tr>
        </thead>
        <tbody>
          {days.map((day, index) => {
            const isBest = day === best
            return (
              <tr key={day.date} className={cn('border-t border-line transition-colors hover:bg-brand-light/60', isBest && 'bg-warn-light/70')}>
                <th scope="row" className="px-3 py-2 text-left font-bold whitespace-nowrap text-heading">
                  {formatWeekdayDay(day.date)}
                  {isBest && (
                    <span className="ml-2 inline-flex items-center gap-1 rounded-full bg-accent/30 px-2 py-0.5 text-[11px] text-warn-text">
                      <Trophy className="size-3" aria-hidden="true" />
                      Best
                    </span>
                  )}
                </th>
                <td className={CELL}>{day.orders}</td>
                <td className={cn(CELL, 'hidden sm:table-cell')}>{day.cancelledOrders}</td>
                <td className={CELL}>
                  <span className="font-bold text-heading">{formatPrice(day.sales)}</span>
                  {/* The day's sales next to the best day's (the amount is written out, so the bar is decoration) */}
                  <span aria-hidden="true" className="mt-1 ml-auto block h-1 w-16 rounded-full bg-line/60">
                    <span className="block h-full rounded-full bg-brand" style={{ width: `${best ? (day.sales / best.sales) * 100 : 0}%` }} />
                  </span>
                </td>
                <td className={cn(CELL, 'hidden text-xs sm:table-cell')}>
                  <SalesChange change={changeFromDayBefore(days, index)} />
                </td>
              </tr>
            )
          })}
        </tbody>
        <tfoot className="sticky bottom-0 bg-surface font-bold text-heading">
          <tr className="border-t border-line">
            <th scope="row" className="px-3 py-2 text-left">
              Total
            </th>
            <td className={CELL}>{totalOrders}</td>
            <td className={cn(CELL, 'hidden sm:table-cell')}>{totalCancelled}</td>
            <td className={CELL}>{formatPrice(totalSales)}</td>
            <td className={cn(CELL, 'hidden sm:table-cell')} />
          </tr>
        </tfoot>
      </table>
    </div>
  )
}

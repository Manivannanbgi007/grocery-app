import { Calculator, PackageCheck, Wallet } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { PageTitle } from '../../../components/layout/PageTitle'
import { DailyBarChart } from '../../../components/sales/DailyBarChart'
import { KpiTile } from '../../../components/sales/KpiTile'
import { PeriodPicker } from '../../../components/sales/PeriodPicker'
import { salesPeriodOf } from '../../../components/sales/salesPeriod'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { cn } from '../../../utils/cn'
import { daysBetween, formatShortDay } from '../../../utils/dates'
import { formatPrice } from '../../../utils/format'
import { PAGE_WIDTH } from '../layout/pageWidth'
import { useEarnings } from '../tasks/useTasks'

/**
 * What the partner earned in the last 7, 14 or 30 days (?days=): totals, a column per day, and the same numbers as a
 * table. A partner earns their zone's payout for every delivered order.
 */
export function DeliveryEarningsPage() {
  const [searchParams] = useSearchParams()
  const { days, from, to } = salesPeriodOf(searchParams)
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useEarnings(from, to)

  let content
  if (isPending) content = <LoadingState label="Loading your earnings…" className="min-h-96" />
  else if (isError) content = <ErrorState compact title="Could not load your earnings" error={error} onRetry={() => void refetch()} className="rounded-2xl bg-card" />
  else {
    // The backend lists only the days with deliveries; the chart shows every day of the period
    const perDay = daysBetween(from, to).map((date) => {
      const day = data.days.find((candidate) => candidate.date === date)
      return { date, value: day?.amount ?? 0, count: day?.deliveries ?? 0 }
    })
    content = (
      <div className={cn('space-y-4', isPlaceholderData && 'opacity-60')}>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <KpiTile label="Earned" value={formatPrice(data.total)} icon={Wallet} accent="green" />
          <KpiTile label="Deliveries" value={String(data.deliveries)} icon={PackageCheck} accent="blue" />
          <KpiTile
            label="Per delivery"
            value={data.deliveries > 0 ? formatPrice(Math.round((data.total / data.deliveries) * 100) / 100) : '–'}
            icon={Calculator}
            accent="orange"
            className="col-span-2 sm:col-span-1"
          />
        </div>
        <section aria-labelledby="earnings-per-day-title" className="min-w-0 rounded-2xl border border-line bg-card p-4 shadow-sm sm:p-6">
          <h2 id="earnings-per-day-title" className="mb-4 text-lg">
            Earned per day
          </h2>
          <DailyBarChart label="Earned per day" countWords={['delivery', 'deliveries']} days={perDay} />
          <details className="mt-4 text-sm">
            <summary className="cursor-pointer font-bold text-brand-text">Show the numbers</summary>
            <table className="mt-2 w-full text-left tabular-nums">
              <caption className="sr-only">Earned per day</caption>
              <thead className="border-b border-line text-heading">
                <tr>
                  <th scope="col" className="py-1">Day</th>
                  <th scope="col" className="py-1 text-right">Deliveries</th>
                  <th scope="col" className="py-1 text-right">Earned</th>
                </tr>
              </thead>
              <tbody>
                {perDay.map((day) => (
                  <tr key={day.date} className="border-b border-line">
                    <td className="py-1">{formatShortDay(day.date)}</td>
                    <td className="py-1 text-right">{day.count}</td>
                    <td className="py-1 text-right">{formatPrice(day.value)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </details>
        </section>
        <p className="text-xs">A delivery counts on the day it was delivered (India time). Cash you collect belongs to the order, not to your earnings.</p>
      </div>
    )
  }

  return (
    <div className={PAGE_WIDTH}>
      <PageTitle title="Earnings" />
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl">Earnings</h1>
          <p className="mt-1">What you earned for your deliveries, day by day.</p>
        </div>
        <PeriodPicker path="/delivery/earnings" days={days} />
      </div>
      {content}
    </div>
  )
}

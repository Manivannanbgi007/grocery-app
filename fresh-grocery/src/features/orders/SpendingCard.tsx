import { Wallet } from 'lucide-react'
import { DailyBarChart } from '../../components/sales/DailyBarChart'
import { ErrorState } from '../../components/ui/ErrorState'
import { LoadingState } from '../../components/ui/LoadingState'
import { ToneBar, ToneIcon } from '../../components/ui/ToneIcon'
import { toneCard } from '../../components/ui/tones'
import { formatMonth } from '../../utils/dates'
import { formatPrice } from '../../utils/format'
import { monthlySpending } from './spending'
import { SPENDING_ORDERS, useOrdersForSpending } from './useOrders'

/** How many months "Your spending" shows. */
const MONTHS = 6

/**
 * "Your spending" on My orders: what the customer spent per month over the last 6 months (cancelled, rejected and
 * undelivered orders do not count), as columns, with the same numbers under "Show the numbers".
 * Nothing at all for a customer without orders (the list below says so).
 */
export function SpendingCard() {
  const { data, isPending, isError, error, refetch } = useOrdersForSpending()

  if (isPending) return <LoadingState className="mb-8 min-h-80" />
  if (isError) return <ErrorState compact title="Could not load your spending" error={error} onRetry={() => void refetch()} className="mb-8 rounded-2xl border border-line bg-card" />
  if (data.content.length === 0) return null

  const months = monthlySpending(data.content, MONTHS)
  const total = months.reduce((sum, month) => sum + month.value, 0)
  const orders = months.reduce((sum, month) => sum + month.count, 0)

  return (
    // Green, the colour of money: a card with a green bar along the top and a wallet icon; the total in a green pill
    <section aria-labelledby="spending-title" className={toneCard('green', 'mb-8 p-4 sm:p-6')}>
      <ToneBar tone="green" />
      <div className="mb-4 flex flex-wrap items-end justify-between gap-x-6 gap-y-2">
        <div className="flex items-center gap-3">
          <ToneIcon icon={Wallet} tone="green" solid />
          <div>
            <h2 id="spending-title" className="text-xl">
              Your spending
            </h2>
            <p className="text-xs">The last {MONTHS} months, without cancelled or undelivered orders</p>
          </div>
        </div>
        <p className="rounded-full bg-brand-light px-4 py-1.5 text-sm">
          <span className="font-heading text-2xl font-bold text-brand-text">{formatPrice(total)}</span> in {orders} {orders === 1 ? 'order' : 'orders'}
        </p>
      </div>
      {orders === 0 ? (
        <p className="py-8 text-center text-sm">Nothing spent in the last {MONTHS} months.</p>
      ) : (
        <DailyBarChart label="Spent per month" days={months} countWords={['order', 'orders']} formatLabel={formatMonth} />
      )}
      <details className="mt-4 text-sm">
        <summary className="cursor-pointer font-bold text-brand-text">Show the numbers</summary>
        <table className="mt-2 w-full text-left tabular-nums">
          <caption className="sr-only">Spent per month</caption>
          <thead className="border-b border-line text-heading">
            <tr>
              <th scope="col" className="py-1">Month</th>
              <th scope="col" className="py-1 text-right">Orders</th>
              <th scope="col" className="py-1 text-right">Spent</th>
            </tr>
          </thead>
          <tbody>
            {months.map((month) => (
              <tr key={month.date} className="border-b border-line">
                <td className="py-1">{formatMonth(month.date)}</td>
                <td className="py-1 text-right">{month.count}</td>
                <td className="py-1 text-right">{formatPrice(month.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
      {data.totalElements > SPENDING_ORDERS && <p className="mt-2 text-xs">From your newest {SPENDING_ORDERS} orders.</p>}
    </section>
  )
}

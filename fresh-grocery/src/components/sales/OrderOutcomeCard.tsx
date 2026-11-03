import type { SalesDay } from '../../api/types'
import { cn } from '../../utils/cn'
import { DonutChart } from '../charts/DonutChart'

/**
 * "Orders in this period": how the period's orders ended up, as a ring. It reads the same daily report as the sales
 * (no extra request): counted as sales, cancelled or rejected (or not delivered), and the rest, still waiting for the
 * system or the store to confirm them.
 */
export function OrderOutcomeCard({ days, className }: { days: SalesDay[]; className?: string }) {
  const sum = (pick: (day: SalesDay) => number) => days.reduce((total, day) => total + pick(day), 0)
  const orders = sum((day) => day.orders)
  const sales = sum((day) => day.salesOrders)
  const stopped = sum((day) => day.cancelledOrders)
  const waiting = Math.max(0, orders - sales - stopped)

  return (
    <section aria-labelledby="order-outcome-title" className={cn('min-w-0 rounded-2xl border border-line bg-card p-4 shadow-sm shadow-heading/5 sm:p-6', className)}>
      <h3 id="order-outcome-title" className="text-lg">
        Orders in this period
      </h3>
      <p className="mb-4 text-xs">How every order placed in the period ended up</p>
      {orders === 0 ? (
        <p className="py-10 text-center text-sm">No orders in this period.</p>
      ) : (
        <DonutChart
          label="Orders in this period by outcome"
          countWords={['order', 'orders']}
          parts={[
            { key: 'sales', label: 'Counted as sales', value: sales, tone: 'good' },
            { key: 'waiting', label: 'Not confirmed yet', value: waiting, tone: 'wait' },
            { key: 'stopped', label: 'Cancelled, rejected or not delivered', value: stopped, tone: 'bad' },
          ]}
        />
      )}
    </section>
  )
}

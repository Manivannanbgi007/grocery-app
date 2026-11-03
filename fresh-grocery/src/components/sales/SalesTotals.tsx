import { CircleX, IndianRupee, Receipt, ShoppingBag } from 'lucide-react'
import type { DailySales } from '../../api/types'
import { formatPrice } from '../../utils/format'
import { KpiTile } from './KpiTile'
import { periodTotalsOf } from './periodTotals'

/** The totals of a period: sales, orders, cancelled or rejected ones, and the average order. */
export function SalesTotals({ sales }: { sales: DailySales }) {
  const totals = periodTotalsOf(sales)
  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
      <KpiTile label="Sales" value={formatPrice(totals.sales)} icon={IndianRupee} accent="blue" />
      <KpiTile label="Orders" value={String(totals.orders)} icon={ShoppingBag} accent="green" />
      <KpiTile label="Cancelled or rejected" value={String(totals.cancelled)} icon={CircleX} accent="pink" />
      <KpiTile label="Average order" value={totals.averageOrder === null ? '–' : formatPrice(totals.averageOrder)} icon={Receipt} accent="orange" />
    </div>
  )
}

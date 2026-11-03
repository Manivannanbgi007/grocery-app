import type { SalesDay } from '../../api/types'
import { AreaChart } from '../charts/AreaChart'

/** Sales per day as a trend (the orders that count as sales, see SALES_DEFINITION). */
export function SalesChart({ days }: { days: SalesDay[] }) {
  return (
    <AreaChart
      label="Sales per day"
      valueName="Sales"
      countWords={['order', 'orders']}
      days={days.map((day) => ({ date: day.date, value: day.sales, count: day.salesOrders }))}
    />
  )
}

import type { OrderStatus, OrderSummary } from '../../api/types'
import type { ChartDay } from '../../components/charts/chartScale'
import { indiaDay, lastMonths } from '../../utils/dates'

/** Orders that cost the customer nothing (yet): never confirmed, cancelled, rejected, or not delivered. */
const NOT_SPENT: OrderStatus[] = ['PENDING', 'CANCELLED', 'REJECTED', 'DELIVERY_FAILED']

/**
 * What the customer spent per month (India time) over the last `months` months, oldest first, from their orders:
 * every month is listed, also one without orders. `date` is the month ("2026-10").
 */
export function monthlySpending(orders: OrderSummary[], months: number, today: string = indiaDay()): ChartDay[] {
  const spent = orders.filter((order) => !NOT_SPENT.includes(order.status))
  return lastMonths(today, months).map((month) => {
    const ofMonth = spent.filter((order) => indiaDay(order.placedAt).startsWith(month))
    const value = Math.round(ofMonth.reduce((sum, order) => sum + order.totalAmount, 0) * 100) / 100
    return { date: month, value, count: ofMonth.length }
  })
}

import type { DailySales } from '../../api/types'

/** The totals of a sales period, summed from its days. */
export interface PeriodTotals {
  sales: number
  orders: number
  /** cancelled, rejected or not delivered */
  cancelled: number
  /** the sales per order that counts as a sale; null when no order counts yet */
  averageOrder: number | null
}

/** Sums a period's days: used by the SalesTotals tiles and the admin dashboard's cards. */
export function periodTotalsOf(sales: DailySales): PeriodTotals {
  const cancelled = sales.days.reduce((sum, day) => sum + day.cancelledOrders, 0)
  const salesOrders = sales.days.reduce((sum, day) => sum + day.salesOrders, 0)
  return {
    sales: sales.totalSales,
    orders: sales.totalOrders,
    cancelled,
    averageOrder: salesOrders > 0 ? Math.round((sales.totalSales / salesOrders) * 100) / 100 : null,
  }
}

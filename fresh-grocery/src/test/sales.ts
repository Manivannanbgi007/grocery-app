import type { SalesDay } from '../api/types'

/** A day of a sales report for tests: `orders` orders (one when anything was sold) that all count as sales, for `sales` rupees. */
export function salesDay(date: string, sales: number, orders = sales > 0 ? 1 : 0): SalesDay {
  return { date, orders, cancelledOrders: 0, salesOrders: orders, itemsTotal: sales, couponDiscounts: 0, deliveryFees: 0, sales }
}

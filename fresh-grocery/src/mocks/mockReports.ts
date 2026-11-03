import type { AdminDashboard, CommissionReport, DailySales, Order, PaymentSummary, PaymentTotals, Review, TopProduct, TopStore } from '../api/types'
import { daysBefore, daysBetween, indiaDay } from '../utils/dates'
import { MockApiError } from './mockApiError'
import { allUsers } from './mockAuth'
import { catalogProducts } from './mockCatalog'
import { searchPartners } from './mockDelivery'
import { allOrders, searchPayments, storeOrders } from './mockOrders'
import { allMockReviews } from './mockRatings'
import { searchStores, storeById } from './mockStores'

/*
 * The vendor's and the admin's reports for the mock API, worked out from the mock orders like report-service does from
 * the order events: a "sale" is an order that was confirmed and did not end cancelled, rejected or undelivered; days
 * are India days. storeId null = every store.
 */

const MAX_DAYS = 366
const roundMoney = (amount: number) => Math.round(amount * 100) / 100
const STOPPED = ['CANCELLED', 'REJECTED', 'DELIVERY_FAILED']

const isSale = (order: Order) => order.status !== 'PENDING' && !STOPPED.includes(order.status)

function checkRange(from: string | null, to: string | null): { from: string; to: string } {
  if (!from || !to) throw new MockApiError(400, "Required parameter 'from' or 'to' is not present.")
  if (from > to) throw new MockApiError(400, "'from' must not be after 'to'")
  if (daysBetween(from, to).length > MAX_DAYS) throw new MockApiError(400, `A report covers at most ${MAX_DAYS} days`)
  return { from, to }
}

const ordersOf = (storeId: number | null) => (storeId === null ? allOrders() : storeOrders(storeId))

export function dailySales(storeId: number | null, fromParam: string | null, toParam: string | null): DailySales {
  const { from, to } = checkRange(fromParam, toParam)
  const orders = ordersOf(storeId)
  const days = daysBetween(from, to).map((date) => {
    const placed = orders.filter((order) => indiaDay(order.placedAt) === date)
    const sales = placed.filter(isSale)
    const sum = (pick: (order: Order) => number) => roundMoney(sales.reduce((total, order) => total + pick(order), 0))
    return {
      date,
      orders: placed.length,
      cancelledOrders: placed.filter((order) => STOPPED.includes(order.status)).length,
      salesOrders: sales.length,
      itemsTotal: sum((order) => order.amounts.itemsTotal),
      couponDiscounts: sum((order) => order.amounts.couponDiscount),
      deliveryFees: sum((order) => order.amounts.deliveryFee),
      sales: sum((order) => order.amounts.totalAmount),
    }
  })
  return {
    from,
    to,
    storeId,
    days,
    totalSales: roundMoney(days.reduce((total, day) => total + day.sales, 0)),
    totalOrders: days.reduce((total, day) => total + day.orders, 0),
  }
}

/** The best sellers by units, in the orders that count as sales. */
export function topProducts(storeId: number | null, fromParam: string | null, toParam: string | null, limit: number): TopProduct[] {
  const { from, to } = checkRange(fromParam, toParam)
  const totals = new Map<number, TopProduct>()
  for (const order of ordersOf(storeId).filter(isSale)) {
    const day = indiaDay(order.placedAt)
    if (day < from || day > to) continue
    for (const item of order.items) {
      const current = totals.get(item.productId) ?? { productId: item.productId, productName: item.productName, quantity: 0, sales: 0 }
      totals.set(item.productId, { ...current, quantity: current.quantity + item.quantity, sales: roundMoney(current.sales + item.lineTotal) })
    }
  }
  return [...totals.values()].sort((a, b) => b.quantity - a.quantity || b.sales - a.sales).slice(0, limit)
}

/** The stores with the highest sales in the period. */
export function topStores(fromParam: string | null, toParam: string | null, limit: number): TopStore[] {
  const { from, to } = checkRange(fromParam, toParam)
  const totals = new Map<number, TopStore>()
  for (const order of allOrders().filter(isSale)) {
    const day = indiaDay(order.placedAt)
    if (day < from || day > to) continue
    const current = totals.get(order.storeId) ?? { storeId: order.storeId, storeName: order.storeName, orders: 0, sales: 0 }
    totals.set(order.storeId, { ...current, orders: current.orders + 1, sales: roundMoney(current.sales + order.amounts.totalAmount) })
  }
  return [...totals.values()].sort((a, b) => b.sales - a.sales).slice(0, limit)
}

/**
 * The platform's commission per store on the orders delivered in the period (CommissionReportResponse): items total x
 * the commission %, the store gets the rest. report-service keeps the percentage each order was placed with; the mock
 * uses the store's current one.
 */
export function commissionReport(fromParam: string | null, toParam: string | null): CommissionReport {
  const { from, to } = checkRange(fromParam, toParam)
  const delivered = allOrders().filter((order) => order.status === 'DELIVERED' && order.deliveredAt !== null && indiaDay(order.deliveredAt) >= from && indiaDay(order.deliveredAt) <= to)
  const storeIds = [...new Set(delivered.map((order) => order.storeId))].sort((a, b) => a - b)
  const stores = storeIds.map((storeId) => {
    const orders = delivered.filter((order) => order.storeId === storeId)
    const percentage = storeById(storeId)?.commissionPercentage ?? 0
    const itemsTotal = roundMoney(orders.reduce((total, order) => total + order.amounts.itemsTotal, 0))
    const commission = roundMoney(orders.reduce((total, order) => total + roundMoney((order.amounts.itemsTotal * percentage) / 100), 0))
    return { storeId, storeName: storeById(storeId)?.name ?? `Store ${storeId}`, deliveredOrders: orders.length, itemsTotal, commission, storePayout: roundMoney(itemsTotal - commission) }
  })
  const totalItems = roundMoney(stores.reduce((total, store) => total + store.itemsTotal, 0))
  const totalCommission = roundMoney(stores.reduce((total, store) => total + store.commission, 0))
  return { from, to, stores, totalItems, totalCommission, totalStorePayout: roundMoney(totalItems - totalCommission) }
}

/** The admin dashboard (DashboardResponse), worked out from the mock data. */
export function dashboard(): AdminDashboard {
  const today = indiaDay()
  const monthAgo = daysBefore(today, 29)
  const orders = allOrders()
  const placedToday = orders.filter((order) => indiaDay(order.placedAt) === today)
  const deliveredToday = orders.filter((order) => order.deliveredAt !== null && indiaDay(order.deliveredAt) === today)
  const recentlyDelivered = orders.filter((order) => order.deliveredAt !== null && indiaDay(order.deliveredAt) >= monthAgo)
  const minutes = recentlyDelivered.map((order) => (new Date(order.deliveredAt!).getTime() - new Date(order.placedAt).getTime()) / 60_000)
  const sum = (list: Order[]) => roundMoney(list.reduce((total, order) => total + order.amounts.totalAmount, 0))
  return {
    today,
    totalOrders: orders.length,
    ordersToday: placedToday.length,
    salesToday: sum(placedToday.filter(isSale)),
    revenueToday: sum(deliveredToday),
    activeStores: searchStores({ status: 'ACTIVE', city: null, keyword: null }).length,
    activeDeliveryPartners: searchPartners('APPROVED').length,
    totalCustomers: allUsers().filter((user) => user.role === 'CUSTOMER').length,
    activeCustomers: new Set(orders.filter((order) => indiaDay(order.placedAt) >= monthAgo).map((order) => order.customerUserId)).size,
    // The mock refunds a paid order at once, so a refund never waits
    pendingRefunds: orders.filter((order) => STOPPED.includes(order.status) && order.paymentStatus === 'PAID').length,
    outOfStockProducts: catalogProducts().filter((product) => !product.inStock).length,
    averageDeliveryMinutes: minutes.length > 0 ? Math.round(minutes.reduce((total, value) => total + value, 0) / minutes.length) : null,
    generatedAt: new Date().toISOString(),
  }
}

/** Money in and out for the payments created in the period (PaymentQueryServiceImpl.getSummary). */
export function paymentSummary(fromParam: string | null, toParam: string | null): PaymentSummary {
  const { from, to } = checkRange(fromParam, toParam)
  const inPeriod = searchPayments({ status: null, method: null, storeId: null }).filter((payment) => {
    const day = indiaDay(payment.createdAt)
    return day >= from && day <= to
  })
  const totals: PaymentTotals[] = []
  for (const payment of inPeriod) {
    const line = totals.find((total) => total.method === payment.method && total.status === payment.status)
    if (line) {
      line.count += 1
      line.amount = roundMoney(line.amount + payment.amount)
    } else totals.push({ method: payment.method, status: payment.status, count: 1, amount: payment.amount })
  }
  const amountOf = (method: string, status: string) => totals.find((total) => total.method === method && total.status === status)?.amount ?? 0
  const paidOnline = amountOf('ONLINE', 'PAID')
  const collectedCash = amountOf('COD', 'COLLECTED')
  return { from, to, paidOnline, collectedCash, refunded: amountOf('ONLINE', 'REFUNDED'), received: roundMoney(paidOnline + collectedCash), totals }
}

/** The visible reviews of the store's products, newest first (product-service's vendor search). */
export function storeReviews(storeId: number, productId: number | null, rating: number | null): Review[] {
  return allMockReviews()
    .filter((review) => review.storeId === storeId && review.status === 'VISIBLE')
    .filter((review) => productId === null || review.productId === productId)
    .filter((review) => rating === null || review.rating === rating)
    .sort((a, b) => b.id - a.id)
}

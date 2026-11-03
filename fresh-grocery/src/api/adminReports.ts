import { apiRequest } from './client'
import type { AdminDashboard, DailySales, TopProduct, TopStore } from './types'

/*
 * The platform's numbers (report-service). They are built from the events of the other services, so a new order shows
 * up a moment later. Dates are "2026-10-26" (India dates); a report covers at most 366 days.
 */

export function getDashboard(signal?: AbortSignal): Promise<AdminDashboard> {
  return apiRequest<AdminDashboard>('/api/v1/admin/reports/dashboard', { signal })
}

/** Sales per day of all stores, or of one store. */
export function getPlatformSales(from: string, to: string, storeId?: number, signal?: AbortSignal): Promise<DailySales> {
  return apiRequest<DailySales>('/api/v1/admin/reports/sales/daily', { query: { from, to, storeId }, signal })
}

/** The stores with the highest sales (1 to 50). */
export function getTopStores(from: string, to: string, limit: number, signal?: AbortSignal): Promise<TopStore[]> {
  return apiRequest<TopStore[]>('/api/v1/admin/reports/top-stores', { query: { from, to, limit }, signal })
}

/** The best-selling products by quantity (1 to 50), of all stores or of one store. */
export function getPlatformTopProducts(from: string, to: string, limit: number, storeId?: number, signal?: AbortSignal): Promise<TopProduct[]> {
  return apiRequest<TopProduct[]>('/api/v1/admin/reports/top-products', { query: { from, to, limit, storeId }, signal })
}

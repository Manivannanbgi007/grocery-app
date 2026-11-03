import { apiRequest } from './client'
import type { DailySales, PageResponse, Review, TopProduct } from './types'

/*
 * What the vendor learns about the store: sales and best sellers (report-service, built from the order events, so a
 * new order shows up a moment later) and the customers' reviews (product-service).
 * Dates are "2026-10-26"; a report covers at most 366 days.
 */

export function getDailySales(from: string, to: string, signal?: AbortSignal): Promise<DailySales> {
  return apiRequest<DailySales>('/api/v1/vendor/reports/sales/daily', { query: { from, to }, signal })
}

export function getTopProducts(from: string, to: string, limit: number, signal?: AbortSignal): Promise<TopProduct[]> {
  return apiRequest<TopProduct[]>('/api/v1/vendor/reports/top-products', { query: { from, to, limit }, signal })
}

/** The visible reviews of the store's products, newest first; rating=1 finds the unhappy customers. */
export function getMyReviews(params: { productId?: number; rating?: number; page?: number; size?: number }, signal?: AbortSignal): Promise<PageResponse<Review>> {
  return apiRequest<PageResponse<Review>>('/api/v1/vendor/reviews', { query: { ...params }, signal })
}

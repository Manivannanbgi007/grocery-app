import { apiRequest } from './client'
import type { PageResponse, Review } from './types'

/** The visible reviews of a product, newest first. `page` starts at 0. */
export function getProductReviews(productId: number, page: number, size: number, signal?: AbortSignal): Promise<PageResponse<Review>> {
  return apiRequest<PageResponse<Review>>(`/api/v1/products/${productId}/reviews`, { query: { page, size }, signal })
}

export interface ReviewRequest {
  /** the delivered order the product came with (proves the customer bought it) */
  orderId: number
  /** 1 to 5 stars */
  rating: number
  /** optional, at most 1000 characters */
  comment: string | null
}

/**
 * Reviews a product of one of the customer's delivered orders, once per order. Fails with
 * 400 "You can review the products of an order once it is delivered" or 409 "You have already reviewed this product for order 9".
 */
export function writeReview(productId: number, request: ReviewRequest): Promise<Review> {
  return apiRequest<Review>(`/api/v1/products/${productId}/reviews`, { method: 'POST', body: request })
}

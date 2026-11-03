import { apiRequest } from './client'
import type { DeliveryRating } from './types'

export interface DeliveryRatingRequest {
  orderId: number
  /** 1 to 5 stars */
  rating: number
  /** optional, at most 500 characters */
  comment: string | null
}

/*
 * The customer's rating of the delivery of an order (delivery-service). There is a delivery once the store has
 * handed the order over for pickup; before that the rating answers 404 "No delivery found for order 12".
 */

/** rating is null until the customer rates the delivery. */
export function getMyDeliveryRating(orderId: number, signal?: AbortSignal): Promise<DeliveryRating> {
  return apiRequest<DeliveryRating>(`/api/v1/delivery-ratings/orders/${orderId}`, { signal })
}

/** Once per delivered order. Fails with 400 "You have already rated this delivery" or "Only a delivered order can be rated ...". */
export function rateDelivery(request: DeliveryRatingRequest): Promise<DeliveryRating> {
  return apiRequest<DeliveryRating>('/api/v1/delivery-ratings', { method: 'POST', body: request })
}

import type { DeliveryRatingRequest } from '../api/deliveryRatings'
import type { ReviewRequest } from '../api/reviews'
import type { ApiErrorDetail, DeliveryRating, Order, Review } from '../api/types'
import { demoReviews } from './data/reviews'
import { MockApiError, validationFailed } from './mockApiError'
import { getOrder } from './mockOrders'

/*
 * What customers rate after a delivery, for the mock API (in memory): the delivery (delivery-service) and the
 * products of the order (product-service). The rules and messages copy the backend.
 */

interface MockDeliveryRating {
  rating: number
  comment: string | null
  ratedAt: string
}

let deliveryRatings = new Map<number, MockDeliveryRating>()
let writtenReviews: Review[] = []
let nextReviewId = 5001
/** Reviews an admin hid (review id -> reason) or showed again (null) while the mock API runs. */
let moderated = new Map<number, string | null>()

/** For tests: forget the ratings and reviews of a test. */
export function resetMockRatings(): void {
  deliveryRatings = new Map()
  writtenReviews = []
  nextReviewId = 5001
  moderated = new Map()
}

/** The demo reviews and the ones written while the mock API runs, with the admins' hiding. */
export function allMockReviews(): Review[] {
  return [...demoReviews, ...writtenReviews].map((review) => {
    if (!moderated.has(review.id)) return review
    const reason = moderated.get(review.id) ?? null
    return { ...review, status: reason ? 'HIDDEN' : 'VISIBLE', hiddenReason: reason }
  })
}

// ---------- The admin's review moderation (AdminReviewController) ----------

/** Every review, newest first; every filter is optional. */
export function searchAllReviews(filter: { storeId: number | null; productId: number | null; rating: number | null; status: string | null }): Review[] {
  return allMockReviews()
    .filter((review) => (filter.storeId === null || review.storeId === filter.storeId) && (filter.productId === null || review.productId === filter.productId))
    .filter((review) => (filter.rating === null || review.rating === filter.rating) && (!filter.status || review.status === filter.status))
    .sort((a, b) => b.id - a.id)
}

function anyReview(reviewId: number): Review {
  const review = allMockReviews().find((candidate) => candidate.id === reviewId)
  if (!review) throw new MockApiError(404, `Review not found with id: ${reviewId}`)
  return review
}

export function hideReview(reviewId: number, reason: unknown): Review {
  if (typeof reason !== 'string' || !reason.trim()) throw validationFailed([{ field: 'reason', message: 'must not be blank' }])
  if (reason.length > 255) throw validationFailed([{ field: 'reason', message: 'size must be between 0 and 255' }])
  anyReview(reviewId)
  moderated.set(reviewId, reason.trim())
  return anyReview(reviewId)
}

export function showReview(reviewId: number): Review {
  anyReview(reviewId)
  moderated.set(reviewId, null)
  return anyReview(reviewId)
}

/** The @Valid rules shared by both requests: orderId, rating 1-5, comment length. */
function checkRatingRequest(body: { orderId?: number; rating?: number; comment?: string | null }, maxComment: number): void {
  const errors: ApiErrorDetail[] = []
  if (!body.orderId) errors.push({ field: 'orderId', message: 'must not be null' })
  if (body.rating === undefined || body.rating === null) errors.push({ field: 'rating', message: 'must not be null' })
  else if (body.rating < 1) errors.push({ field: 'rating', message: 'must be greater than or equal to 1' })
  else if (body.rating > 5) errors.push({ field: 'rating', message: 'must be less than or equal to 5' })
  if (body.comment && body.comment.length > maxComment) errors.push({ field: 'comment', message: `size must be between 0 and ${maxComment}` })
  if (errors.length > 0) throw validationFailed(errors)
}

const cleanComment = (comment: string | null | undefined) => comment?.trim() || null

// ---------- Delivery ratings (delivery-service) ----------

/** In the mock an order has a delivery once a partner was assigned; another customer's order has none. */
function orderWithDelivery(customerUserId: number, orderId: number): Order {
  const notFound = new MockApiError(404, `No delivery found for order ${orderId}`)
  let order: Order
  try {
    order = getOrder(customerUserId, orderId)
  } catch {
    throw notFound
  }
  if (!order.deliveryPartnerName) throw notFound
  return order
}

export function getDeliveryRating(customerUserId: number, orderId: number): DeliveryRating {
  const order = orderWithDelivery(customerUserId, orderId)
  const rated = deliveryRatings.get(orderId)
  return { orderId, partnerName: order.deliveryPartnerName, rating: rated?.rating ?? null, comment: rated?.comment ?? null, ratedAt: rated?.ratedAt ?? null }
}

export function rateDelivery(customerUserId: number, body: Partial<DeliveryRatingRequest>): DeliveryRating {
  checkRatingRequest(body, 500)
  const order = orderWithDelivery(customerUserId, body.orderId!)
  if (order.status !== 'DELIVERED') throw new MockApiError(400, `Only a delivered order can be rated (this delivery is ${order.status})`)
  if (deliveryRatings.has(order.id)) throw new MockApiError(400, 'You have already rated this delivery')
  deliveryRatings.set(order.id, { rating: body.rating!, comment: cleanComment(body.comment), ratedAt: new Date().toISOString() })
  return getDeliveryRating(customerUserId, order.id)
}

// ---------- Product reviews (product-service) ----------

/** ProductReview.publicName: "Priya Sharma" -> "Priya S." */
function publicName(fullName: string): string {
  const parts = fullName.trim().split(/\s+/)
  return parts.length === 1 ? parts[0] : `${parts[0]} ${parts[parts.length - 1].charAt(0)}.`
}

export function writeReview(customerUserId: number, productId: number, body: Partial<ReviewRequest>): Review {
  checkRatingRequest(body, 1000)
  const order = getOrder(customerUserId, body.orderId!) // another customer's order: 404, like one that does not exist
  if (order.status !== 'DELIVERED') throw new MockApiError(400, 'You can review the products of an order once it is delivered')
  if (!order.items.some((item) => item.productId === productId)) throw new MockApiError(400, `Product ${productId} is not part of order ${order.id}`)
  if (writtenReviews.some((review) => review.orderId === order.id && review.productId === productId)) {
    throw new MockApiError(409, `You have already reviewed this product for order ${order.id}`)
  }
  const review: Review = {
    id: nextReviewId++,
    productId,
    storeId: order.storeId,
    orderId: order.id,
    reviewerName: publicName(order.deliveryAddress.contactName),
    rating: body.rating!,
    comment: cleanComment(body.comment),
    status: 'VISIBLE',
    hiddenReason: null,
    createdAt: new Date().toISOString(),
  }
  writtenReviews.push(review)
  return review
}

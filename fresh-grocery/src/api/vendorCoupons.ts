import { apiRequest } from './client'
import type { DiscountType, PageResponse, CouponDetails } from './types'

/** CouponRequest: create or change one of the store's coupons. */
export interface CouponRequest {
  /** 4-20 letters or digits, unique on the whole platform */
  code: string
  description: string
  discountType: DiscountType
  /** percent (at most 100) or rupees */
  discountValue: number
  /** PERCENTAGE only: the most it takes off */
  maxDiscountAmount: number | null
  /** the items total a cart needs (0 = no minimum) */
  minOrderAmount: number
  validFrom: string
  /** after validFrom */
  validUntil: string
  /** optional: how often it may be used in total */
  usageLimit: number | null
  /** how often one customer may use it (1 to 100) */
  perCustomerLimit: number
}

/*
 * The store's own coupons (cart-service), shown to customers next to the platform coupons.
 * Rule errors answer 400 ("validUntil must be after validFrom", "A percentage discount cannot be more than 100"),
 * a code in use 409 ("Coupon code SAVE20 is already taken. Choose another code.").
 */

/** Latest end date first; `page` starts at 0. */
export function getMyCoupons(page: number, size: number, signal?: AbortSignal): Promise<PageResponse<CouponDetails>> {
  return apiRequest<PageResponse<CouponDetails>>('/api/v1/vendor/coupons', { query: { page, size }, signal })
}

export function createCoupon(request: CouponRequest): Promise<CouponDetails> {
  return apiRequest<CouponDetails>('/api/v1/vendor/coupons', { method: 'POST', body: request })
}

export function updateCoupon(couponId: number, request: CouponRequest): Promise<CouponDetails> {
  return apiRequest<CouponDetails>(`/api/v1/vendor/coupons/${couponId}`, { method: 'PUT', body: request })
}

/** false = customers can no longer use it. */
export function setCouponActive(couponId: number, active: boolean): Promise<CouponDetails> {
  return apiRequest<CouponDetails>(`/api/v1/vendor/coupons/${couponId}/active`, { method: 'PATCH', body: { active } })
}

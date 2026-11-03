import { apiRequest } from './client'
import type { Coupon } from './types'

/**
 * Coupons customers can use right now.
 * Without a storeId: the platform coupons (valid in every store). With one: also that store's own coupons.
 */
export function getCoupons(storeId?: number, signal?: AbortSignal): Promise<Coupon[]> {
  return apiRequest<Coupon[]>('/api/v1/coupons', { query: { storeId }, signal })
}

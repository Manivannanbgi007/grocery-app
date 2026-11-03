import { apiRequest } from './client'
import type { AppNotification, Banner, CouponDetails, PageResponse } from './types'
import type { CouponRequest } from './vendorCoupons'

/*
 * Marketing and monitoring for admins: every coupon (cart-service; admins make the platform coupons, valid in every
 * store), the home page banners (platform-service) and the notifications that were sent (notification-service).
 */

/** All coupons, one store's (storeId) or the platform's only (platformOnly), newest first. */
export function searchCoupons(filter: { storeId?: number; platformOnly?: boolean; page: number }, size = 20, signal?: AbortSignal): Promise<PageResponse<CouponDetails>> {
  return apiRequest<PageResponse<CouponDetails>>('/api/v1/admin/coupons', { query: { ...filter, size }, signal })
}

/** A coupon valid in every store (the same request as a vendor's coupon). */
export function createPlatformCoupon(request: CouponRequest): Promise<CouponDetails> {
  return apiRequest<CouponDetails>('/api/v1/admin/coupons', { method: 'POST', body: request })
}

export function updatePlatformCoupon(couponId: number, request: CouponRequest): Promise<CouponDetails> {
  return apiRequest<CouponDetails>(`/api/v1/admin/coupons/${couponId}`, { method: 'PUT', body: request })
}

/** Switches any coupon on or off, also a store's. */
export function setAnyCouponActive(couponId: number, active: boolean): Promise<CouponDetails> {
  return apiRequest<CouponDetails>(`/api/v1/admin/coupons/${couponId}/active`, { method: 'PATCH', body: { active } })
}

/** Every banner, also the switched-off and the ended ones (live = customers see it now). */
export function getAllBanners(signal?: AbortSignal): Promise<Banner[]> {
  return apiRequest<Banner[]>('/api/v1/admin/banners', { signal })
}

/** BannerRequest: startsAt / endsAt are moments ("2026-11-01T00:00:00Z") or null for "always". */
export interface BannerRequest {
  title: string
  subtitle: string | null
  imageUrl: string
  linkUrl: string | null
  /** 1 to 100, lower = shown first */
  position: number
  startsAt: string | null
  endsAt: string | null
  active: boolean
}

export function createBanner(request: BannerRequest): Promise<Banner> {
  return apiRequest<Banner>('/api/v1/admin/banners', { method: 'POST', body: request })
}

export function updateBanner(bannerId: number, request: BannerRequest): Promise<Banner> {
  return apiRequest<Banner>(`/api/v1/admin/banners/${bannerId}`, { method: 'PUT', body: request })
}

export function deleteBanner(bannerId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/admin/banners/${bannerId}`, { method: 'DELETE' })
}

/** The query of the notification log; emailStatus FAILED finds the emails that did not go out. */
export interface AdminNotificationFilter {
  userId?: number
  type?: string
  emailStatus?: string
  /** 0-based */
  page: number
}

/** Every notification that was sent, newest first. */
export function searchNotifications(filter: AdminNotificationFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<AppNotification>> {
  return apiRequest<PageResponse<AppNotification>>('/api/v1/admin/notifications', { query: { ...filter, size }, signal })
}

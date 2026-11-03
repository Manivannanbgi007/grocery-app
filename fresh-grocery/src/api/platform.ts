import { apiRequest } from './client'
import type { Banner, PlatformSettings } from './types'

/** Delivery fee, free-delivery amount, minimum order value and support contact. */
export function getPlatformSettings(signal?: AbortSignal): Promise<PlatformSettings> {
  return apiRequest<PlatformSettings>('/api/v1/platform/settings', { signal })
}

/** The feature flags, e.g. { "PRODUCT_REVIEWS": true, "DELIVERY_SLOTS": false } (the super admin switches them). */
export function getFeatureFlags(signal?: AbortSignal): Promise<Record<string, boolean>> {
  return apiRequest<Record<string, boolean>>('/api/v1/platform/feature-flags', { signal })
}

/** The home page banners customers see right now, in position order. */
export function getBanners(signal?: AbortSignal): Promise<Banner[]> {
  return apiRequest<Banner[]>('/api/v1/banners', { signal })
}

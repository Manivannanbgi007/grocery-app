import { apiRequest } from './client'
import type { DayOfWeek, StoreDetails } from './types'

/** StoreDetailsRequest: what the vendor fills in to register the store, and to change it later. */
export interface StoreDetailsRequest {
  name: string
  description: string | null
  /** 10-digit Indian mobile number */
  phone: string
  email: string | null
  addressLine1: string
  addressLine2: string | null
  city: string
  state: string
  /** 6 digits, not starting with 0 */
  pincode: string
}

/** OpeningHoursRequest: one day; opensAt / closesAt like "07:00", not needed when closed. */
export interface OpeningHoursRequest {
  dayOfWeek: DayOfWeek
  opensAt: string | null
  closesAt: string | null
  closed: boolean
}

/*
 * The logged-in vendor's own store (store-service). These calls work with the vendor's user id, so they also work
 * before the store is approved (unlike the product and order calls, which need the store id in the login token).
 */

/** Fails with 404 "You have not registered a store yet". */
export function getMyStore(signal?: AbortSignal): Promise<StoreDetails> {
  return apiRequest<StoreDetails>('/api/v1/vendor/store', { signal })
}

/** The store then waits for an admin's approval. Fails with 409 when the vendor already has a store. */
export function registerStore(request: StoreDetailsRequest): Promise<StoreDetails> {
  return apiRequest<StoreDetails>('/api/v1/vendor/store', { method: 'POST', body: request })
}

/** A REJECTED store goes back for approval. */
export function updateMyStore(request: StoreDetailsRequest): Promise<StoreDetails> {
  return apiRequest<StoreDetails>('/api/v1/vendor/store', { method: 'PUT', body: request })
}

/** Replaces the whole week; days that are left out are closed. Fails with 400 e.g. "MONDAY: opensAt must be before closesAt". */
export function updateOpeningHours(days: OpeningHoursRequest[]): Promise<StoreDetails> {
  return apiRequest<StoreDetails>('/api/v1/vendor/store/opening-hours', { method: 'PUT', body: { days } })
}

/** Replaces the PIN codes the store delivers to (1 to 50). */
export function updateDeliveryAreas(pincodes: string[]): Promise<StoreDetails> {
  return apiRequest<StoreDetails>('/api/v1/vendor/store/delivery-areas', { method: 'PUT', body: { pincodes } })
}

/** false = pause new orders (customers then cannot order from the store). */
export function setAcceptingOrders(acceptingOrders: boolean): Promise<StoreDetails> {
  return apiRequest<StoreDetails>('/api/v1/vendor/store/accepting-orders', { method: 'PATCH', body: { acceptingOrders } })
}

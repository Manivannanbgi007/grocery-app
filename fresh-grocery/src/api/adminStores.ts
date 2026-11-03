import { apiRequest } from './client'
import type { PageResponse, StoreDetails, StoreStatus } from './types'

/*
 * Every store of the platform (store-service), for admins: the approval queue and the store's status.
 * Approving publishes StoreApproved; auth-service then puts the store id into the vendor's next token, and the vendor
 * panel gets one by itself (a token refresh, see StoreGate) the next time the vendor opens it.
 */

/** The query of the store list. Leave a field out to not filter on it. */
export interface AdminStoreFilter {
  status?: StoreStatus
  city?: string
  /** part of the store's name */
  keyword?: string
  /** 0-based */
  page: number
}

/** Stores, oldest first (the approval queue is worked through in the order the stores registered). */
export function searchStores(filter: AdminStoreFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<StoreDetails>> {
  return apiRequest<PageResponse<StoreDetails>>('/api/v1/admin/stores', { query: { ...filter, size }, signal })
}

export function getStore(storeId: number, signal?: AbortSignal): Promise<StoreDetails> {
  return apiRequest<StoreDetails>(`/api/v1/admin/stores/${storeId}`, { signal })
}

/** PENDING_APPROVAL → ACTIVE: customers can order from it once the vendor has stock. */
export function approveStore(storeId: number): Promise<StoreDetails> {
  return apiRequest<StoreDetails>(`/api/v1/admin/stores/${storeId}/approve`, { method: 'POST' })
}

/** PENDING_APPROVAL → REJECTED; the vendor sees the reason and can change the details, which sends it back to the queue. */
export function rejectStore(storeId: number, reason: string): Promise<StoreDetails> {
  return apiRequest<StoreDetails>(`/api/v1/admin/stores/${storeId}/reject`, { method: 'POST', body: { reason } })
}

/** ACTIVE → DISABLED: the store disappears for customers; the vendor sees the reason. */
export function disableStore(storeId: number, reason: string): Promise<StoreDetails> {
  return apiRequest<StoreDetails>(`/api/v1/admin/stores/${storeId}/disable`, { method: 'POST', body: { reason } })
}

/** DISABLED → ACTIVE */
export function enableStore(storeId: number): Promise<StoreDetails> {
  return apiRequest<StoreDetails>(`/api/v1/admin/stores/${storeId}/enable`, { method: 'POST' })
}

import { apiRequest } from './client'
import type { PageResponse, Store } from './types'

/** One approved store; 404 "Store not found with id: 9" for an unknown store or one that is not active. */
export function getStore(storeId: number, signal?: AbortSignal): Promise<Store> {
  return apiRequest<Store>(`/api/v1/stores/${storeId}`, { signal })
}

/** Approved stores (the list is small, so one page of 100 holds all of them). */
export function getStores(signal?: AbortSignal): Promise<PageResponse<Store>> {
  return apiRequest<PageResponse<Store>>('/api/v1/stores', { query: { size: 100 }, signal })
}

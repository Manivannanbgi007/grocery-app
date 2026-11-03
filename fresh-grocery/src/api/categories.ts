import { apiRequest } from './client'
import type { Category } from './types'

/** All active categories, in display order. */
export function getCategories(signal?: AbortSignal): Promise<Category[]> {
  return apiRequest<Category[]>('/api/v1/categories', { signal })
}

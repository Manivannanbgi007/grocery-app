import { apiRequest } from './client'
import type { Category, PageResponse, Product, ProductSort, ProductStatus, Review, SavedProduct } from './types'

/*
 * The catalog for admins. Reads come from product-read-service (Elasticsearch: every store's products, also hidden
 * and blocked ones, and every category); changes go to product-service and reach the reads a second or two later.
 */

/** The query of the product list. Leave a field out to not filter on it. */
export interface AdminProductFilter {
  q?: string
  storeId?: number
  categoryId?: number
  status?: ProductStatus
  lowStockOnly?: boolean
  sort?: ProductSort
  /** 0-based */
  page: number
}

export function searchAllProducts(filter: AdminProductFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<Product>> {
  return apiRequest<PageResponse<Product>>('/api/v1/admin/products', { query: { ...filter, size }, signal })
}

/** Any product of any store, also hidden, blocked or deleted ones. */
export function getAnyProduct(productId: number, signal?: AbortSignal): Promise<Product> {
  return apiRequest<Product>(`/api/v1/admin/products/${productId}`, { signal })
}

/** BLOCKED: hidden from customers, and the vendor cannot show it again. */
export function blockProduct(productId: number): Promise<SavedProduct> {
  return apiRequest<SavedProduct>(`/api/v1/admin/products/${productId}/block`, { method: 'POST' })
}

/** Back to ACTIVE. */
export function unblockProduct(productId: number): Promise<SavedProduct> {
  return apiRequest<SavedProduct>(`/api/v1/admin/products/${productId}/unblock`, { method: 'POST' })
}

/** Every category, also the hidden ones, in their display order. */
export function getAllCategories(signal?: AbortSignal): Promise<Category[]> {
  return apiRequest<Category[]>('/api/v1/admin/categories', { signal })
}

/** CategoryRequest: name (max 60), optional description, image address and position (lower = first). */
export interface CategoryRequest {
  name: string
  description: string | null
  imageUrl: string | null
  displayOrder: number | null
}

export function createCategory(request: CategoryRequest): Promise<Category> {
  return apiRequest<Category>('/api/v1/admin/categories', { method: 'POST', body: request })
}

export function updateCategory(categoryId: number, request: CategoryRequest): Promise<Category> {
  return apiRequest<Category>(`/api/v1/admin/categories/${categoryId}`, { method: 'PUT', body: request })
}

/** false hides the category (and its products from the category menus) in the shop. */
export function setCategoryActive(categoryId: number, active: boolean): Promise<Category> {
  return apiRequest<Category>(`/api/v1/admin/categories/${categoryId}/status`, { method: 'PATCH', body: { active } })
}

/** The query of the review list. */
export interface AdminReviewFilter {
  storeId?: number
  productId?: number
  rating?: number
  status?: Review['status']
  /** 0-based */
  page: number
}

/** Every review, newest first, also the hidden ones. */
export function searchReviews(filter: AdminReviewFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<Review>> {
  return apiRequest<PageResponse<Review>>('/api/v1/admin/reviews', { query: { ...filter, size }, signal })
}

/** Hides a review (offensive, fake, not about the product); the product's rating is worked out again without it. */
export function hideReview(reviewId: number, reason: string): Promise<Review> {
  return apiRequest<Review>(`/api/v1/admin/reviews/${reviewId}/hide`, { method: 'PATCH', body: { reason } })
}

export function showReview(reviewId: number): Promise<Review> {
  return apiRequest<Review>(`/api/v1/admin/reviews/${reviewId}/show`, { method: 'PATCH' })
}

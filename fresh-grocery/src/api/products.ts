import { apiRequest } from './client'
import type { PageResponse, Product, ProductSearchParams } from './types'

/** Search and filter the catalog. Every parameter is optional; page starts at 0. */
export function searchProducts(params: ProductSearchParams, signal?: AbortSignal): Promise<PageResponse<Product>> {
  return apiRequest<PageResponse<Product>>('/api/v1/products', { query: { ...params }, signal })
}

/**
 * The search box's suggestions while the customer types: at most `size` products, best first. Elasticsearch also finds
 * partly typed words ("toma" -> Tomato), words in any order and small typos (product-read-service). categoryId: optional.
 */
export function suggestProducts(text: string, categoryId: number | undefined, size: number, signal?: AbortSignal): Promise<Product[]> {
  return apiRequest<Product[]>('/api/v1/products/suggestions', { query: { q: text, categoryId, size }, signal })
}

/** One product. Fails with a 404 ApiError when it does not exist or is not for sale. */
export function getProduct(productId: number, signal?: AbortSignal): Promise<Product> {
  return apiRequest<Product>(`/api/v1/products/${productId}`, { signal })
}

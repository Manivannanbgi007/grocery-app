import { apiRequest } from './client'
import type { PageResponse, Product, ProductSort, ProductStatus, ProductUnit, SavedProduct, Stock } from './types'

/** ProductRequest: a product as the vendor enters it (create and update). */
export interface ProductRequest {
  categoryId: number
  name: string
  description: string | null
  brand: string | null
  /** the store's own code: 2-40 letters, digits or dashes, unique in the store */
  sku: string
  unit: ProductUnit
  /** with unit: 250 G = a 250 gram pack */
  unitQuantity: number
  /** price before the discount (MRP) */
  price: number
  /** 0 to 90 */
  discountPercentage: number
  /** GST, 0 to 28 */
  taxPercentage: number
}

export interface VendorProductFilter {
  q?: string
  status?: ProductStatus
  categoryId?: number
  lowStockOnly?: boolean
  sort?: ProductSort
  /** 0-based */
  page?: number
  size?: number
}

/*
 * The vendor's products. Reading goes to product-read-service (with the stock), writing to product-service:
 * a change reaches the read side a second or two later (CQRS). Stock lives in inventory-service.
 * All of it needs the store id in the login token.
 */

/** ACTIVE, INACTIVE and BLOCKED products of the store, with their stock (sorted by name by default). */
export function getMyProducts(filter: VendorProductFilter, signal?: AbortSignal): Promise<PageResponse<Product>> {
  return apiRequest<PageResponse<Product>>('/api/v1/vendor/products', { query: { ...filter }, signal })
}

/** 404 for another store's product, and for a moment after a new product is created (it is not in the read model yet). */
export function getMyProduct(productId: number, signal?: AbortSignal): Promise<Product> {
  return apiRequest<Product>(`/api/v1/vendor/products/${productId}`, { signal })
}

/** Fails with 409 "Your store already has a product with SKU ..." or 400 (field errors, inactive category). */
export function createProduct(request: ProductRequest): Promise<SavedProduct> {
  return apiRequest<SavedProduct>('/api/v1/vendor/products', { method: 'POST', body: request })
}

export function updateProduct(productId: number, request: ProductRequest): Promise<SavedProduct> {
  return apiRequest<SavedProduct>(`/api/v1/vendor/products/${productId}`, { method: 'PUT', body: request })
}

/** ACTIVE (customers see it) or INACTIVE (hidden). A product BLOCKED by an admin cannot be changed. */
export function setProductStatus(productId: number, status: 'ACTIVE' | 'INACTIVE'): Promise<SavedProduct> {
  return apiRequest<SavedProduct>(`/api/v1/vendor/products/${productId}/status`, { method: 'PATCH', body: { status } })
}

export function deleteProduct(productId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/vendor/products/${productId}`, { method: 'DELETE' })
}

/** JPEG, PNG or WEBP, at most 2 MB; at most 5 images per product. */
export function uploadProductImage(productId: number, image: File): Promise<SavedProduct> {
  const form = new FormData()
  form.append('image', image)
  return apiRequest<SavedProduct>(`/api/v1/vendor/products/${productId}/images`, { method: 'POST', body: form })
}

export function removeProductImage(productId: number, imageUrl: string): Promise<SavedProduct> {
  return apiRequest<SavedProduct>(`/api/v1/vendor/products/${productId}/images`, { method: 'DELETE', query: { imageUrl } })
}

/** 404 "Product 12 has no stock record in your store yet ..." until the stock is set once. */
export function getMyStock(productId: number, signal?: AbortSignal): Promise<Stock> {
  return apiRequest<Stock>(`/api/v1/vendor/inventory/${productId}`, { signal })
}

/**
 * Sets the counted number of units (the first call creates the stock record). lowStockThreshold left out = keep it
 * (5 for a new one). Fails with 400 when fewer than the units reserved for open orders.
 */
export function setStock(productId: number, quantityOnHand: number, lowStockThreshold: number | null): Promise<Stock> {
  return apiRequest<Stock>(`/api/v1/vendor/inventory/${productId}`, { method: 'PUT', body: { quantityOnHand, lowStockThreshold } })
}

/** New goods arrived: adds them to the units on hand. */
export function addStock(productId: number, quantity: number): Promise<Stock> {
  return apiRequest<Stock>(`/api/v1/vendor/inventory/${productId}/additions`, { method: 'POST', body: { quantity } })
}

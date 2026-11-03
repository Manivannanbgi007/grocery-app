import type { CategoryRequest } from '../api/adminCatalog'
import type { ProductRequest } from '../api/vendorProducts'
import type { ApiErrorDetail, Category, Product, ProductStatus, ProductUnit, SavedProduct, Stock } from '../api/types'
import { demoCategories } from './data/categories'
import { demoProductRows, LOW_STOCK_THRESHOLD, toProduct, type DemoProductRow } from './data/products'
import { MockApiError, validationFailed } from './mockApiError'

/*
 * The products and their stock for the mock API, kept in memory (they reset when the page reloads): editable copies of
 * the demo catalog (src/mocks/data/products.ts stays as it is: the backend seed SQL is generated from it).
 * The shop, the carts, the orders and the vendor panel all read these copies, so a vendor's change shows everywhere.
 * The rules copy product-service (ProductRequest, VendorProductServiceImpl) and inventory-service (Stock).
 */

const MAX_IMAGES = 5
const MAX_QUANTITY = 1_000_000
const UNITS: ProductUnit[] = ['KG', 'G', 'L', 'ML', 'PIECE', 'PACK', 'DOZEN']
const DAY = 24 * 60 * 60 * 1000

interface StockRecord {
  quantityOnHand: number
  reservedQuantity: number
  lowStockThreshold: number
  updatedAt: string
}

interface CatalogEntry {
  row: DemoProductRow
  imageUrls: string[]
  /** null until the vendor sets the stock of a new product */
  stock: StockRecord | null
  createdAt: string
  updatedAt: string
  deleted: boolean
}

function seedCatalog(): CatalogEntry[] {
  return demoProductRows.map((row) => {
    const added = new Date(Date.now() - row.addedDaysAgo * DAY).toISOString()
    return {
      row: { ...row },
      imageUrls: [],
      stock: { quantityOnHand: row.stock, reservedQuantity: 0, lowStockThreshold: LOW_STOCK_THRESHOLD, updatedAt: added },
      createdAt: added,
      updatedAt: added,
      deleted: false,
    }
  })
}

let catalog = seedCatalog()
let nextProductId = 200
/** Editable copies of the demo categories (admins add, change and hide them). */
let categories: Category[] = demoCategories.map((category) => ({ ...category }))
let nextCategoryId = 100

/** For tests: back to the demo catalog and categories. */
export function resetMockCatalog(): void {
  catalog = seedCatalog()
  nextProductId = 200
  categories = demoCategories.map((category) => ({ ...category }))
  nextCategoryId = 100
}

const roundMoney = (amount: number) => Math.round(amount * 100) / 100
const available = (stock: StockRecord | null) => (stock ? stock.quantityOnHand - stock.reservedQuantity : 0)

/** The read model (product-read-service): the product with its stock, images and rating. */
function toReadProduct(entry: CatalogEntry): Product {
  const units = available(entry.stock)
  const product = toProduct(entry.row)
  return {
    ...product,
    // An admin may have renamed the category
    categoryName: categories.find((category) => category.id === entry.row.categoryId)?.name ?? product.categoryName,
    imageUrls: entry.imageUrls,
    availableQuantity: units,
    inStock: units > 0,
    lowStock: entry.stock !== null && units <= entry.stock.lowStockThreshold,
    updatedAt: entry.updatedAt,
  }
}

/** The answer of product-service after a change (no stock, no rating). */
function toSavedProduct(entry: CatalogEntry): SavedProduct {
  const { availableQuantity: _available, inStock: _inStock, lowStock: _low, ratingAverage: _rating, ratingCount: _count, ...product } = toReadProduct(entry)
  return { ...product, createdAt: entry.createdAt, updatedAt: entry.updatedAt }
}

function toStock(entry: CatalogEntry, stock: StockRecord): Stock {
  const units = available(stock)
  return {
    productId: entry.row.id,
    storeId: entry.row.storeId,
    quantityOnHand: stock.quantityOnHand,
    reservedQuantity: stock.reservedQuantity,
    availableQuantity: units,
    lowStockThreshold: stock.lowStockThreshold,
    lowStock: units <= stock.lowStockThreshold,
    outOfStock: units <= 0,
    updatedAt: stock.updatedAt,
  }
}

// ---------- What the shop reads ----------

/** Every product that is not deleted (any status); the shop shows only ACTIVE ones. */
export function catalogProducts(): Product[] {
  return catalog.filter((entry) => !entry.deleted).map(toReadProduct)
}

/** One product (any status), or undefined when it does not exist or was deleted. */
export function catalogProduct(productId: number): Product | undefined {
  const entry = catalog.find((candidate) => candidate.row.id === productId && !candidate.deleted)
  return entry ? toReadProduct(entry) : undefined
}

const byDisplayOrder = (a: Category, b: Category) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0) || a.name.localeCompare(b.name)

/** The categories the shop shows (active ones), in their display order. */
export function activeCategories(): Category[] {
  return categories.filter((category) => category.active).sort(byDisplayOrder)
}

/** For "newest first". */
export function addedAt(productId: number): number {
  return Date.parse(catalog.find((entry) => entry.row.id === productId)?.createdAt ?? '1970-01-01T00:00:00Z')
}

// ---------- The vendor's products (product-service and product-read-service) ----------

function storeEntry(storeId: number, productId: number): CatalogEntry {
  const entry = catalog.find((candidate) => candidate.row.id === productId && candidate.row.storeId === storeId && !candidate.deleted)
  if (!entry) throw new MockApiError(404, `Product not found with id: ${productId}`)
  return entry
}

export interface StoreProductFilter {
  q: string | null
  status: ProductStatus | null
  categoryId: number | null
  lowStockOnly: boolean
}

/** The store's products (deleted ones are gone), sorted by name. */
export function listStoreProducts(storeId: number, filter: StoreProductFilter): Product[] {
  const words = (filter.q ?? '').toLowerCase().split(/\s+/).filter(Boolean)
  return catalog
    .filter((entry) => entry.row.storeId === storeId && !entry.deleted)
    .map(toReadProduct)
    .filter((product) => filter.status === null || product.status === filter.status)
    .filter((product) => filter.categoryId === null || product.categoryId === filter.categoryId)
    .filter((product) => !filter.lowStockOnly || product.lowStock)
    .filter((product) => words.every((word) => `${product.name} ${product.brand ?? ''} ${product.sku}`.toLowerCase().includes(word)))
    .sort((a, b) => a.name.localeCompare(b.name))
}

export function getStoreProduct(storeId: number, productId: number): Product {
  return toReadProduct(storeEntry(storeId, productId))
}

/** The @Valid rules of ProductRequest, the category check and the unique SKU per store. */
function checkProductRequest(storeId: number, body: Partial<ProductRequest>, productId: number | null): void {
  const errors: ApiErrorDetail[] = []
  const decimals = (value: number, places: number) => Math.round(value * 10 ** places) === value * 10 ** places
  if (body.categoryId === undefined || body.categoryId === null) errors.push({ field: 'categoryId', message: 'must not be null' })
  if (!body.name?.trim()) errors.push({ field: 'name', message: 'must not be blank' })
  else if (body.name.length > 150) errors.push({ field: 'name', message: 'size must be between 0 and 150' })
  if (body.description && body.description.length > 1000) errors.push({ field: 'description', message: 'size must be between 0 and 1000' })
  if (body.brand && body.brand.length > 60) errors.push({ field: 'brand', message: 'size must be between 0 and 60' })
  if (!/^[A-Za-z0-9-]{2,40}$/.test(body.sku ?? '')) errors.push({ field: 'sku', message: 'must be 2-40 letters, digits or dashes' })
  if (!body.unit || !UNITS.includes(body.unit)) errors.push({ field: 'unit', message: 'must not be null' })
  if (typeof body.unitQuantity !== 'number' || body.unitQuantity <= 0) errors.push({ field: 'unitQuantity', message: 'must be greater than 0' })
  else if (!decimals(body.unitQuantity, 3)) errors.push({ field: 'unitQuantity', message: 'numeric value out of bounds (<7 digits>.<3 digits> expected)' })
  if (typeof body.price !== 'number' || body.price < 0.01) errors.push({ field: 'price', message: 'must be greater than or equal to 0.01' })
  else if (!decimals(body.price, 2)) errors.push({ field: 'price', message: 'numeric value out of bounds (<8 digits>.<2 digits> expected)' })
  const discount = body.discountPercentage ?? 0
  if (discount < 0 || discount > 90) errors.push({ field: 'discountPercentage', message: 'must be less than or equal to 90.00' })
  if (typeof body.taxPercentage !== 'number' || body.taxPercentage < 0 || body.taxPercentage > 28) {
    errors.push({ field: 'taxPercentage', message: 'must be less than or equal to 28.00' })
  }
  if (errors.length > 0) throw validationFailed(errors)

  const category = categories.find((candidate) => candidate.id === body.categoryId)
  if (!category) throw new MockApiError(404, `Category not found with id: ${body.categoryId}`)
  if (!category.active) throw new MockApiError(400, `Category '${category.name}' is not active`)
  const sku = body.sku!.toUpperCase()
  const taken = catalog.some((entry) => entry.row.storeId === storeId && !entry.deleted && entry.row.id !== productId && entry.row.sku.toUpperCase() === sku)
  if (taken) throw new MockApiError(409, `Your store already has a product with SKU ${body.sku}`)
}

function copyRequest(body: ProductRequest, row: DemoProductRow): void {
  Object.assign(row, {
    categoryId: body.categoryId,
    name: body.name.trim(),
    description: body.description?.trim() ?? '',
    brand: body.brand?.trim() || null,
    sku: body.sku,
    unit: body.unit,
    unitQuantity: body.unitQuantity,
    price: roundMoney(body.price),
    discountPercentage: body.discountPercentage ?? 0,
    taxPercentage: body.taxPercentage,
  })
}

/** A new product is ACTIVE but has no stock yet (so it shows as sold out until the vendor sets it). */
export function createProduct(storeId: number, body: Partial<ProductRequest>): SavedProduct {
  checkProductRequest(storeId, body, null)
  const now = new Date().toISOString()
  const row: DemoProductRow = {
    id: nextProductId++,
    storeId,
    categoryId: 0,
    name: '',
    description: '',
    brand: null,
    sku: '',
    unit: 'PIECE',
    unitQuantity: 1,
    price: 0,
    discountPercentage: 0,
    taxPercentage: 0,
    stock: 0,
    addedDaysAgo: 0,
  }
  copyRequest(body as ProductRequest, row)
  const entry: CatalogEntry = { row, imageUrls: [], stock: null, createdAt: now, updatedAt: now, deleted: false }
  catalog.push(entry)
  return toSavedProduct(entry)
}

export function updateProduct(storeId: number, productId: number, body: Partial<ProductRequest>): SavedProduct {
  const entry = storeEntry(storeId, productId)
  checkProductRequest(storeId, body, productId)
  copyRequest(body as ProductRequest, entry.row)
  entry.updatedAt = new Date().toISOString()
  return toSavedProduct(entry)
}

/** Product.changeStatusByVendor: only ACTIVE or INACTIVE, and never for a product an admin blocked. */
export function setProductStatus(storeId: number, productId: number, status: unknown): SavedProduct {
  const entry = storeEntry(storeId, productId)
  if (status !== 'ACTIVE' && status !== 'INACTIVE') throw new MockApiError(400, 'A vendor can only set a product to ACTIVE or INACTIVE')
  if (entry.row.status === 'BLOCKED') throw new MockApiError(400, 'This product was blocked by an admin. Please contact support.')
  entry.row.status = status
  entry.updatedAt = new Date().toISOString()
  return toSavedProduct(entry)
}

export function deleteProduct(storeId: number, productId: number): void {
  storeEntry(storeId, productId).deleted = true
}

export function addImage(storeId: number, productId: number, image: { type: string; size: number; url: string }): SavedProduct {
  const entry = storeEntry(storeId, productId)
  if (image.size === 0) throw new MockApiError(400, 'The image file is empty')
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(image.type)) throw new MockApiError(400, 'Only JPEG, PNG or WEBP images are allowed')
  if (entry.imageUrls.length >= MAX_IMAGES) throw new MockApiError(400, `A product can have at most ${MAX_IMAGES} images`)
  entry.imageUrls = [...entry.imageUrls, image.url]
  entry.updatedAt = new Date().toISOString()
  return toSavedProduct(entry)
}

export function removeImage(storeId: number, productId: number, imageUrl: string): SavedProduct {
  const entry = storeEntry(storeId, productId)
  if (!entry.imageUrls.includes(imageUrl)) throw new MockApiError(404, `This product has no image ${imageUrl}`)
  entry.imageUrls = entry.imageUrls.filter((url) => url !== imageUrl)
  entry.updatedAt = new Date().toISOString()
  return toSavedProduct(entry)
}

// ---------- Stock (inventory-service) ----------

/** inventory-service asks the catalog first: another store's product looks like one that does not exist. */
function stockEntry(storeId: number, productId: number): CatalogEntry {
  const entry = catalog.find((candidate) => candidate.row.id === productId && candidate.row.storeId === storeId)
  if (!entry) throw new MockApiError(404, `Product ${productId} not found in your store. If you have just created it, try again in a few seconds.`)
  if (entry.deleted) throw new MockApiError(400, `Product ${productId} is deleted, so it cannot get stock`)
  return entry
}

function checkRange(value: unknown, field: string, min: number): number {
  if (typeof value !== 'number' || !Number.isInteger(value)) throw validationFailed([{ field, message: 'must not be null' }])
  if (value < min) throw validationFailed([{ field, message: `must be greater than or equal to ${min}` }])
  if (value > MAX_QUANTITY) throw validationFailed([{ field, message: `must be less than or equal to ${MAX_QUANTITY}` }])
  return value
}

export function getStock(storeId: number, productId: number): Stock {
  const entry = stockEntry(storeId, productId)
  if (!entry.stock) {
    throw new MockApiError(404, `Product ${productId} has no stock record in your store yet. Set its stock first with PUT /api/v1/vendor/inventory/${productId}`)
  }
  return toStock(entry, entry.stock)
}

/** The first call creates the stock record (alert at 5 unless given). */
export function setStock(storeId: number, productId: number, body: { quantityOnHand?: unknown; lowStockThreshold?: unknown }): Stock {
  const quantityOnHand = checkRange(body.quantityOnHand, 'quantityOnHand', 0)
  const threshold = body.lowStockThreshold === undefined || body.lowStockThreshold === null ? null : checkRange(body.lowStockThreshold, 'lowStockThreshold', 0)
  const entry = stockEntry(storeId, productId)
  const stock = entry.stock ?? { quantityOnHand: 0, reservedQuantity: 0, lowStockThreshold: LOW_STOCK_THRESHOLD, updatedAt: '' }
  if (quantityOnHand < stock.reservedQuantity) {
    throw new MockApiError(400, `Quantity on hand cannot be less than the ${stock.reservedQuantity} units reserved for open orders`)
  }
  stock.quantityOnHand = quantityOnHand
  if (threshold !== null) stock.lowStockThreshold = threshold
  stock.updatedAt = new Date().toISOString()
  entry.stock = stock
  return toStock(entry, stock)
}

export function addStock(storeId: number, productId: number, body: { quantity?: unknown }): Stock {
  const quantity = checkRange(body.quantity, 'quantity', 1)
  const entry = stockEntry(storeId, productId)
  if (!entry.stock) {
    throw new MockApiError(404, `Product ${productId} has no stock record in your store yet. Set its stock first with PUT /api/v1/vendor/inventory/${productId}`)
  }
  if (entry.stock.quantityOnHand + quantity > MAX_QUANTITY) throw new MockApiError(400, `Quantity on hand cannot be more than ${MAX_QUANTITY}`)
  entry.stock.quantityOnHand += quantity
  entry.stock.updatedAt = new Date().toISOString()
  return toStock(entry, entry.stock)
}

// ---------- The admin's catalog (AdminCatalogQueryController, AdminProductController, AdminCategoryController) ----------

export interface AdminProductQuery extends StoreProductFilter {
  storeId: number | null
}

/** Every store's products (not the deleted ones), sorted by name. */
export function searchAllProducts(filter: AdminProductQuery): Product[] {
  const storeIds = filter.storeId === null ? [...new Set(catalog.map((entry) => entry.row.storeId))] : [filter.storeId]
  return storeIds.flatMap((storeId) => listStoreProducts(storeId, filter)).sort((a, b) => a.name.localeCompare(b.name))
}

function anyEntry(productId: number): CatalogEntry {
  const entry = catalog.find((candidate) => candidate.row.id === productId && !candidate.deleted)
  if (!entry) throw new MockApiError(404, `Product not found with id: ${productId}`)
  return entry
}

export function getAnyProduct(productId: number): Product {
  return toReadProduct(anyEntry(productId))
}

/** Product.block: hidden from customers; the vendor cannot undo it. */
export function blockProduct(productId: number): SavedProduct {
  const entry = anyEntry(productId)
  if (entry.row.status === 'BLOCKED') throw new MockApiError(400, 'The product is already blocked')
  entry.row.status = 'BLOCKED'
  entry.updatedAt = new Date().toISOString()
  return toSavedProduct(entry)
}

export function unblockProduct(productId: number): SavedProduct {
  const entry = anyEntry(productId)
  if (entry.row.status !== 'BLOCKED') throw new MockApiError(400, `Only a BLOCKED product can be unblocked (current status: ${entry.row.status})`)
  entry.row.status = 'ACTIVE'
  entry.updatedAt = new Date().toISOString()
  return toSavedProduct(entry)
}

/** Every category, also the hidden ones, in their display order. */
export function allCategories(): Category[] {
  return [...categories].sort(byDisplayOrder)
}

/** SlugUtils.toSlug: "Dairy & Eggs" -> "dairy-eggs". */
const slugOf = (name: string) =>
  name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')

/** The @Valid rules of CategoryRequest and the unique name. */
function checkCategory(body: Partial<CategoryRequest>, categoryId: number | null): CategoryRequest {
  const errors: ApiErrorDetail[] = []
  if (!body.name?.trim()) errors.push({ field: 'name', message: 'must not be blank' })
  else if (body.name.length > 60) errors.push({ field: 'name', message: 'size must be between 0 and 60' })
  if (body.description && body.description.length > 255) errors.push({ field: 'description', message: 'size must be between 0 and 255' })
  if (body.displayOrder !== null && body.displayOrder !== undefined && body.displayOrder < 0) errors.push({ field: 'displayOrder', message: 'must be greater than or equal to 0' })
  if (errors.length > 0) throw validationFailed(errors)
  const name = body.name!.trim()
  if (categories.some((category) => category.id !== categoryId && category.name.toLowerCase() === name.toLowerCase())) {
    throw new MockApiError(409, `A category with this name already exists: ${body.name}`)
  }
  return body as CategoryRequest
}

function copyCategory(request: CategoryRequest, category: Category): void {
  category.name = request.name.trim()
  category.slug = slugOf(request.name)
  category.description = request.description?.trim() || null
  category.imageUrl = request.imageUrl?.trim() || null
  category.displayOrder = request.displayOrder ?? 0
}

export function createCategory(body: Partial<CategoryRequest>): Category {
  const request = checkCategory(body, null)
  const category: Category = { id: nextCategoryId++, name: '', slug: '', description: null, imageUrl: null, displayOrder: 0, active: true }
  copyCategory(request, category)
  categories.push(category)
  return category
}

function findCategory(categoryId: number): Category {
  const category = categories.find((candidate) => candidate.id === categoryId)
  if (!category) throw new MockApiError(404, `Category not found with id: ${categoryId}`)
  return category
}

export function updateCategory(categoryId: number, body: Partial<CategoryRequest>): Category {
  const category = findCategory(categoryId)
  copyCategory(checkCategory(body, categoryId), category)
  return category
}

export function setCategoryActive(categoryId: number, active: unknown): Category {
  if (typeof active !== 'boolean') throw validationFailed([{ field: 'active', message: 'must not be null' }])
  const category = findCategory(categoryId)
  category.active = active
  return category
}

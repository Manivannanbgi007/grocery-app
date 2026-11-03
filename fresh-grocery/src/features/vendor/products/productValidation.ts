import type { Product, ProductUnit } from '../../../api/types'
import type { ProductRequest } from '../../../api/vendorProducts'
import { removeEmpty, type FieldErrors } from '../../auth/validation'

/* The product form: what the vendor types, and the rules of product-service's ProductRequest. */

export interface ProductValues {
  name: string
  /** the category id as text ("" = none chosen) */
  categoryId: string
  brand: string
  sku: string
  description: string
  unit: ProductUnit
  unitQuantity: string
  price: string
  discountPercentage: string
  taxPercentage: string
}

export const PRODUCT_FIELDS: readonly (keyof ProductValues)[] = [
  'name',
  'categoryId',
  'brand',
  'sku',
  'description',
  'unit',
  'unitQuantity',
  'price',
  'discountPercentage',
  'taxPercentage',
]

export const UNIT_LABELS: Record<ProductUnit, string> = {
  KG: 'Kilogram (kg)',
  G: 'Gram (g)',
  L: 'Litre (L)',
  ML: 'Millilitre (ml)',
  PIECE: 'Piece',
  PACK: 'Pack',
  DOZEN: 'Dozen',
}

/** The GST rates of India; a product saved with another rate keeps it as an extra choice. */
export const GST_RATES = [0, 5, 12, 18, 28]

export function newProductValues(): ProductValues {
  return { name: '', categoryId: '', brand: '', sku: '', description: '', unit: 'KG', unitQuantity: '1', price: '', discountPercentage: '0', taxPercentage: '0' }
}

export function productValuesOf(product: Product): ProductValues {
  return {
    name: product.name,
    categoryId: String(product.categoryId),
    brand: product.brand ?? '',
    sku: product.sku,
    description: product.description ?? '',
    unit: product.unit,
    unitQuantity: String(product.unitQuantity),
    price: String(product.price),
    discountPercentage: String(product.discountPercentage),
    taxPercentage: String(product.taxPercentage),
  }
}

/** A positive number with at most `places` decimals ("12.5", "0.25"), or null. */
function decimal(text: string, places: number): number | null {
  const pattern = places === 2 ? /^\d+(\.\d{1,2})?$/ : /^\d+(\.\d{1,3})?$/
  return pattern.test(text.trim()) ? Number(text) : null
}

export function validateProduct(values: ProductValues): FieldErrors<keyof ProductValues> {
  const unitQuantity = decimal(values.unitQuantity, 3)
  const price = decimal(values.price, 2)
  const discount = values.discountPercentage.trim() === '' ? 0 : decimal(values.discountPercentage, 2)
  return removeEmpty({
    name: !values.name.trim() ? 'Please enter the product name.' : values.name.trim().length > 150 ? 'Please use at most 150 characters.' : undefined,
    categoryId: values.categoryId ? undefined : 'Please choose a category.',
    brand: values.brand.trim().length > 60 ? 'Please use at most 60 characters.' : undefined,
    sku: /^[A-Za-z0-9-]{2,40}$/.test(values.sku.trim()) ? undefined : 'Use 2 to 40 letters, digits or dashes (your own product code, e.g. FM-VEG-020).',
    description: values.description.trim().length > 1000 ? 'Please use at most 1000 characters.' : undefined,
    unitQuantity: unitQuantity !== null && unitQuantity > 0 && unitQuantity < 10_000_000 ? undefined : 'Enter the pack size, e.g. 500 (up to 3 decimals).',
    price: price !== null && price >= 0.01 && price < 100_000_000 ? undefined : 'Enter the price in rupees, e.g. 45 or 45.50.',
    discountPercentage: discount !== null && discount <= 90 ? undefined : 'Enter a discount from 0 to 90 %.',
  })
}

export function toProductRequest(values: ProductValues): ProductRequest {
  return {
    categoryId: Number(values.categoryId),
    name: values.name.trim(),
    description: values.description.trim() || null,
    brand: values.brand.trim() || null,
    sku: values.sku.trim(),
    unit: values.unit,
    unitQuantity: Number(values.unitQuantity),
    price: Number(values.price),
    discountPercentage: values.discountPercentage.trim() === '' ? 0 : Number(values.discountPercentage),
    taxPercentage: Number(values.taxPercentage),
  }
}

/** The price customers pay, calculated like product-service: price - round(price × discount / 100, 2). */
export function sellingPriceOf(price: number, discountPercentage: number): number {
  const discount = Math.round(price * discountPercentage) / 100
  return Math.round((price - discount) * 100) / 100
}

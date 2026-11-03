import type { ProductSearchParams, ProductSort } from '../../api/types'
import { formatPrice } from '../../utils/format'

/*
 * The shop's filters live in the URL (/shop?q=milk&category=3&page=2), so a filtered page can be shared,
 * survives a refresh, and the browser's Back button goes back to the previous filters.
 * This file turns the URL into filters and the filters into an API search.
 */

/** Products per page: 3 rows of 4 on a big screen. */
export const SHOP_PAGE_SIZE = 12

export const SORT_OPTIONS: Array<{ value: ProductSort; label: string }> = [
  { value: 'RELEVANCE', label: 'Best match' },
  { value: 'PRICE_LOW_TO_HIGH', label: 'Price: low to high' },
  { value: 'PRICE_HIGH_TO_LOW', label: 'Price: high to low' },
  { value: 'NAME', label: 'Name: A to Z' },
  { value: 'NEWEST', label: 'Newest first' },
  { value: 'RATING', label: 'Top rated' },
]

export interface ShopFilters {
  q?: string
  category?: number
  /** one store's products only */
  store?: number
  brand?: string
  minPrice?: number
  maxPrice?: number
  inStock: boolean
  sort: ProductSort
  /** 1-based in the URL and on the buttons; the API counts from 0 */
  page: number
}

/** A positive whole number from the URL, or undefined ("abc", "-3" and "0" are ignored). */
function positiveInt(value: string | null): number | undefined {
  const number = Number(value)
  return value && Number.isInteger(number) && number > 0 ? number : undefined
}

/** A price from the URL: 0 or more, or undefined. */
function price(value: string | null): number | undefined {
  const number = Number(value)
  return value && Number.isFinite(number) && number >= 0 ? number : undefined
}

/** Reads the filters from the URL. Wrong values (typed by hand, old links) fall back to the defaults instead of failing. */
export function parseShopFilters(params: URLSearchParams): ShopFilters {
  const sort = params.get('sort') as ProductSort | null
  return {
    q: params.get('q')?.trim() || undefined,
    category: positiveInt(params.get('category')),
    store: positiveInt(params.get('store')),
    brand: params.get('brand')?.trim() || undefined,
    minPrice: price(params.get('minPrice')),
    maxPrice: price(params.get('maxPrice')),
    inStock: params.get('inStock') === 'true',
    sort: SORT_OPTIONS.some((option) => option.value === sort) ? (sort as ProductSort) : 'RELEVANCE',
    page: positiveInt(params.get('page')) ?? 1,
  }
}

/** The GET /api/v1/products parameters for these filters. */
export function toProductSearch(filters: ShopFilters): ProductSearchParams {
  return {
    q: filters.q,
    categoryId: filters.category,
    storeId: filters.store,
    brand: filters.brand,
    minPrice: filters.minPrice,
    maxPrice: filters.maxPrice,
    inStockOnly: filters.inStock || undefined,
    sort: filters.sort,
    page: filters.page - 1,
    size: SHOP_PAGE_SIZE,
  }
}

/** True when anything narrows the list (the sort order and the page do not count). */
export function hasActiveFilters(filters: ShopFilters): boolean {
  return Boolean(filters.q || filters.category || filters.store || filters.brand || filters.minPrice !== undefined || filters.maxPrice !== undefined || filters.inStock)
}

/** The chosen price range as text ("₹100 – ₹150", "₹0 – ₹150", "₹100 – any"), or undefined when there is none. */
export function priceRangeLabel(filters: Pick<ShopFilters, 'minPrice' | 'maxPrice'>): string | undefined {
  if (filters.minPrice === undefined && filters.maxPrice === undefined) return undefined
  const from = filters.minPrice !== undefined ? formatPrice(filters.minPrice) : '₹0'
  const to = filters.maxPrice !== undefined ? formatPrice(filters.maxPrice) : 'any'
  return `${from} – ${to}`
}

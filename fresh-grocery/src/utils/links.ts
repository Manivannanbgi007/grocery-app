import type { ProductSort } from '../api/types'

/** The shop filters that can be put in a link (the shop page reads them back from the URL). */
export interface ShopLinkFilters {
  q?: string
  category?: number
  store?: number
  brand?: string
  minPrice?: number
  maxPrice?: number
  /** true = hide products that are out of stock */
  inStock?: boolean
  sort?: ProductSort
  /** 1-based, like the page numbers people see */
  page?: number
}

/**
 * A link to the shop page with filters. Default values are left out, so links stay short:
 *   { q: "milk", category: 3 }                 -> "/shop?q=milk&category=3"
 *   { brand: "Amul", sort: "NEWEST", page: 2 } -> "/shop?brand=Amul&sort=NEWEST&page=2"
 */
export function shopLink(filters: ShopLinkFilters = {}): string {
  const params = new URLSearchParams()
  const text = filters.q?.trim()
  if (text) params.set('q', text)
  if (filters.category) params.set('category', String(filters.category))
  if (filters.store) params.set('store', String(filters.store))
  if (filters.brand) params.set('brand', filters.brand)
  if (filters.minPrice !== undefined) params.set('minPrice', String(filters.minPrice))
  if (filters.maxPrice !== undefined) params.set('maxPrice', String(filters.maxPrice))
  if (filters.inStock) params.set('inStock', 'true')
  if (filters.sort && filters.sort !== 'RELEVANCE') params.set('sort', filters.sort)
  if (filters.page && filters.page > 1) params.set('page', String(filters.page))
  const query = params.toString()
  return query ? `/shop?${query}` : '/shop'
}

/**
 * Banners (and other admin-made links) point at backend-style paths. This turns them into pages of this app:
 *   "/categories/1" -> "/shop?category=1"   "/offers" -> "/#offers"   "/products/5" -> "/products/5"
 * Full web addresses ("https://...") are returned unchanged.
 */
export function appLink(linkUrl: string | null | undefined): string {
  if (!linkUrl) return '/shop'
  if (/^https?:\/\//.test(linkUrl)) return linkUrl

  const category = /^\/categories\/(\d+)$/.exec(linkUrl)
  if (category) return shopLink({ category: Number(category[1]) })
  if (linkUrl === '/offers') return '/#offers'
  return linkUrl
}

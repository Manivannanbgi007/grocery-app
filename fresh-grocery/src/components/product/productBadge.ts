import type { Product } from '../../api/types'
import type { BadgeTone } from '../ui/Badge'

export interface ProductBadgeInfo {
  label: string
  tone: BadgeTone
}

/** "Top rated" needs at least this average from at least two reviews. */
const TOP_RATED_AVERAGE = 4.5

/**
 * The one badge in the corner of a product card, picked from real data (most important first):
 * sold out -> discount % -> only a few left -> top rated -> none.
 */
export function productBadge(product: Product): ProductBadgeInfo | null {
  if (!product.inStock) return { label: 'Sold out', tone: 'neutral' }
  if (product.sellingPrice < product.price) return { label: `-${Math.round(product.discountPercentage)}%`, tone: 'brand' }
  if (product.lowStock) return { label: `Only ${product.availableQuantity} left`, tone: 'new' }
  if (product.ratingAverage !== null && product.ratingAverage >= TOP_RATED_AVERAGE && product.ratingCount >= 2) {
    return { label: 'Top rated', tone: 'hot' }
  }
  return null
}

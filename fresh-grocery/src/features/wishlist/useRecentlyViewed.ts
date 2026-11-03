import type { Product } from '../../api/types'
import { useLocalStorageList } from '../../hooks/useLocalStorageList'
import { toSavedProduct, type SavedProduct } from './useWishlist'

const RECENTLY_VIEWED_KEY = 'fresh-grocery.recently-viewed'
const MAX_RECENTLY_VIEWED = 8

/** The products opened last in this browser: newest first, no duplicates, at most 8 (shown on the wishlist page). */
export function useRecentlyViewed() {
  const list = useLocalStorageList<SavedProduct>(RECENTLY_VIEWED_KEY, MAX_RECENTLY_VIEWED, (product) => product.id)

  return {
    products: list.items,
    /** Puts the product first (moving it up if it was already in the list). */
    record: (product: Product) => list.add(toSavedProduct(product)),
    clear: list.clear,
  }
}

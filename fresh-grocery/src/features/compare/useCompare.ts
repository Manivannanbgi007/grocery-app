import type { Product } from '../../api/types'
import { useLocalStorageList } from '../../hooks/useLocalStorageList'
import { toSavedProduct, type SavedProduct } from '../wishlist/useWishlist'

const COMPARE_KEY = 'fresh-grocery.compare'
/** More columns do not fit next to each other on a laptop */
export const MAX_COMPARE_ITEMS = 4

export type CompareResult = 'added' | 'removed' | 'full'

/** The products chosen for the comparison, saved in this browser like the wishlist (oldest first, as in the table). */
export function useCompare() {
  const list = useLocalStorageList<SavedProduct>(COMPARE_KEY, MAX_COMPARE_ITEMS, (product) => product.id)
  // The list keeps the newest first; the table shows them in the order they were chosen
  const products = [...list.items].reverse()

  /** Adds the product, or removes it when it is in the list already. A full list does not take another one. */
  function toggle(product: Product): CompareResult {
    if (list.has(product.id)) {
      list.remove(product.id)
      return 'removed'
    }
    if (list.items.length >= MAX_COMPARE_ITEMS) return 'full'
    list.add(toSavedProduct(product))
    return 'added'
  }

  return {
    products,
    count: products.length,
    isInCompare: list.has,
    toggle,
    remove: list.remove,
    clear: list.clear,
  }
}

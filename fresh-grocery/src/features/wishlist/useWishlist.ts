import type { Product } from '../../api/types'
import { useLocalStorageList } from '../../hooks/useLocalStorageList'

/**
 * The part of a product the wishlist keeps in the browser: enough to show the product straight away.
 * The wishlist page loads the current price and stock from the API (prices change).
 */
export type SavedProduct = Pick<
  Product,
  'id' | 'storeId' | 'categoryId' | 'categoryName' | 'name' | 'unit' | 'unitQuantity' | 'price' | 'discountPercentage' | 'sellingPrice' | 'imageUrls' | 'ratingAverage' | 'ratingCount'
> & {
  /** when it was saved (ISO); missing in lists saved before the date was kept */
  addedAt?: string
}

export function toSavedProduct(product: Product): SavedProduct {
  return {
    addedAt: new Date().toISOString(),
    id: product.id,
    storeId: product.storeId,
    categoryId: product.categoryId,
    categoryName: product.categoryName,
    name: product.name,
    unit: product.unit,
    unitQuantity: product.unitQuantity,
    price: product.price,
    discountPercentage: product.discountPercentage,
    sellingPrice: product.sellingPrice,
    imageUrls: product.imageUrls,
    ratingAverage: product.ratingAverage,
    ratingCount: product.ratingCount,
  }
}

const WISHLIST_KEY = 'fresh-grocery.wishlist'
const MAX_WISHLIST_ITEMS = 50

/** The wishlist, saved in this browser (there is no wishlist API in the backend). */
export function useWishlist() {
  const list = useLocalStorageList<SavedProduct>(WISHLIST_KEY, MAX_WISHLIST_ITEMS, (product) => product.id)

  /** Adds the product, or removes it when it is already in the wishlist. Returns true when it was added. */
  function toggle(product: Product): boolean {
    if (list.has(product.id)) {
      list.remove(product.id)
      return false
    }
    list.add(toSavedProduct(product))
    return true
  }

  return {
    products: list.items,
    count: list.items.length,
    isInWishlist: list.has,
    toggle,
    remove: list.remove,
  }
}

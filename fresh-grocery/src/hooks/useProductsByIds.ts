import { useQueries } from '@tanstack/react-query'
import { isApiError } from '../api/client'
import { getProduct } from '../api/products'
import { queryKeys } from '../api/queryKeys'
import type { Product } from '../api/types'

/**
 * The current data (price, stock) of several products, one request per product (cached, shared with the product page).
 * Used by the cart (stock limits) and the wishlist (prices may have changed since the product was saved).
 *
 * @returns products: id -> product; missing: ids the shop no longer sells (404)
 */
export function useProductsByIds(productIds: number[]) {
  const results = useQueries({
    queries: productIds.map((productId) => ({
      queryKey: queryKeys.product(productId),
      queryFn: ({ signal }: { signal: AbortSignal }) => getProduct(productId, signal),
    })),
  })

  const products = new Map<number, Product>()
  const missing = new Set<number>()
  productIds.forEach((productId, index) => {
    const result = results[index]
    if (result?.data) products.set(productId, result.data)
    else if (isApiError(result?.error, 404)) missing.add(productId)
  })
  return { products, missing }
}

import { useQuery } from '@tanstack/react-query'
import { getProduct } from '../api/products'
import { queryKeys } from '../api/queryKeys'

/** One product with its current price and stock. A 404 (not found / not for sale) arrives as an ApiError in `error`. */
export function useProduct(productId: number) {
  return useQuery({
    queryKey: queryKeys.product(productId),
    queryFn: ({ signal }) => getProduct(productId, signal),
    enabled: Number.isInteger(productId) && productId > 0,
  })
}

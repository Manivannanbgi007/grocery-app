import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { searchProducts } from '../api/products'
import { queryKeys } from '../api/queryKeys'
import type { ProductSearchParams } from '../api/types'

/**
 * One page of products for these filters.
 * keepPreviousData: while the next page loads, the current one stays on screen instead of flashing empty.
 */
export function useProductSearch(params: ProductSearchParams, enabled = true) {
  return useQuery({
    queryKey: queryKeys.products(params),
    queryFn: ({ signal }) => searchProducts(params, signal),
    placeholderData: keepPreviousData,
    enabled,
  })
}

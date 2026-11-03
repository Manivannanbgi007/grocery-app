import type { ProductSearchParams } from '../api/types'
import { biggestDiscounts } from '../utils/discount'
import { useProductSearch } from './useProductSearch'

/**
 * The API cannot filter or sort by discount, so this loads up to 100 in-stock products and picks the discounted ones
 * in the browser, biggest discount first. Used by "Deals of the Day", "More deals" and the "on sale" pop-up
 * (they share one request: same filters = same React Query cache entry).
 */
const DEALS_SEARCH: ProductSearchParams = { inStockOnly: true, size: 100 }

export function useDeals(enabled = true) {
  const query = useProductSearch(DEALS_SEARCH, enabled)
  return {
    deals: biggestDiscounts(query.data?.content ?? []),
    isPending: query.isPending,
    isError: query.isError,
    error: query.error,
    refetch: query.refetch,
  }
}

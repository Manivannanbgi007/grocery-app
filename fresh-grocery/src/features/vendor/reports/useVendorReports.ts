import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query'
import { queryKeys } from '../../../api/queryKeys'
import { getMyProduct } from '../../../api/vendorProducts'
import { getDailySales, getMyReviews, getTopProducts } from '../../../api/vendorReports'
import { useAuth } from '../../auth/authContext'

export const VENDOR_REVIEWS_PER_PAGE = 20

/** The store's sales per day from `from` to `to` (report-service; a new order shows up there a moment later). */
export function useDailySales(from: string, to: string) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorSales(vendor?.id ?? 0, from, to),
    queryFn: ({ signal }) => getDailySales(from, to, signal),
    enabled: vendor !== null,
    placeholderData: keepPreviousData,
  })
}

export function useTopProducts(from: string, to: string, limit: number) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorTopProducts(vendor?.id ?? 0, from, to),
    queryFn: ({ signal }) => getTopProducts(from, to, limit, signal),
    enabled: vendor !== null,
    placeholderData: keepPreviousData,
  })
}

/** One page (1-based) of the reviews of the store's products, optionally only one star rating. */
export function useMyReviews(rating: number | undefined, page: number, size = VENDOR_REVIEWS_PER_PAGE) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorReviews(vendor?.id ?? 0, { rating, page }, size),
    queryFn: ({ signal }) => getMyReviews({ rating, page: page - 1, size }, signal),
    enabled: vendor !== null,
    placeholderData: keepPreviousData,
  })
}

/** The names of some of the store's products (also hidden ones), shared with the product pages' cache. */
export function useMyProductNames(productIds: number[]): Map<number, string> {
  const { vendor } = useAuth()
  const results = useQueries({
    queries: productIds.map((productId) => ({
      queryKey: queryKeys.vendorProduct(vendor?.id ?? 0, productId),
      queryFn: ({ signal }: { signal: AbortSignal }) => getMyProduct(productId, signal),
      enabled: vendor !== null,
    })),
  })
  const names = new Map<number, string>()
  productIds.forEach((productId, index) => {
    const name = results[index]?.data?.name
    if (name) names.set(productId, name)
  })
  return names
}

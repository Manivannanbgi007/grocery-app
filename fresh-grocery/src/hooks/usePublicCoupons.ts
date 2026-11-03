import { useQueries } from '@tanstack/react-query'
import { getCoupons } from '../api/coupons'
import { queryKeys } from '../api/queryKeys'
import type { Coupon } from '../api/types'
import { useStores } from './useStores'

/**
 * Every coupon customers can use right now: the platform coupons and each store's own coupons.
 *
 * GET /api/v1/coupons returns the platform coupons; with ?storeId=X it adds that store's coupons.
 * So this asks once without a store and once per store, and removes the duplicates (platform coupons come back every time).
 */
export function usePublicCoupons() {
  const { data: stores = [], isPending: storesPending } = useStores()
  const storeIds: Array<number | undefined> = [undefined, ...stores.map((store) => store.id)]

  const results = useQueries({
    queries: storeIds.map((storeId) => ({
      queryKey: queryKeys.coupons(storeId),
      queryFn: ({ signal }: { signal: AbortSignal }) => getCoupons(storeId, signal),
    })),
  })

  const byCode = new Map<string, Coupon>()
  for (const result of results) {
    for (const coupon of result.data ?? []) byCode.set(coupon.code, coupon)
  }
  // Platform coupons (valid everywhere) first, then the store coupons
  const coupons = [...byCode.values()].sort((a, b) => Number(a.storeId !== null) - Number(b.storeId !== null))

  const platformResult = results[0]
  return {
    coupons,
    isPending: storesPending || results.some((result) => result.isPending),
    isError: platformResult?.isError ?? false,
    error: platformResult?.error,
    refetch: () => results.forEach((result) => void result.refetch()),
  }
}

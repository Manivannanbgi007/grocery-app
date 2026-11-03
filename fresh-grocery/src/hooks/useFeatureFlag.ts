import { useQuery } from '@tanstack/react-query'
import { getFeatureFlags } from '../api/platform'
import { queryKeys } from '../api/queryKeys'

/** The website asks for the flags again after 5 minutes, so a switch by the super admin arrives soon. */
const FIVE_MINUTES = 5 * 60_000

/**
 * Whether a feature flag is on. While the flags load, or when they cannot be loaded, `fallback` counts, so a feature
 * that is on today does not flicker or disappear when platform-service is down.
 * Used: PRODUCT_REVIEWS (the "review this product" form of delivered orders).
 */
export function useFeatureFlag(key: string, fallback = true): boolean {
  const { data } = useQuery({
    queryKey: queryKeys.featureFlags,
    queryFn: ({ signal }) => getFeatureFlags(signal),
    staleTime: FIVE_MINUTES,
  })
  return data?.[key] ?? fallback
}

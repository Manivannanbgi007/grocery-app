import { QueryClient } from '@tanstack/react-query'
import { retryDelay, retryOnce } from './retry'

/** How long fetched data counts as fresh; within this time React Query reuses it without asking the server again. */
const ONE_MINUTE = 60 * 1000

/** For data that rarely changes (categories, stores, platform settings). */
export const TEN_MINUTES = 10 * ONE_MINUTE

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: ONE_MINUTE,
        refetchOnWindowFocus: false,
        // Try a failed request once more, but not after a 4xx error other than 429 (see api/retry.ts)
        retry: retryOnce,
        retryDelay,
      },
    },
  })
}

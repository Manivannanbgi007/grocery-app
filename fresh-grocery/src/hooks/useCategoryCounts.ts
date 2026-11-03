import { useQueries } from '@tanstack/react-query'
import { searchProducts } from '../api/products'
import { TEN_MINUTES } from '../api/queryClient'
import { queryKeys } from '../api/queryKeys'
import type { Category } from '../api/types'

/**
 * The number of products in each category, as a Map of categoryId -> count.
 *
 * The API has no "count" endpoint, so this asks for one product per category (size=1) and reads
 * `totalElements` from the page. The counts are cached for 10 minutes.
 * Pass enabled=false to wait (e.g. until a menu is opened), so the requests are only sent when needed.
 */
export function useCategoryCounts(categories: Category[], enabled = true): Map<number, number> {
  const results = useQueries({
    queries: categories.map((category) => ({
      queryKey: queryKeys.categoryCount(category.id),
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        searchProducts({ categoryId: category.id, size: 1 }, signal).then((page) => page.totalElements),
      staleTime: TEN_MINUTES,
      enabled,
    })),
  })

  const counts = new Map<number, number>()
  categories.forEach((category, index) => {
    const count = results[index]?.data
    if (count !== undefined) counts.set(category.id, count)
  })
  return counts
}

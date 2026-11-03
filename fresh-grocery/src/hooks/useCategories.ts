import { useQuery } from '@tanstack/react-query'
import { getCategories } from '../api/categories'
import { TEN_MINUTES } from '../api/queryClient'
import { queryKeys } from '../api/queryKeys'

/** All categories. Many components use this; React Query sends one request and shares the result. */
export function useCategories() {
  return useQuery({
    queryKey: queryKeys.categories,
    queryFn: ({ signal }) => getCategories(signal),
    staleTime: TEN_MINUTES,
  })
}

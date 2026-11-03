import { useQuery } from '@tanstack/react-query'
import { getBanners } from '../api/platform'
import { TEN_MINUTES } from '../api/queryClient'
import { queryKeys } from '../api/queryKeys'

/** The home page banners that are live right now. */
export function useBanners() {
  return useQuery({
    queryKey: queryKeys.banners,
    queryFn: ({ signal }) => getBanners(signal),
    staleTime: TEN_MINUTES,
  })
}

import { useQuery } from '@tanstack/react-query'
import { getPlatformSettings } from '../api/platform'
import { TEN_MINUTES } from '../api/queryClient'
import { queryKeys } from '../api/queryKeys'

/** Delivery fee, free-delivery amount, minimum order value and support contact (shown in the header and footer). */
export function usePlatformSettings() {
  return useQuery({
    queryKey: queryKeys.platformSettings,
    queryFn: ({ signal }) => getPlatformSettings(signal),
    staleTime: TEN_MINUTES,
  })
}

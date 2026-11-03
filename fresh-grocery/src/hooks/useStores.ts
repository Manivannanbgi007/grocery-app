import { useQuery } from '@tanstack/react-query'
import { TEN_MINUTES } from '../api/queryClient'
import { queryKeys } from '../api/queryKeys'
import { getStore, getStores } from '../api/stores'

/** All approved stores. */
export function useStores() {
  return useQuery({
    queryKey: queryKeys.stores,
    queryFn: ({ signal }) => getStores(signal).then((page) => page.content),
    staleTime: TEN_MINUTES,
  })
}

/** One store (its page); "open now" changes during the day, so it is asked again after a minute. */
export function useStore(storeId: number) {
  return useQuery({
    queryKey: queryKeys.store(storeId),
    queryFn: ({ signal }) => getStore(storeId, signal),
    staleTime: 60_000,
  })
}

/** The name of a store ("Fresh Mart"), or undefined while the stores are loading. */
export function useStoreName(storeId: number | null | undefined): string | undefined {
  const { data: stores } = useStores()
  return stores?.find((store) => store.id === storeId)?.name
}

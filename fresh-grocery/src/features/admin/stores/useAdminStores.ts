import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approveStore, disableStore, enableStore, getStore, rejectStore, searchStores } from '../../../api/adminStores'
import { queryKeys } from '../../../api/queryKeys'
import type { StoreDetails, StoreStatus } from '../../../api/types'
import { useAuth } from '../../auth/authContext'

export const STORES_PER_PAGE = 20

/** One page (1-based) of the stores, filtered by status, name and city. */
export function useStores(filter: { status?: StoreStatus; keyword?: string; city?: string }, page: number, size = STORES_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminStores(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchStores(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** Every store's id and name (at most 100), for the store filters of the lists and to name a store by its id. */
export function useStoreOptions() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminStoreOptions(admin?.id ?? 0),
    queryFn: ({ signal }) => searchStores({ page: 0 }, 100, signal).then((page) => page.content.map((store) => ({ id: store.id, name: store.name }))),
    enabled: admin !== null,
    staleTime: 5 * 60_000,
  })
}

export function useStore(storeId: number) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminStore(admin?.id ?? 0, storeId),
    queryFn: ({ signal }) => getStore(storeId, signal),
    enabled: admin !== null,
  })
}

/**
 * Approve, reject, disable and enable a store. The answer is the changed store: it replaces the cached one, and the lists,
 * the counters and the dashboard are loaded again.
 */
export function useStoreActions(storeId: number) {
  const queryClient = useQueryClient()
  const { admin } = useAuth()
  const userId = admin?.id ?? 0

  function saved(store: StoreDetails) {
    queryClient.setQueryData(queryKeys.adminStore(userId, store.id), store)
    void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminStores(userId) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.adminDashboard(userId) })
  }

  return {
    approve: useMutation({ mutationFn: () => approveStore(storeId), onSuccess: saved }),
    reject: useMutation({ mutationFn: (reason: string) => rejectStore(storeId, reason), onSuccess: saved }),
    disable: useMutation({ mutationFn: (reason: string) => disableStore(storeId, reason), onSuccess: saved }),
    enable: useMutation({ mutationFn: () => enableStore(storeId), onSuccess: saved }),
  }
}

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../../api/queryKeys'
import { getCommissionReport, updateCommission } from '../../../../api/superAdmin'
import { useAuth } from '../../../auth/authContext'

/** Commission per store on the orders delivered from `from` to `to` (not asked for while `enabled` is false). */
export function useCommissionReport(from: string, to: string, enabled = true) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminCommissions(admin?.id ?? 0, from, to),
    queryFn: ({ signal }) => getCommissionReport(from, to, signal),
    enabled: enabled && admin?.role === 'SUPER_ADMIN',
    placeholderData: keepPreviousData,
  })
}

/** Sets a store's commission; the answer replaces the cached store and the store lists are loaded again. */
export function useUpdateCommission(storeId: number) {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0
  return useMutation({
    mutationFn: (percentage: number) => updateCommission(storeId, percentage),
    onSuccess: (store) => {
      queryClient.setQueryData(queryKeys.adminStore(userId, storeId), store)
      void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminStores(userId) })
    },
  })
}

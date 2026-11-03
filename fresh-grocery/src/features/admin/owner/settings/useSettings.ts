import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../../api/queryKeys'
import { getSettings, updateSettings } from '../../../../api/superAdmin'
import { useAuth } from '../../../auth/authContext'

export function useSettings() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminSettings(admin?.id ?? 0),
    queryFn: ({ signal }) => getSettings(signal),
    enabled: admin?.role === 'SUPER_ADMIN',
  })
}

/** Saves the settings; the answer replaces the cached ones, and the shop's own copy (header, cart hints) is loaded again. */
export function useUpdateSettings() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: updateSettings,
    onSuccess: (settings) => {
      queryClient.setQueryData(queryKeys.adminSettings(admin?.id ?? 0), settings)
      void queryClient.invalidateQueries({ queryKey: queryKeys.platformSettings })
    },
  })
}

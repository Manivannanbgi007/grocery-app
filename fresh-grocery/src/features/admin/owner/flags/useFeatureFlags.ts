import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../../api/queryKeys'
import { createFeatureFlag, getFeatureFlags, switchFeatureFlag } from '../../../../api/superAdmin'
import type { FeatureFlag } from '../../../../api/types'
import { useAuth } from '../../../auth/authContext'

export function useFeatureFlags() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminFeatureFlags(admin?.id ?? 0),
    queryFn: ({ signal }) => getFeatureFlags(signal),
    enabled: admin?.role === 'SUPER_ADMIN',
  })
}

/** Adds a flag or switches one; the answer goes into the cached list at once (sorted by key, like the backend). */
export function useFeatureFlagChanges() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const key = queryKeys.adminFeatureFlags(admin?.id ?? 0)

  function putInList(flag: FeatureFlag) {
    queryClient.setQueryData<FeatureFlag[]>(key, (flags = []) => [...flags.filter((candidate) => candidate.key !== flag.key), flag].sort((a, b) => a.key.localeCompare(b.key)))
    // The shop's copy of the flags (useFeatureFlag) in this browser
    void queryClient.invalidateQueries({ queryKey: queryKeys.featureFlags })
  }

  const create = useMutation({ mutationFn: createFeatureFlag, onSuccess: putInList })
  const toggle = useMutation({ mutationFn: (flag: FeatureFlag) => switchFeatureFlag(flag.key, !flag.enabled), onSuccess: putInList })
  return { create, toggle }
}

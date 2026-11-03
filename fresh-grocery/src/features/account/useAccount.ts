import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { changePassword } from '../../api/auth'
import { getMyProfile, updateMyProfile, type UpdateProfileRequest } from '../../api/customers'
import { queryKeys } from '../../api/queryKeys'
import { retryWhileProfileIsCreated } from '../../api/retry'
import { useAuth } from '../auth/authContext'

/** The logged-in customer's profile (customer-service). */
export function useProfile() {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.profile(customer?.id ?? 0),
    queryFn: ({ signal }) => getMyProfile(signal),
    enabled: customer !== null,
    ...retryWhileProfileIsCreated,
  })
}

/** Saves the profile; the answer replaces the cached one. The caller shows the errors next to the fields. */
export function useUpdateProfile() {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateProfileRequest) => updateMyProfile(request),
    onSuccess: (profile) => queryClient.setQueryData(queryKeys.profile(customer?.id ?? 0), profile),
  })
}

export function useChangePassword() {
  return useMutation({ mutationFn: ({ currentPassword, newPassword }: { currentPassword: string; newPassword: string }) => changePassword(currentPassword, newPassword) })
}

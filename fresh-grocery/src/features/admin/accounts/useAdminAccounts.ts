import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getCustomerAddresses, getUser, searchCustomers, searchUsers, setUserStatus, type AdminUserFilter } from '../../../api/adminAccounts'
import { queryKeys } from '../../../api/queryKeys'
import { changeRole, createAdmin } from '../../../api/superAdmin'
import type { User, UserRole } from '../../../api/types'
import { useAuth } from '../../auth/authContext'

export const ACCOUNTS_PER_PAGE = 20

/** One page (1-based) of the accounts, filtered by role, status and name or email. */
export function useAccounts(filter: Omit<AdminUserFilter, 'page'>, page: number, size = ACCOUNTS_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminUsers(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchUsers(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

export function useAccount(accountId: number) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminUser(admin?.id ?? 0, accountId),
    queryFn: ({ signal }) => getUser(accountId, signal),
    enabled: admin !== null,
  })
}

/** Switches an account on or off; the answer replaces the cached account and the lists are loaded again. */
export function useSetAccountStatus(accountId: number) {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0
  return useMutation({
    mutationFn: (status: User['status']) => setUserStatus(accountId, status),
    onSuccess: (account) => {
      queryClient.setQueryData(queryKeys.adminUser(userId, accountId), account)
      void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminUsers(userId) })
    },
  })
}

/** Super admin: adds an admin account; the account lists are loaded again. */
export function useCreateAdmin() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createAdmin,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminUsers(admin?.id ?? 0) }),
  })
}

/** Super admin: gives an account another role; the answer replaces the cached account and the lists are loaded again. */
export function useChangeRole(accountId: number) {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0
  return useMutation({
    mutationFn: (role: Exclude<UserRole, 'SUPER_ADMIN'>) => changeRole(accountId, role),
    onSuccess: (account) => {
      queryClient.setQueryData(queryKeys.adminUser(userId, accountId), account)
      void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminUsers(userId) })
    },
  })
}

/**
 * The delivery profile of an account (customers have one; any account that signed up as a customer keeps it).
 * customer-service finds customers by name, email or phone, not by the user id, so this asks for the login email and
 * keeps the profile with the same user id (null: none, or not yet: it is made from the registration a moment later).
 */
export function useCustomerProfile(account: User | undefined) {
  const { admin } = useAuth()
  const email = account?.email ?? ''
  return useQuery({
    queryKey: queryKeys.adminCustomerByEmail(admin?.id ?? 0, email),
    queryFn: ({ signal }) => searchCustomers({ keyword: email, page: 0 }, 5, signal).then((page) => page.content.find((customer) => customer.userId === account?.id) ?? null),
    enabled: admin !== null && account !== undefined,
  })
}

export function useCustomerAddresses(customerId: number | undefined) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminCustomerAddresses(admin?.id ?? 0, customerId ?? 0),
    queryFn: ({ signal }) => getCustomerAddresses(customerId!, signal),
    enabled: admin !== null && customerId !== undefined,
  })
}

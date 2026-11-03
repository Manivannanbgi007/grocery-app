import { apiRequest } from './client'
import type { Address, CustomerProfile, PageResponse, User, UserRole } from './types'

/*
 * Accounts for admins: every login (auth-service) and the customers' delivery profiles and addresses
 * (customer-service). A disabled account cannot log in (403 "Your account is disabled"); a login that is already
 * open keeps working until its token runs out (at most 60 minutes).
 */

/** The query of the account list. keyword = part of the name or the email. */
export interface AdminUserFilter {
  role?: UserRole
  status?: User['status']
  keyword?: string
  /** 0-based */
  page: number
}

/** Accounts, oldest first. */
export function searchUsers(filter: AdminUserFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<User>> {
  return apiRequest<PageResponse<User>>('/api/v1/admin/users', { query: { ...filter, size }, signal })
}

export function getUser(userId: number, signal?: AbortSignal): Promise<User> {
  return apiRequest<User>(`/api/v1/admin/users/${userId}`, { signal })
}

/** Switches an account on or off. The backend refuses a SUPER_ADMIN account. */
export function setUserStatus(userId: number, status: User['status']): Promise<User> {
  return apiRequest<User>(`/api/v1/admin/users/${userId}/status`, { method: 'PATCH', body: { status } })
}

/** Customer profiles, oldest first; keyword = part of the name, email or phone. */
export function searchCustomers(filter: { keyword?: string; page: number }, size = 20, signal?: AbortSignal): Promise<PageResponse<CustomerProfile>> {
  return apiRequest<PageResponse<CustomerProfile>>('/api/v1/admin/customers', { query: { ...filter, size }, signal })
}

/** A customer's delivery addresses (by the customer id of the profile, not the user id). */
export function getCustomerAddresses(customerId: number, signal?: AbortSignal): Promise<Address[]> {
  return apiRequest<Address[]>(`/api/v1/admin/customers/${customerId}/addresses`, { signal })
}

import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getPaymentSummary, searchPayments, type AdminPaymentFilter } from '../../../api/adminOrders'
import { queryKeys } from '../../../api/queryKeys'
import { useAuth } from '../../auth/authContext'

export const PAYMENTS_PER_PAGE = 20

/** One page (1-based) of all payments, filtered by status, method and store. */
export function useAdminPayments(filter: Omit<AdminPaymentFilter, 'page'>, page: number, size = PAYMENTS_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminPayments(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchPayments(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** Money in and out of the payments created from `from` to `to`. */
export function usePaymentSummary(from: string, to: string) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminPaymentSummary(admin?.id ?? 0, from, to),
    queryFn: ({ signal }) => getPaymentSummary(from, to, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

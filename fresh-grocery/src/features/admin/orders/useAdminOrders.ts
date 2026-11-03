import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelOrder, downloadOrderInvoice, getOrder, searchOrders, type AdminOrderFilter } from '../../../api/adminOrders'
import { queryKeys } from '../../../api/queryKeys'
import type { Order } from '../../../api/types'
import { saveFile } from '../../../utils/download'
import { useAuth } from '../../auth/authContext'
import { pollInterval } from '../../orders/orderStatus'

export const ORDERS_PER_PAGE = 20

/** One page (1-based) of all orders, filtered by status, store and customer. */
export function useAdminOrders(filter: Omit<AdminOrderFilter, 'page'>, page: number, size = ORDERS_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminOrders(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchOrders(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** One order, asked for again while it moves (the same rule as the customer's order page). */
export function useAdminOrder(orderId: number) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminOrder(admin?.id ?? 0, orderId),
    queryFn: ({ signal }) => getOrder(orderId, signal),
    enabled: admin !== null,
    refetchInterval: (current) => pollInterval(current.state.data),
  })
}

/** Cancels a problem order. The changed order replaces the cached one; the lists and the money are loaded again. */
export function useCancelAsAdmin(orderId: number) {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0
  return useMutation({
    mutationFn: (reason: string) => cancelOrder(orderId, reason),
    onSuccess: (order: Order) => {
      queryClient.setQueryData(queryKeys.adminOrder(userId, orderId), order)
      void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminOrders(userId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminPayments(userId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminDeliveries(userId) })
    },
  })
}

export function useDownloadOrderInvoice(orderId: number) {
  return useMutation({
    mutationFn: () => downloadOrderInvoice(orderId),
    onSuccess: ({ blob, fileName }) => saveFile(blob, fileName ?? `invoice-order-${orderId}.pdf`),
  })
}

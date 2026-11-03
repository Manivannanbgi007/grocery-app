import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { assignDelivery, searchDeliveries, searchPartners } from '../../../api/adminDelivery'
import { queryKeys } from '../../../api/queryKeys'
import type { DeliveryStatus, KycStatus } from '../../../api/types'
import { useAuth } from '../../auth/authContext'

export const DELIVERIES_PER_PAGE = 20

/** One page (1-based) of the deliveries, newest first; no status = all of them. */
export function useAdminDeliveries(status: DeliveryStatus | undefined, page: number, size = DELIVERIES_PER_PAGE) {
  const { admin } = useAuth()
  const query = { status, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminDeliveries(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchDeliveries(query, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** Partners (at most 100), e.g. the approved ones to choose from, or all of them to name a partner by id. */
export function usePartnerList(kycStatus?: KycStatus) {
  const { admin } = useAuth()
  const filter = { kycStatus, page: 0 }
  return useQuery({
    queryKey: queryKeys.adminPartners(admin?.id ?? 0, filter, 100),
    queryFn: ({ signal }) => searchPartners(filter, 100, signal).then((page) => page.content),
    enabled: admin !== null,
  })
}

/** Gives a waiting delivery to a partner; the lists, the counters and the order are loaded again. */
export function useAssignDelivery(deliveryId: number) {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0
  return useMutation({
    mutationFn: (partnerUserId: number) => assignDelivery(deliveryId, partnerUserId),
    onSuccess: (delivery) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminDeliveries(userId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.adminOrder(userId, delivery.orderId) })
    },
  })
}

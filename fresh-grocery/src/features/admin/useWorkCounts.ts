import { useQuery } from '@tanstack/react-query'
import { searchDeliveries, searchPartners } from '../../api/adminDelivery'
import { searchStores } from '../../api/adminStores'
import { queryKeys } from '../../api/queryKeys'
import type { DeliveryStatus, KycStatus, StoreStatus } from '../../api/types'
import { useAuth } from '../auth/authContext'

/*
 * The work that waits for the platform team, as numbers for the menu and the dashboard. Each number is the total of a
 * one-item page of the list. They are asked for again every minute (admins get no notifications).
 */

const REFRESH_EVERY = 60_000

/** How many stores have this status (PENDING_APPROVAL = the approval queue). */
export function useStoreCount(status: StoreStatus) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminStoreCount(admin?.id ?? 0, status),
    queryFn: ({ signal }) => searchStores({ status, page: 0 }, 1, signal).then((page) => page.totalElements),
    enabled: admin !== null,
    refetchInterval: REFRESH_EVERY,
  })
}

/** How many delivery partners have this KYC status (PENDING = waiting for a review). */
export function usePartnerCount(kycStatus: KycStatus) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminPartnerCount(admin?.id ?? 0, kycStatus),
    queryFn: ({ signal }) => searchPartners({ kycStatus, page: 0 }, 1, signal).then((page) => page.totalElements),
    enabled: admin !== null,
    refetchInterval: REFRESH_EVERY,
  })
}

/** How many deliveries have this status (WAITING_FOR_PARTNER = nobody took it). */
export function useDeliveryCount(status: DeliveryStatus) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminDeliveryCount(admin?.id ?? 0, status),
    queryFn: ({ signal }) => searchDeliveries({ status, page: 0 }, 1, signal).then((page) => page.totalElements),
    enabled: admin !== null,
    refetchInterval: REFRESH_EVERY,
  })
}

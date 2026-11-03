import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { approvePartner, createZone, getZones, rejectPartner, searchPartners, updateZone, type ZoneRequest } from '../../../api/adminDelivery'
import { queryKeys } from '../../../api/queryKeys'
import type { KycStatus } from '../../../api/types'
import { useAuth } from '../../auth/authContext'

export const PARTNERS_PER_PAGE = 20

/** One page (1-based) of the delivery partners, optionally one KYC status. */
export function useAdminPartners(kycStatus: KycStatus | undefined, page: number, size = PARTNERS_PER_PAGE) {
  const { admin } = useAuth()
  const filter = { kycStatus, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminPartners(admin?.id ?? 0, filter, size),
    queryFn: ({ signal }) => searchPartners(filter, size, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

/** Approves or rejects a partner's KYC; the lists, the counters and the dashboard are loaded again. */
export function useKycReview(partnerUserId: number) {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0
  const reviewed = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminPartners(userId) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.adminDashboard(userId) })
  }
  return {
    approve: useMutation({ mutationFn: () => approvePartner(partnerUserId), onSuccess: reviewed }),
    reject: useMutation({ mutationFn: (reason: string) => rejectPartner(partnerUserId, reason), onSuccess: reviewed }),
  }
}

export function useZones() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminZones(admin?.id ?? 0),
    queryFn: ({ signal }) => getZones(signal),
    enabled: admin !== null,
  })
}

/** Adds or changes a zone; the zones and the partners (they show their zone's name) are loaded again. */
export function useZoneChanges() {
  const { admin } = useAuth()
  const queryClient = useQueryClient()
  const userId = admin?.id ?? 0
  const saved = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.adminZones(userId) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.allAdminPartners(userId) })
  }
  return {
    create: useMutation({ mutationFn: (request: ZoneRequest) => createZone(request), onSuccess: saved }),
    update: useMutation({ mutationFn: ({ zoneId, request }: { zoneId: number; request: ZoneRequest }) => updateZone(zoneId, request), onSuccess: saved }),
  }
}

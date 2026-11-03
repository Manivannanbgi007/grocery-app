import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useEffectEvent } from 'react'
import { getAvailability, getMyPartnerProfile, goOffline, goOnline, submitKyc, type KycRequest } from '../../api/delivery'
import { queryKeys } from '../../api/queryKeys'
import { retryWhileProfileIsCreated } from '../../api/retry'
import type { Availability } from '../../api/types'
import { useAuth } from '../auth/authContext'

/** "Go online" lasts 30 minutes; the panel goes online again this long before it runs out. */
const RENEW_BEFORE_MS = 5 * 60_000

/** The logged-in partner's profile and KYC. Right after the registration it does not exist for a moment (404): ask again. */
export function usePartnerProfile() {
  const { partner } = useAuth()
  return useQuery({
    queryKey: queryKeys.deliveryProfile(partner?.id ?? 0),
    queryFn: ({ signal }) => getMyPartnerProfile(signal),
    enabled: partner !== null,
    ...retryWhileProfileIsCreated,
  })
}

/** Sends the KYC; the answer (the profile, now PENDING) replaces the cached one. */
export function useSubmitKyc() {
  const { partner } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: KycRequest) => submitKyc(request),
    onSuccess: (profile) => queryClient.setQueryData(queryKeys.deliveryProfile(partner?.id ?? 0), profile),
  })
}

/** Online or not (asked again every minute: it ends by itself after 30 minutes when nobody renews it). */
export function useAvailability(enabled: boolean) {
  const { partner } = useAuth()
  return useQuery({
    queryKey: queryKeys.deliveryAvailability(partner?.id ?? 0),
    queryFn: ({ signal }) => getAvailability(signal),
    enabled: enabled && partner !== null,
    refetchInterval: 60_000,
  })
}

/** Going online or offline. Going online may bring a waiting delivery at once, so the task lists are loaded again. */
export function useAvailabilityChanges() {
  const { partner } = useAuth()
  const queryClient = useQueryClient()
  const userId = partner?.id ?? 0
  const saved = (availability: Availability) => {
    queryClient.setQueryData(queryKeys.deliveryAvailability(userId), availability)
    void queryClient.invalidateQueries({ queryKey: queryKeys.allDeliveryTasks(userId) })
  }
  return {
    online: useMutation({ mutationFn: goOnline, onSuccess: saved }),
    offline: useMutation({ mutationFn: goOffline, onSuccess: saved }),
    // The automatic renewal (useStayOnline) runs in the background: no app-wide loading sign for it
    renew: useMutation({ mutationFn: goOnline, onSuccess: saved, meta: { quiet: true } }),
  }
}

/** While the panel is open and the partner is online, goes online again 5 minutes before it would end. */
export function useStayOnline(availability: Availability | undefined) {
  const { renew: renewal } = useAvailabilityChanges()
  const renew = useEffectEvent(() => renewal.mutate())
  const onlineUntil = availability?.online ? availability.onlineUntil : null

  useEffect(() => {
    if (!onlineUntil) return
    const timer = setTimeout(renew, Math.max(0, Date.parse(onlineUntil) - Date.now() - RENEW_BEFORE_MS))
    return () => clearTimeout(timer)
  }, [onlineUntil])
}

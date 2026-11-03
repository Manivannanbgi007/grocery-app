import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { downloadProofPhoto, getEarnings, getMyDeliveries, getMyDelivery, reportIssue, takeDeliveryStep, uploadProof, type DeliveryStep } from '../../../api/delivery'
import { queryKeys } from '../../../api/queryKeys'
import type { Delivery, DeliveryIssue, DeliveryStatus } from '../../../api/types'
import { useAuth } from '../../auth/authContext'

/** While online, a new job can come any moment: the current tasks are asked for this often. */
const ONLINE_POLL_MS = 15_000
const FINISHED: DeliveryStatus[] = ['DELIVERED', 'RETURNED', 'CANCELLED', 'WAITING_FOR_PARTNER']
export const HISTORY_PER_PAGE = 20

/** The current tasks (assigned, accepted, on the way, failed until returned); asked for every 15 s while online. */
export function useCurrentTasks(online: boolean) {
  const { partner } = useAuth()
  return useQuery({
    queryKey: queryKeys.deliveryTasks(partner?.id ?? 0, true, 1, 10),
    queryFn: ({ signal }) => getMyDeliveries({ active: true, page: 0, size: 10 }, signal),
    enabled: partner !== null,
    refetchInterval: online ? ONLINE_POLL_MS : 60_000,
  })
}

/** One page (1-based) of the finished deliveries (delivered and returned), newest first. */
export function useFinishedTasks(page: number, size = HISTORY_PER_PAGE) {
  const { partner } = useAuth()
  return useQuery({
    queryKey: queryKeys.deliveryTasks(partner?.id ?? 0, false, page, size),
    queryFn: ({ signal }) => getMyDeliveries({ active: false, page: page - 1, size }, signal),
    enabled: partner !== null,
    placeholderData: keepPreviousData,
  })
}

/** One delivery; asked for again every 30 s until it is finished (the order can be cancelled before the pick-up). */
export function useTask(deliveryId: number) {
  const { partner } = useAuth()
  return useQuery({
    queryKey: queryKeys.deliveryTask(partner?.id ?? 0, deliveryId),
    queryFn: ({ signal }) => getMyDelivery(deliveryId, signal),
    enabled: partner !== null,
    refetchInterval: (current) => (current.state.data && FINISHED.includes(current.state.data.status) ? false : 30_000),
  })
}

/**
 * The partner's steps for one delivery, the problem report and the proof photo. The answer (the changed delivery)
 * replaces the cached one; the task lists and the earnings are loaded again (a delivery can bring the next job).
 */
export function useTaskActions(deliveryId: number) {
  const { partner } = useAuth()
  const queryClient = useQueryClient()
  const userId = partner?.id ?? 0
  const saved = (delivery: Delivery) => {
    queryClient.setQueryData(queryKeys.deliveryTask(userId, deliveryId), delivery)
    void queryClient.invalidateQueries({ queryKey: queryKeys.allDeliveryTasks(userId) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.allDeliveryEarnings(userId) })
  }
  return {
    step: useMutation({
      mutationFn: (step: DeliveryStep) => takeDeliveryStep(deliveryId, step),
      onSuccess: (delivery, step) => {
        if (step !== 'reject') return saved(delivery)
        // A rejected job is not the partner's any more
        queryClient.removeQueries({ queryKey: queryKeys.deliveryTask(userId, deliveryId) })
        void queryClient.invalidateQueries({ queryKey: queryKeys.allDeliveryTasks(userId) })
      },
    }),
    report: useMutation({ mutationFn: ({ issue, note }: { issue: DeliveryIssue; note: string | null }) => reportIssue(deliveryId, issue, note), onSuccess: saved }),
    proof: useMutation({ mutationFn: (photo: File) => uploadProof(deliveryId, photo), onSuccess: saved }),
  }
}

/**
 * The proof photo as an address an <img> can show. The backend's photo needs the login token, so it is downloaded and
 * shown from memory (a blob: address). A photo of the mock API is already a data: address.
 */
export function useProofPhoto(photoUrl: string | null) {
  return useQuery({
    queryKey: queryKeys.deliveryProofPhoto(photoUrl),
    queryFn: async () => (photoUrl!.startsWith('data:') ? photoUrl! : URL.createObjectURL((await downloadProofPhoto(photoUrl!)).blob)),
    enabled: photoUrl !== null,
    staleTime: Infinity,
  })
}

/** What the partner earned from `from` to `to`. */
export function useEarnings(from: string, to: string) {
  const { partner } = useAuth()
  return useQuery({
    queryKey: queryKeys.deliveryEarnings(partner?.id ?? 0, from, to),
    queryFn: ({ signal }) => getEarnings(from, to, signal),
    enabled: partner !== null,
    placeholderData: keepPreviousData,
  })
}

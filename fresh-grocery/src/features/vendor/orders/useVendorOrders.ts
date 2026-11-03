import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../api/queryKeys'
import type { Order, OrderStatus } from '../../../api/types'
import {
  acceptOrder,
  downloadStoreInvoice,
  getStoreOrder,
  getStoreOrders,
  markReadyForPickup,
  packOrder,
  rejectOrder,
} from '../../../api/vendorOrders'
import { saveFile } from '../../../utils/download'
import { useAuth } from '../../auth/authContext'
import { pollInterval } from '../../orders/orderStatus'
import { useOrderStreamState } from './useStoreOrderStream'
import type { StoreStep } from './vendorOrderStatus'

/** While the live stream is down, the queue asks for news four times a minute while it is on screen. */
export const QUEUE_POLL_MS = 15_000
/** While the live stream works, the server reports every change; the queue still asks once a minute, in case one got lost. */
export const SAFETY_NET_POLL_MS = 60_000
export const VENDOR_ORDERS_PER_PAGE = 20

/**
 * The order queue is the store's to-do list, so it must never show old news. The live stream (useStoreOrderStream)
 * reports every change at once and the lists are then loaded again; these settings are the safety net under it. The
 * app's defaults suit data that rarely changes (60 s fresh, nothing asked when the vendor comes back to the browser
 * tab); the queue overrides them:
 * - asked every 60 s while the stream is live, every 15 s while it is down (React Query pauses this while the browser
 *   tab is hidden);
 * - always "stale": a list or counter is asked again whenever it is shown (another tab of the queue, back from an
 *   order); the last answer stays on screen until the new one comes;
 * - asked at once when the vendor comes back to the browser tab.
 * React Query stops the timer when the page is left, cancels a request no longer needed (the `signal`), and asks once
 * for a list that several parts of the page show.
 */
function useLiveQueue() {
  const live = useOrderStreamState() === 'live'
  return {
    refetchInterval: live ? SAFETY_NET_POLL_MS : QUEUE_POLL_MS,
    staleTime: 0,
    refetchOnWindowFocus: true,
  } as const
}

/** One page of the store's orders (page is 1-based); no status = all of them. */
export function useStoreOrders(status: OrderStatus | undefined, page: number, size = VENDOR_ORDERS_PER_PAGE) {
  const { vendor } = useAuth()
  const liveQueue = useLiveQueue()
  return useQuery({
    queryKey: queryKeys.vendorOrders(vendor?.id ?? 0, { status, page }, size),
    queryFn: ({ signal }) => getStoreOrders({ status, page: page - 1, size }, signal),
    enabled: vendor !== null,
    ...liveQueue,
    placeholderData: keepPreviousData,
  })
}

/** How many orders are in one status, e.g. the new ones waiting (one tiny request: size=1, totalElements). */
export function useStoreOrderCount(status: OrderStatus, enabled = true) {
  const { vendor } = useAuth()
  const liveQueue = useLiveQueue()
  return useQuery({
    queryKey: queryKeys.vendorOrderCount(vendor?.id ?? 0, status),
    queryFn: ({ signal }) => getStoreOrders({ status, size: 1 }, signal).then((page) => page.totalElements),
    enabled: enabled && vendor !== null,
    ...liveQueue,
  })
}

/** Asks every list and counter of the queue again now (the "Check now" button). */
export function useRefreshStoreOrders() {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.allVendorOrderLists(vendor?.id ?? 0) })
}

/**
 * One order of the store, asked for again while it moves (the same rule as the customer's order page), and, like the
 * queue, whenever it is shown and at once when the vendor comes back to the browser tab. The live stream loads it again
 * as soon as it changes.
 */
export function useStoreOrder(orderId: number) {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorOrder(vendor?.id ?? 0, orderId),
    queryFn: ({ signal }) => getStoreOrder(orderId, signal),
    enabled: vendor !== null,
    refetchInterval: (current) => pollInterval(current.state.data),
    staleTime: 0,
    refetchOnWindowFocus: true,
  })
}

const STEP_CALLS: Record<StoreStep, (orderId: number) => Promise<Order>> = {
  accept: acceptOrder,
  pack: packOrder,
  ready: markReadyForPickup,
}

/**
 * The store's steps for one order. The answer (the changed order) replaces the cached one and the queue is loaded
 * again. A refused step (someone else changed the order) loads the order again. The callers show the messages.
 */
export function useOrderSteps(orderId: number) {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  const vendorId = vendor?.id ?? 0
  const applied = (order: Order) => {
    queryClient.setQueryData(queryKeys.vendorOrder(vendorId, orderId), order)
    return queryClient.invalidateQueries({ queryKey: queryKeys.allVendorOrderLists(vendorId) })
  }
  const refused = () => queryClient.invalidateQueries({ queryKey: queryKeys.vendorOrder(vendorId, orderId) })

  return {
    step: useMutation({ mutationFn: (step: StoreStep) => STEP_CALLS[step](orderId), onSuccess: applied, onError: refused }),
    reject: useMutation({ mutationFn: (reason: string) => rejectOrder(orderId, reason), onSuccess: applied, onError: refused }),
  }
}

export function useDownloadStoreInvoice(orderId: number) {
  return useMutation({
    mutationFn: () => downloadStoreInvoice(orderId),
    onSuccess: ({ blob, fileName }) => saveFile(blob, fileName ?? `invoice-order-${orderId}.pdf`),
  })
}

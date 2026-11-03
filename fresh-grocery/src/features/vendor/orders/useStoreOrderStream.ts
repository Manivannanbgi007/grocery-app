import { useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState } from 'react'
import { isApiError } from '../../../api/client'
import { queryKeys } from '../../../api/queryKeys'
import { followStoreOrders } from '../../../api/vendorOrders'
import { TRACKING_CONFIG } from '../../../config/tracking'
import { useAuth } from '../../auth/authContext'

/** The state of the live connection to the store's orders. */
export type OrderStreamState =
  | 'connecting' // opening the first connection
  | 'live' // connected: the server reports every change at once
  | 'reconnecting' // the connection broke; trying again (2 s, 4 s, 8 s ... at most 30 s apart)
  | 'failed' // not allowed (logged out, no store): no new attempts

/**
 * The live stream's state for every page of the vendor panel (VendorLayout provides it). Outside the panel there is no
 * stream: 'connecting', so a page keeps checking by itself.
 */
export const OrderStreamContext = createContext<OrderStreamState>('connecting')

export function useOrderStreamState(): OrderStreamState {
  return useContext(OrderStreamContext)
}

/**
 * Follows the store's orders live (Server-Sent Events) while `enabled`, and keeps React Query's copies up to date:
 * - "ready" (connected): the queue's lists and counters are loaded again (a change may have happened meanwhile);
 * - "order": the lists and counters, and that one order, are loaded again.
 * React Query asks only for what is on screen now; the rest is asked when it is shown next.
 * The vendor panel's frame (VendorLayout) opens it once, so every page of the panel is live.
 *
 * Robust like the tracking page's stream (useTrackingStream, with the same timing): it reconnects after any break
 * (at once when the browser is back online) and treats 45 s of silence as a dead connection (the server sends a
 * keep-alive every 15 s). Everything is cleaned up when the panel closes: the connection, the timers, the listener.
 */
export function useStoreOrderStream(enabled: boolean): OrderStreamState {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  const [state, setState] = useState<OrderStreamState>('connecting')
  const vendorId = vendor?.id ?? 0

  useEffect(() => {
    if (!enabled) return
    const panelClosed = new AbortController()
    let attempt = 0
    let retryTimer: number | undefined

    const reloadLists = () => void queryClient.invalidateQueries({ queryKey: queryKeys.allVendorOrderLists(vendorId) })
    const reloadOrder = (orderId: number) => void queryClient.invalidateQueries({ queryKey: queryKeys.vendorOrder(vendorId, orderId) })

    async function connect(): Promise<void> {
      retryTimer = undefined
      const connection = new AbortController()
      const closeConnection = () => connection.abort()
      panelClosed.signal.addEventListener('abort', closeConnection)
      let silenceTimer: number | undefined
      const stillAlive = () => {
        window.clearTimeout(silenceTimer)
        silenceTimer = window.setTimeout(closeConnection, TRACKING_CONFIG.silentConnectionMs)
      }

      let broke = false
      try {
        stillAlive()
        await followStoreOrders(
          () => {
            attempt = 0
            setState('live')
            reloadLists()
          },
          (change) => {
            reloadLists()
            reloadOrder(change.orderId)
          },
          stillAlive,
          connection.signal,
        )
        // The server ended the stream (after 30 minutes, or it is restarting): connect again
      } catch (error) {
        if (panelClosed.signal.aborted) return
        if (isApiError(error, 401) || isApiError(error, 403)) {
          setState('failed')
          return
        }
        broke = true
      } finally {
        window.clearTimeout(silenceTimer)
        panelClosed.signal.removeEventListener('abort', closeConnection)
      }
      if (panelClosed.signal.aborted) return

      // A broken stream shows "reconnecting" (the pages then check every 15 s). After a planned end the panel stays
      // "live" for the short wait: the next stream starts with "ready", which loads the lists again.
      if (broke) setState('reconnecting')
      const delay = Math.min(TRACKING_CONFIG.reconnectFirstMs * 2 ** attempt, TRACKING_CONFIG.reconnectMaxMs)
      attempt += 1
      retryTimer = window.setTimeout(() => void connect(), delay)
    }

    // Back online (e.g. the laptop's Wi-Fi returned): do not wait for the next attempt
    const reconnectNow = () => {
      if (retryTimer === undefined) return
      window.clearTimeout(retryTimer)
      attempt = 0
      void connect()
    }

    window.addEventListener('online', reconnectNow)
    void connect()
    return () => {
      panelClosed.abort()
      window.clearTimeout(retryTimer)
      window.removeEventListener('online', reconnectNow)
    }
  }, [enabled, queryClient, vendorId])

  // Without a stream (the store is not approved yet) nothing is live
  return enabled ? state : 'connecting'
}

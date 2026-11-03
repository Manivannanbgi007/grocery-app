import { useEffect, useState } from 'react'
import { isApiError } from '../../api/client'
import { followOrderTracking } from '../../api/tracking'
import type { TrackingSnapshot } from '../../api/types'
import { TRACKING_CONFIG } from '../../config/tracking'
import { isOverStatus } from './trackingTexts'

/** The state of the live connection, for the page's status badge. */
export type StreamState =
  | 'connecting' // opening the first connection
  | 'live' // connected: updates arrive as they happen
  | 'reconnecting' // the connection was lost; trying again (2 s, 4 s, 8 s ... at most 30 s apart)
  | 'waiting' // the order has no delivery yet (404): asking again every 15 s
  | 'ended' // the delivery is over: the last state is shown, no more updates
  | 'failed' // not allowed (logged out, not the customer's order): no new attempts

const WAITING_RETRY_MS = 15_000

/**
 * Follows the delivery of an order live (Server-Sent Events) while `enabled`: the last snapshot and the connection
 * state. Everything is cleaned up when the page closes (the connection, the timers, the listener).
 * One hook per order: give the component that uses it key={orderId}, so another order starts fresh.
 *
 * Robust on purpose: a phone changes networks, a laptop sleeps, a proxy closes a quiet connection. So it reconnects
 * after any break (sooner when the browser says it is back online), and treats 45 s of silence as a dead connection
 * (the server sends at least a keep-alive every 15 s).
 */
export function useTrackingStream(orderId: number, enabled: boolean): { snapshot: TrackingSnapshot | null; state: StreamState } {
  const [snapshot, setSnapshot] = useState<TrackingSnapshot | null>(null)
  const [state, setState] = useState<StreamState>('connecting')

  useEffect(() => {
    if (!enabled) return
    const pageClosed = new AbortController()
    let attempt = 0
    let retryTimer: number | undefined

    async function connect(): Promise<void> {
      retryTimer = undefined
      const connection = new AbortController()
      const closeConnection = () => connection.abort()
      pageClosed.signal.addEventListener('abort', closeConnection)
      let silenceTimer: number | undefined
      const stillAlive = () => {
        window.clearTimeout(silenceTimer)
        silenceTimer = window.setTimeout(closeConnection, TRACKING_CONFIG.silentConnectionMs)
      }

      let over = false
      let noDeliveryYet = false
      try {
        stillAlive()
        await followOrderTracking(
          orderId,
          (next) => {
            attempt = 0
            over = isOverStatus(next.status)
            setSnapshot(next)
            setState(over ? 'ended' : 'live')
          },
          stillAlive,
          connection.signal,
        )
        // The server ended the stream: the delivery is over (stop), or the stream reached its time limit (reconnect)
      } catch (error) {
        if (pageClosed.signal.aborted) return
        if (isApiError(error, 401) || isApiError(error, 403)) {
          setState('failed')
          return
        }
        noDeliveryYet = isApiError(error, 404)
      } finally {
        window.clearTimeout(silenceTimer)
        pageClosed.signal.removeEventListener('abort', closeConnection)
      }
      if (pageClosed.signal.aborted || over) return

      setState(noDeliveryYet ? 'waiting' : 'reconnecting')
      const delay = noDeliveryYet ? WAITING_RETRY_MS : Math.min(TRACKING_CONFIG.reconnectFirstMs * 2 ** attempt, TRACKING_CONFIG.reconnectMaxMs)
      attempt += 1
      retryTimer = window.setTimeout(() => void connect(), delay)
    }

    // Back online (e.g. the phone left a tunnel): do not wait for the next attempt
    const reconnectNow = () => {
      if (retryTimer === undefined) return
      window.clearTimeout(retryTimer)
      attempt = 0
      void connect()
    }

    window.addEventListener('online', reconnectNow)
    void connect()
    return () => {
      pageClosed.abort()
      window.clearTimeout(retryTimer)
      window.removeEventListener('online', reconnectNow)
    }
  }, [orderId, enabled])

  return { snapshot, state }
}

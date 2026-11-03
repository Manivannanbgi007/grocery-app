import type { OrderStatus } from '../api/types'
import { listStoreOrders } from './mockOrders'

/*
 * The vendor's live order stream for the mock API (order-service's VendorOrderStreams): "ready" at once, then an
 * "order" event whenever one of the store's orders is new or changes status, and a keep-alive every 15 s.
 * The real backend hears about every change from Kafka; the mock simply compares the store's orders twice a second.
 * (Reading them also lets the mock's saga move a new order on, as the real one does by itself.)
 */

const CHECK_EVERY_MS = 500
/** The cleanup of every open stream (so a test can close them all). */
const openStreams = new Set<() => void>()

/** For tests: every stream closed. */
export function resetMockOrderStreams(): void {
  openStreams.forEach((close) => close())
  openStreams.clear()
}

/** Each order of the store with its status now. */
function statusesOf(storeId: number): Map<number, OrderStatus> {
  return new Map(listStoreOrders(storeId, null).map((order) => [order.id, order.status]))
}

export function storeOrderStream(storeId: number): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  let close = () => {}
  return new ReadableStream<Uint8Array>({
    start(controller) {
      let closed = false
      const send = (text: string) => {
        if (!closed) controller.enqueue(encoder.encode(text))
      }
      let known = statusesOf(storeId)
      const check = setInterval(() => {
        const now = statusesOf(storeId)
        now.forEach((status, orderId) => {
          if (known.get(orderId) !== status) send(`event:order\ndata:${JSON.stringify({ orderId, status })}\n\n`)
        })
        known = now
      }, CHECK_EVERY_MS)
      const keepAlive = setInterval(() => send(':keep-alive\n\n'), 15_000)
      close = () => {
        if (closed) return
        closed = true
        clearInterval(check)
        clearInterval(keepAlive)
        openStreams.delete(close)
        try {
          controller.close()
        } catch {
          // the reader already cancelled the stream (page closed): nothing to close
        }
      }
      openStreams.add(close)
      send('event:ready\ndata:{}\n\n')
    },
    cancel() {
      close()
    },
  })
}

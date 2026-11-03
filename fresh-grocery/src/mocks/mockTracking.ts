import type { Delivery, DeliveryPartner, DeliveryStatus, LiveLocation, LocationUpdate, TrackingSnapshot } from '../api/types'
import { MockApiError, validationFailed } from './mockApiError'

/*
 * Live delivery tracking for the mock API (delivery-service's DeliveryTrackingServiceImpl, in memory): the partners'
 * last positions, the same rules as the backend, and the open live streams of this browser tab.
 * mockDelivery.ts calls trackingChanged() after every status change, like the backend's DeliveryStatusListener.
 * There is no routing service in the mock: the route is always null (the map draws a straight dashed line).
 */

const TRACKED: DeliveryStatus[] = ['ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY']
const OVER: DeliveryStatus[] = ['DELIVERED', 'FAILED', 'RETURNED', 'CANCELLED']
const MIN_UPDATE_INTERVAL_MS = 2_000
const MAX_LOCATION_AGE_MS = 10 * 60_000
const ALLOWED_CLOCK_DIFFERENCE_MS = 60_000
const MAX_ACCURACY_METERS = 1_000

let locations = new Map<number, LiveLocation>()
const listeners = new Set<(delivery: Delivery) => void>()
/** The cleanup of every open stream (so a test can close them all). */
const openStreams = new Set<() => void>()

export const isTracked = (status: DeliveryStatus) => TRACKED.includes(status)
export const isOver = (status: DeliveryStatus) => OVER.includes(status)

/** For tests: no positions, every stream closed. */
export function resetMockTracking(): void {
  openStreams.forEach((close) => close())
  openStreams.clear()
  listeners.clear()
  locations = new Map()
}

/** A delivery changed (a status, a position): the streams of its order send the new snapshot. */
export function trackingChanged(delivery: Delivery): void {
  if (!isTracked(delivery.status)) locations.delete(delivery.id) // the position is forgotten when tracking ends
  listeners.forEach((listener) => listener(delivery))
}

/** The rules of DeliveryTrackingServiceImpl.updateLocation. Returns false when the update was skipped. */
export function updateLocation(delivery: Delivery, body: Partial<LocationUpdate>): boolean {
  const errors = []
  if (typeof body.latitude !== 'number' || body.latitude < -90 || body.latitude > 90) errors.push({ field: 'latitude', message: 'must be between -90 and 90' })
  if (typeof body.longitude !== 'number' || body.longitude < -180 || body.longitude > 180) errors.push({ field: 'longitude', message: 'must be between -180 and 180' })
  if (!body.recordedAt) errors.push({ field: 'recordedAt', message: 'must not be null' })
  if (errors.length > 0) throw validationFailed(errors)

  if (!isTracked(delivery.status)) {
    throw new MockApiError(400, isOver(delivery.status) ? `This delivery is ${delivery.status}: location sharing has stopped` : 'Location sharing starts when you accept the delivery')
  }
  const now = Date.now()
  const recordedAt = new Date(body.recordedAt!).getTime()
  if (recordedAt > now + ALLOWED_CLOCK_DIFFERENCE_MS) throw new MockApiError(400, "The location's time is in the future: please check the phone's date and time")
  if (recordedAt < now - MAX_LOCATION_AGE_MS) throw new MockApiError(400, 'This location is older than 10 minutes and was not used')
  if (body.accuracyMeters && body.accuracyMeters > MAX_ACCURACY_METERS) {
    throw new MockApiError(400, `The location is not precise enough (about ${Math.round(body.accuracyMeters)} m). Please turn on GPS / precise location`)
  }

  const last = locations.get(delivery.id)
  if (last && (recordedAt <= new Date(last.recordedAt).getTime() || now - new Date(last.receivedAt).getTime() < MIN_UPDATE_INTERVAL_MS)) return false
  locations.set(delivery.id, {
    latitude: body.latitude!,
    longitude: body.longitude!,
    accuracyMeters: body.accuracyMeters ?? null,
    recordedAt: new Date(recordedAt).toISOString(),
    receivedAt: new Date(now).toISOString(),
  })
  trackingChanged(delivery)
  return true
}

/** TrackingSnapshotFactory.build */
export function snapshotOf(delivery: Delivery, partner: DeliveryPartner | undefined): TrackingSnapshot {
  return {
    orderId: delivery.orderId,
    deliveryId: delivery.id,
    status: delivery.status,
    live: isTracked(delivery.status),
    partner: partner ? { name: partner.fullName, phone: partner.phone, vehicleType: partner.vehicleType } : null,
    pickup: { name: delivery.storeName, address: delivery.storeAddress, location: delivery.storeLocation },
    destination: { name: delivery.customerName, address: delivery.customerAddress, location: delivery.customerLocation },
    location: isTracked(delivery.status) ? (locations.get(delivery.id) ?? null) : null,
    route: null,
    generatedAt: new Date().toISOString(),
  }
}

/**
 * The live stream of one order (LiveTrackingStreams): the snapshot at once, then after every change, a keep-alive every
 * 15 s; it ends after the final state of a delivery that is over.
 */
export function trackingStream(orderId: number, current: () => TrackingSnapshot, snapshotAfterChange: (delivery: Delivery) => TrackingSnapshot): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder()
  let close = () => {}
  return new ReadableStream<Uint8Array>({
    start(controller) {
      let closed = false
      const send = (snapshot: TrackingSnapshot) => {
        if (closed) return
        controller.enqueue(encoder.encode(`event:tracking\ndata:${JSON.stringify(snapshot)}\n\n`))
        if (isOver(snapshot.status)) close()
      }
      const listener = (delivery: Delivery) => {
        if (delivery.orderId === orderId) send(snapshotAfterChange(delivery))
      }
      const keepAlive = setInterval(() => !closed && controller.enqueue(encoder.encode(':keep-alive\n\n')), 15_000)
      close = () => {
        if (closed) return
        closed = true
        listeners.delete(listener)
        clearInterval(keepAlive)
        openStreams.delete(close)
        try {
          controller.close()
        } catch {
          // the reader already cancelled the stream (page closed): nothing to close
        }
      }
      listeners.add(listener)
      openStreams.add(close)
      send(current())
    },
    cancel() {
      close()
    },
  })
}

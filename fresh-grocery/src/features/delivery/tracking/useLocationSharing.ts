import { useCallback, useEffect, useRef, useState } from 'react'
import { isApiError } from '../../../api/client'
import { sendLocation } from '../../../api/tracking'
import type { TrackingRoute } from '../../../api/types'
import { TRACKING_CONFIG } from '../../../config/tracking'
import { distanceMeters } from '../../../utils/geo'

/** Where location sharing stands, for the panel's message. */
export type SharingState =
  | 'off' // not started yet: the partner taps "Share my location" (the browser then asks for permission)
  | 'starting' // waiting for the permission answer or the first GPS position
  | 'sharing' // positions are measured and sent
  | 'denied' // location access was refused (by the partner or the browser settings)
  | 'unavailable' // the device cannot find its position (location turned off, no GPS)
  | 'unsupported' // the browser has no Geolocation API
  | 'insecure' // not an https page: browsers only give positions to secure pages

/** The partner's own last GPS fix. */
export interface OwnPosition {
  latitude: number
  longitude: number
  accuracyMeters: number | null
  recordedAt: string
  receivedAt: string
}

export interface LocationSharing {
  state: SharingState
  position: OwnPosition | null
  /** when the last position reached the server (ms), null before the first one */
  lastSentAt: number | null
  /** the last problem while sending (no connection, too imprecise ...), null when the last send worked */
  problem: string | null
  route: TrackingRoute | null
  /** true after the page was in the background while sharing (the browser may have paused the GPS) */
  wasInBackground: boolean
  start: () => void
}

/**
 * Shares the partner's position for one delivery while `active` (the delivery is accepted, picked up or on its way).
 * It stops by itself when the delivery is no longer active, and when the page closes (the GPS watch, the timers and
 * the listeners are all removed).
 *
 * How it works: the browser's Geolocation API reports positions (watchPosition). Not every position is sent: only after
 * moving 25 m or after 15 s, never faster than every 4 s (TRACKING_CONFIG), and none that are too imprecise. A failed
 * send is not repeated: the next position (seconds later) is newer anyway.
 *
 * A web page cannot share its location in the background: when the phone locks or another app is in front, the browser
 * pauses the page. That is why the screen is kept on while sharing (Wake Lock API, where supported) and the panel asks
 * to keep it open. Continuous background tracking needs a native app (docs/delivery-tracking.md).
 */
export function useLocationSharing(deliveryId: number, active: boolean, onDeliveryEnded: () => void): LocationSharing {
  const [wanted, setWanted] = useState(false)
  const [state, setState] = useState<SharingState>('off')
  const [position, setPosition] = useState<OwnPosition | null>(null)
  const [lastSentAt, setLastSentAt] = useState<number | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [route, setRoute] = useState<TrackingRoute | null>(null)
  const [wasInBackground, setWasInBackground] = useState(false)
  const lastSent = useRef<{ latitude: number; longitude: number; at: number } | null>(null)
  const sending = useRef(false)
  const endedRef = useRef(onDeliveryEnded)
  useEffect(() => {
    endedRef.current = onDeliveryEnded
  })

  const start = useCallback(() => {
    if (!('geolocation' in navigator)) return setState('unsupported')
    if (window.isSecureContext === false) return setState('insecure')
    setWasInBackground(false)
    setState('starting')
    setWanted(true)
  }, [])

  // Starts by itself when the browser already allows location for this site (no question to ask)
  useEffect(() => {
    if (!active || !navigator.permissions?.query) return
    let cancelled = false
    navigator.permissions
      .query({ name: 'geolocation' })
      .then((permission) => {
        if (!cancelled && permission.state === 'granted') start()
      })
      .catch(() => {
        // Permissions API not usable here: the partner taps the button
      })
    return () => {
      cancelled = true
    }
  }, [active, start])

  // The GPS watch, while wanted and the delivery is active
  useEffect(() => {
    if (!wanted || !active) return

    const send = (fix: OwnPosition) => {
      sending.current = true
      sendLocation(deliveryId, { latitude: fix.latitude, longitude: fix.longitude, accuracyMeters: fix.accuracyMeters, recordedAt: fix.recordedAt })
        .then((result) => {
          lastSent.current = { latitude: fix.latitude, longitude: fix.longitude, at: Date.now() }
          setLastSentAt(Date.now())
          setProblem(null)
          setRoute(result.route)
        })
        .catch((error: unknown) => {
          if (isApiError(error, 0)) setProblem('No connection: your position is sent again as soon as you are back online.')
          else if (isApiError(error, 400) && /stopped|accept/.test(error.message)) endedRef.current() // the delivery moved on
          else setProblem(error instanceof Error ? error.message : 'Your position could not be sent.')
        })
        .finally(() => {
          sending.current = false
        })
    }

    const onPosition = (geo: GeolocationPosition) => {
      const fix: OwnPosition = {
        latitude: geo.coords.latitude,
        longitude: geo.coords.longitude,
        accuracyMeters: Number.isFinite(geo.coords.accuracy) ? geo.coords.accuracy : null,
        recordedAt: new Date(geo.timestamp).toISOString(),
        receivedAt: new Date().toISOString(),
      }
      setPosition(fix)
      setState('sharing')

      if (fix.accuracyMeters !== null && fix.accuracyMeters > TRACKING_CONFIG.maxAccuracyMeters) {
        setProblem(`Your position is not precise enough (about ${Math.round(fix.accuracyMeters)} m). Turn on GPS / precise location.`)
        return
      }
      const last = lastSent.current
      const now = Date.now()
      if (sending.current || (last && now - last.at < TRACKING_CONFIG.sendAtMostEveryMs)) return
      const moved = last ? distanceMeters(last, fix) : Infinity
      if (!last || moved >= TRACKING_CONFIG.sendAfterMovingMeters || now - last.at >= TRACKING_CONFIG.sendAtLeastEveryMs) send(fix)
    }

    const onError = (error: GeolocationPositionError) => {
      if (error.code === error.PERMISSION_DENIED) {
        setState('denied')
        setWanted(false)
      } else if (error.code === error.POSITION_UNAVAILABLE) {
        setState('unavailable')
      } // TIMEOUT: no position for a while; the watch goes on and the panel shows the last one
    }

    const { geolocation } = navigator
    const watchId = geolocation.watchPosition(onPosition, onError, { enableHighAccuracy: true, maximumAge: 5_000, timeout: 30_000 })
    return () => geolocation.clearWatch(watchId)
  }, [wanted, active, deliveryId])

  // While sharing: keep the screen on, and notice when the page goes to the background
  useEffect(() => {
    if (state !== 'sharing') return
    let wakeLock: WakeLockSentinel | null = null
    const keepScreenOn = () => {
      navigator.wakeLock
        ?.request('screen')
        .then((sentinel) => {
          wakeLock = sentinel
        })
        .catch(() => {
          // not supported or refused (e.g. battery saver): sharing works anyway while the screen is on
        })
    }
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') setWasInBackground(true)
      else keepScreenOn() // the browser releases the wake lock in the background
    }
    keepScreenOn()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      void wakeLock?.release().catch(() => {})
    }
  }, [state])

  // The delivery is no longer active (delivered, cancelled ...): the watch effect above has stopped, and the state says so
  return { state: active ? state : 'off', position, lastSentAt, problem, route, wasInBackground, start }
}

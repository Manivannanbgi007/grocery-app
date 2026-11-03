import type { GeoPoint } from '../api/types'

const EARTH_RADIUS_METERS = 6_371_000
const toRadians = (degrees: number) => (degrees * Math.PI) / 180

/** Straight-line distance in meters between two points (haversine formula): ignores roads. */
export function distanceMeters(from: GeoPoint, to: GeoPoint): number {
  const lat1 = toRadians(from.latitude)
  const lat2 = toRadians(to.latitude)
  const deltaLat = lat2 - lat1
  const deltaLon = toRadians(to.longitude - from.longitude)
  const a = Math.sin(deltaLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) ** 2
  return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

/** 450 -> "450 m", 1240 -> "1.2 km" */
export function formatDistance(meters: number): string {
  return meters < 1000 ? `${Math.round(meters / 10) * 10} m` : `${(meters / 1000).toFixed(1)} km`
}

/** How long ago, short: "just now", "20 s ago", "3 min ago", "2 h ago". */
export function formatAge(iso: string, now: number): string {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000))
  if (seconds < 10) return 'just now'
  if (seconds < 60) return `${seconds} s ago`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`
  return `${Math.floor(seconds / 3600)} h ago`
}

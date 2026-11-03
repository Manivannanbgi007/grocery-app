/*
 * Every setting of the delivery tracking maps in one place (docs/delivery-tracking.md). Change them here, or for one
 * environment with the VITE_MAP_* variables (.env.example) - never write tile addresses or coordinates into components.
 */

const env = import.meta.env

/** "12.9716,77.5946" -> [12.9716, 77.5946]; the fallback when the value is missing or not two numbers. */
function parsePoint(value: string | undefined, fallback: [number, number]): [number, number] {
  const parts = (value ?? '').split(',').map(Number)
  return parts.length === 2 && parts.every(Number.isFinite) ? [parts[0], parts[1]] : fallback
}

export const MAP_CONFIG = {
  /**
   * The map images ("tiles"). The default is OpenStreetMap's own tile server: free, run by volunteers, for light use
   * only - no promise of capacity and no SLA (https://operations.osmfoundation.org/policies/tiles/). A production shop
   * uses a tile provider with a contract (MapTiler, Stadia Maps, Thunderforest ...) or its own tile server: set
   * VITE_MAP_TILE_URL (and its attribution) to that.
   */
  tileUrl: env.VITE_MAP_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  /** Required by OpenStreetMap's licence: always shown in the map's corner. */
  attribution:
    env.VITE_MAP_ATTRIBUTION || '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors',
  maxZoom: 19,
  /** Where the map looks before any point is known (Bengaluru, where the shop delivers). */
  defaultCenter: parsePoint(env.VITE_MAP_DEFAULT_CENTER, [12.9716, 77.5946]),
  defaultZoom: 13,
  /** The zoom when there is only one point to show. */
  singlePointZoom: 15,
  /** Lines and circles on the map: names of the theme's colours (index.css), read with themeColour() */
  colors: {
    route: 'brand',
    straightLine: 'body',
    accuracy: 'brand',
  },
} as const

export const TRACKING_CONFIG = {
  /** The live stream reconnects after a lost connection: first after this, then twice as long each time ... */
  reconnectFirstMs: 2_000,
  /** ... but never waiting longer than this. */
  reconnectMaxMs: 30_000,
  /** The server sends at least a keep-alive every 15 s; silence for this long means the connection is dead. */
  silentConnectionMs: 45_000,
  /** The partner's position is shown as "delayed" when it is older than this. */
  staleLocationMs: 60_000,

  // --- the partner's phone (location sharing) ---
  /** A new position is sent after moving this far ... */
  sendAfterMovingMeters: 25,
  /** ... or after this long anyway (so the customer sees "updated just now" while waiting at a red light) ... */
  sendAtLeastEveryMs: 15_000,
  /** ... but never more often than this (the server skips updates closer than 2 s). */
  sendAtMostEveryMs: 4_000,
  /** Positions less precise than this are not sent (the server refuses them anyway). */
  maxAccuracyMeters: 1_000,
} as const

import L from 'leaflet'
import { useEffect, useRef } from 'react'
import { useMap, useMapEvents } from 'react-leaflet'
import { MAP_CONFIG } from '../../config/tracking'

/**
 * Keeps the map's view useful without fighting the user:
 * - when points appear (or disappear), the view fits all of them;
 * - when the moving point (the partner) leaves the view, the map follows it - unless the user dragged the map
 *   themselves (then they decide where to look).
 */
export function FitToPoints({ points, follow }: { points: [number, number][]; follow: [number, number] | null }) {
  const map = useMap()
  const userMoved = useRef(false)
  useMapEvents({
    dragstart: () => {
      userMoved.current = true
    },
  })

  // Fits the view when the NUMBER of points changes - on purpose not on every move of the partner (following them is
  // the next effect). The points themselves are read from a ref, so their changes alone do not run it.
  // Without animation: the view is final at once, so the next effect (which runs right after) sees the new view.
  // (An animated fit and a pan started at the same moment fight each other, and the markers end up out of view.)
  const latestPoints = useRef(points)
  useEffect(() => {
    latestPoints.current = points
  })
  const pointCount = points.length
  useEffect(() => {
    const current = latestPoints.current
    if (current.length === 0) return
    if (current.length === 1) map.setView(current[0], MAP_CONFIG.singlePointZoom, { animate: false })
    else map.fitBounds(L.latLngBounds(current), { padding: [48, 48], maxZoom: 16, animate: false })
  }, [map, pointCount])

  const followLat = follow?.[0]
  const followLng = follow?.[1]
  useEffect(() => {
    if (followLat === undefined || followLng === undefined || userMoved.current) return
    if (!map.getBounds().contains([followLat, followLng])) map.panTo([followLat, followLng])
  }, [map, followLat, followLng])

  return null
}

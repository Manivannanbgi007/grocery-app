import { Polyline } from 'react-leaflet'
import type { GeoPoint, TrackingRoute } from '../../api/types'
import { MAP_CONFIG } from '../../config/tracking'
import { themeColour } from '../../utils/themeColour'

/**
 * The way from the partner to the next stop. With a routing service: the route along the streets (solid green line).
 * Without one: a dashed grey straight line - so nobody mistakes it for the road (the page says "straight line" too).
 */
export function RouteLine({ route, from, to }: { route: TrackingRoute | null; from: GeoPoint | null; to: GeoPoint | null }) {
  if (route && route.points.length > 1) {
    return <Polyline positions={route.points} pathOptions={{ color: themeColour(MAP_CONFIG.colors.route), weight: 5, opacity: 0.85 }} />
  }
  if (from && to) {
    return (
      <Polyline
        positions={[
          [from.latitude, from.longitude],
          [to.latitude, to.longitude],
        ]}
        pathOptions={{ color: themeColour(MAP_CONFIG.colors.straightLine), weight: 3, dashArray: '6 8', opacity: 0.8 }}
      />
    )
  }
  return null
}

import 'leaflet/dist/leaflet.css'
import { MapContainer, TileLayer } from 'react-leaflet'
import type { GeoPoint, LiveLocation, TrackingRoute } from '../../api/types'
import { MAP_CONFIG } from '../../config/tracking'
import { cn } from '../../utils/cn'
import { FitToPoints } from './FitToPoints'
import { PartnerMarker, PlaceMarker } from './markers'
import { RouteLine } from './RouteLine'

export interface TrackingMapProps {
  /** the store (null when it has no coordinates) */
  pickup: GeoPoint | null
  pickupLabel: string
  /** the customer's address (null when unknown) */
  destination: GeoPoint | null
  destinationLabel: string
  /** the partner's last position (null when unknown) */
  partner: LiveLocation | null
  partnerLabel: string
  /** the position is old: shown grey */
  partnerStale?: boolean
  /** where the partner goes next: PICKUP (the store) or DESTINATION (the customer) */
  nextStop: 'PICKUP' | 'DESTINATION'
  route: TrackingRoute | null
  /** what a screen reader announces for the map */
  ariaLabel: string
  className?: string
}

/**
 * The delivery map: OpenStreetMap tiles (MAP_CONFIG), the store, the customer's address, the partner, and the way
 * between the partner and the next stop. Everything it shows comes in as props; it loads nothing itself.
 * Loaded on demand (LazyTrackingMap), so Leaflet is only downloaded by pages that show a map.
 */
export default function TrackingMap(props: TrackingMapProps) {
  const { pickup, destination, partner, nextStop, route } = props
  const partnerPoint: [number, number] | null = partner ? [partner.latitude, partner.longitude] : null
  const points = [pickup, destination, partner]
    .filter((point): point is GeoPoint => point !== null)
    .map((point): [number, number] => [point.latitude, point.longitude])
  const target = nextStop === 'PICKUP' ? pickup : destination

  return (
    <div role="region" aria-label={props.ariaLabel} className={cn('relative z-0 overflow-hidden rounded-2xl border border-line bg-card', props.className)}>
      <MapContainer center={MAP_CONFIG.defaultCenter} zoom={MAP_CONFIG.defaultZoom} maxZoom={MAP_CONFIG.maxZoom} scrollWheelZoom={false} className="size-full">
        {/* The attribution is required by OpenStreetMap's licence: keep it */}
        <TileLayer url={MAP_CONFIG.tileUrl} attribution={MAP_CONFIG.attribution} maxZoom={MAP_CONFIG.maxZoom} />
        <RouteLine route={route} from={partner} to={target} />
        {pickup && <PlaceMarker kind="store" point={pickup} label={props.pickupLabel} />}
        {destination && <PlaceMarker kind="home" point={destination} label={props.destinationLabel} />}
        {partner && <PartnerMarker location={partner} label={props.partnerLabel} stale={props.partnerStale ?? false} />}
        <FitToPoints points={points} follow={partnerPoint} />
      </MapContainer>
    </div>
  )
}

import L from 'leaflet'
import { Circle, Marker, Tooltip } from 'react-leaflet'
import { MAP_CONFIG } from '../../config/tracking'
import type { GeoPoint, LiveLocation } from '../../api/types'
import { themeColour } from '../../utils/themeColour'

/*
 * The markers of the tracking map. Leaflet's default marker is a PNG that bundlers cannot find, so each marker is a
 * small piece of HTML ("divIcon"): a round badge with an emoji, styled with Tailwind classes.
 */

function badgeIcon(emoji: string, classes: string): L.DivIcon {
  return L.divIcon({
    className: '', // no Leaflet default styles: the HTML below is the whole marker
    html: `<span class="flex size-10 items-center justify-center rounded-full border-2 border-white text-xl shadow-md ${classes}">${emoji}</span>`,
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    tooltipAnchor: [0, -22],
  })
}

const ICONS = {
  store: badgeIcon('🏪', 'bg-brand-light'),
  home: badgeIcon('🏠', 'bg-accent'),
  partner: badgeIcon('🛵', 'bg-brand'),
  partnerStale: badgeIcon('🛵', 'bg-muted'),
}

/** The store (where the order is picked up) or the customer's address (where it goes). */
export function PlaceMarker({ kind, point, label }: { kind: 'store' | 'home'; point: GeoPoint; label: string }) {
  return (
    <Marker position={[point.latitude, point.longitude]} icon={ICONS[kind]} title={label} alt={label} keyboard>
      <Tooltip direction="top">{label}</Tooltip>
    </Marker>
  )
}

/**
 * The delivery partner. A grey badge when the position is old ("stale"). A faint circle shows how far off the
 * position may be when the phone said it is not very precise (more than 30 m).
 */
export function PartnerMarker({ location, label, stale }: { location: LiveLocation; label: string; stale: boolean }) {
  const position: [number, number] = [location.latitude, location.longitude]
  return (
    <>
      {location.accuracyMeters !== null && location.accuracyMeters > 30 && (
        <Circle center={position} radius={location.accuracyMeters} pathOptions={{ color: themeColour(MAP_CONFIG.colors.accuracy), weight: 1, fillOpacity: 0.1 }} />
      )}
      <Marker position={position} icon={stale ? ICONS.partnerStale : ICONS.partner} title={label} alt={label} keyboard zIndexOffset={1000}>
        <Tooltip direction="top">{label}</Tooltip>
      </Marker>
    </>
  )
}

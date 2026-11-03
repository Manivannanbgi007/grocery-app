import { useQueryClient } from '@tanstack/react-query'
import { LocateFixed, Smartphone } from 'lucide-react'
import { useCallback } from 'react'
import { queryKeys } from '../../../api/queryKeys'
import type { Delivery } from '../../../api/types'
import { LazyTrackingMap } from '../../../components/map/LazyTrackingMap'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { useNow } from '../../../hooks/useNow'
import { formatAge } from '../../../utils/geo'
import { useAuth } from '../../auth/authContext'
import { sharesLocation } from '../deliveryTexts'
import { useLocationSharing, type SharingState } from './useLocationSharing'

const MESSAGES: Record<Exclude<SharingState, 'sharing'>, string> = {
  off: 'The customer follows your trip on a map. Share your location while you deliver this order.',
  starting: 'Finding your position…',
  denied: 'Location access is blocked for this site. Allow it in the browser (the lock or "i" icon next to the address → Location), then tap "Share my location" again.',
  unavailable: 'Your phone cannot find its position. Turn on location (GPS) and wait a moment.',
  unsupported: 'This browser cannot share a location. Please use a current Chrome, Safari, Edge or Firefox.',
  insecure: 'Location can only be shared on a secure (https) page. Please open the delivery panel through its https address.',
}

/**
 * The partner's live location for one delivery: start sharing (the browser asks for permission), what is being shared
 * and when it was last sent, and a map with the store, the customer and the partner. Shown while the delivery is
 * accepted, picked up or on its way; sharing stops by itself afterwards.
 */
export function LiveLocationPanel({ delivery }: { delivery: Delivery }) {
  const { partner } = useAuth()
  const queryClient = useQueryClient()
  const active = sharesLocation(delivery.status)
  // The server says the delivery moved on (e.g. cancelled): load it again, the panel then disappears
  const deliveryEnded = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.deliveryTask(partner?.id ?? 0, delivery.id) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.allDeliveryTasks(partner?.id ?? 0) })
  }, [queryClient, partner?.id, delivery.id])
  const sharing = useLocationSharing(delivery.id, active, deliveryEnded)
  const now = useNow()

  if (!active) return null
  const state = sharing.state
  const sharingNow = state === 'sharing'
  let message: string
  if (state !== 'sharing') message = MESSAGES[state]
  else if (sharing.lastSentAt) message = `The customer sees where you are. Last sent ${formatAge(new Date(sharing.lastSentAt).toISOString(), now)}.`
  else message = 'Sending your first position…'

  return (
    <section aria-labelledby={`live-location-${delivery.id}`} className="space-y-3 rounded-2xl border border-line bg-card p-5 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id={`live-location-${delivery.id}`} className="flex items-center gap-2 text-xl">
          <LocateFixed className="size-5 text-brand" aria-hidden="true" />
          Live location
        </h2>
        <Badge tone={sharingNow ? 'brand' : 'neutral'}>{sharingNow ? 'Sharing' : 'Not sharing'}</Badge>
      </div>

      <p role="status" className="text-sm text-heading">
        {message}
      </p>
      {sharing.problem && (
        <p role="alert" className="rounded-lg bg-hot/10 px-3 py-2 text-sm text-heading">
          {sharing.problem}
        </p>
      )}
      {(state === 'off' || state === 'denied') && <Button onClick={sharing.start}>Share my location</Button>}

      {sharingNow && (
        <p className="flex gap-2 text-sm">
          <Smartphone className="size-4 shrink-0" aria-hidden="true" />
          {sharing.wasInBackground
            ? 'While the app was in the background, your location may not have been sent. Keep this screen open while you deliver.'
            : 'Keep this screen open while you deliver: a phone pauses the location of a web page in the background.'}
        </p>
      )}

      <LazyTrackingMap
        className="h-72"
        ariaLabel="Your delivery on the map: the store, the customer and you"
        pickup={delivery.storeLocation}
        pickupLabel={`${delivery.storeName} (pick up)`}
        destination={delivery.customerLocation}
        destinationLabel={`${delivery.customerName} (deliver)`}
        partner={sharing.position}
        partnerLabel="You"
        nextStop={delivery.status === 'ACCEPTED' ? 'PICKUP' : 'DESTINATION'}
        route={sharing.route}
      />
    </section>
  )
}

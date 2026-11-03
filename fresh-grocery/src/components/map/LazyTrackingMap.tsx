import { lazy, Suspense } from 'react'
import { cn } from '../../utils/cn'
import { LoadingState } from '../ui/LoadingState'
import type { TrackingMapProps } from './TrackingMap'

// Leaflet (~150 kB) is only downloaded when a page actually shows a map
const TrackingMap = lazy(() => import('./TrackingMap'))

/** The delivery map, loaded on first use; the spinner shows its place meanwhile. */
export function LazyTrackingMap(props: TrackingMapProps) {
  return (
    <Suspense fallback={<LoadingState label="Loading the map…" className={cn('bg-surface', props.className ?? 'h-80')} />}>
      <TrackingMap {...props} />
    </Suspense>
  )
}

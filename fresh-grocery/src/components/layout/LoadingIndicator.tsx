import { useIsFetching, useIsMutating } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'
import type { DataRouter } from 'react-router'
import { useDelayedFlag } from '../../hooks/useDelayedFlag'
import { Spinner } from '../ui/Spinner'

/** Fast answers do not flash the sign: it appears once the app has waited this long. */
const SHOW_AFTER_MS = 400

/**
 * The app's busy sign, once for every page (App.tsx): "Loading…" at the top of the screen while the app waits for
 * something the user is looking at or asked for:
 * - a page that opens while its code is still downloading (see `lazy` in routes.tsx),
 * - data that is not on screen yet: a first load, or another page or filter of a list,
 * - a change being saved (any mutation).
 * Background refreshes of data already on screen (polling) do not count, and neither do requests that show their own
 * progress: those set `meta: { quiet: true }` on their useQuery or useMutation.
 */
export function LoadingIndicator({ router }: { router: DataRouter }) {
  const openingPage = useSyncExternalStore(router.subscribe, () => router.state.navigation.state !== 'idle')
  const loading = useIsFetching({ predicate: (query) => query.state.data === undefined && !query.meta?.quiet })
  const saving = useIsMutating({ predicate: (mutation) => !mutation.meta?.quiet })
  const visible = useDelayedFlag(openingPage || loading > 0 || saving > 0, SHOW_AFTER_MS)

  // The status box is always there, so screen readers notice when "Loading…" appears in it.
  // Phones: under the top bar (its middle is full there); bigger screens: in the middle of the top bar.
  return (
    <div role="status" className="pointer-events-none fixed inset-x-0 top-18 z-60 flex justify-center sm:top-3 print:hidden">
      {visible && (
        <span className="flex items-center gap-2 rounded-full border border-line bg-card px-4 py-2 text-sm font-bold text-heading shadow-lg">
          <Spinner size="sm" className="text-brand" />
          Loading…
        </span>
      )}
    </div>
  )
}

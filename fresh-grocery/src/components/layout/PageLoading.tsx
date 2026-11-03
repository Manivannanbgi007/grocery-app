import { Spinner } from '../ui/Spinner'

/**
 * Shown for a moment when the app is opened directly on a page whose code is loaded separately (see `lazy` in routes.tsx),
 * e.g. a bookmark to /shop. Later page changes keep the current page on screen until the next one is ready (with the
 * "Loading…" sign of LoadingIndicator).
 */
export function PageLoading() {
  return (
    <div role="status" className="flex min-h-screen flex-col items-center justify-center gap-3 text-brand">
      <Spinner size="lg" />
      <span className="font-heading font-bold text-heading">Loading Fresh Grocery…</span>
    </div>
  )
}

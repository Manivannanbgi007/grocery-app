import { cn } from '../../utils/cn'
import type { StreamState } from './useTrackingStream'

const LOOKS: Record<StreamState, { label: string; dot: string; pulse?: boolean }> = {
  connecting: { label: 'Connecting…', dot: 'bg-muted' },
  live: { label: 'Live', dot: 'bg-brand', pulse: true },
  reconnecting: { label: 'Reconnecting…', dot: 'bg-accent' },
  waiting: { label: 'Waiting for the delivery', dot: 'bg-muted' },
  ended: { label: 'Finished', dot: 'bg-muted' },
  failed: { label: 'Not available', dot: 'bg-hot' },
}

/** A small badge: is the page receiving live updates right now? */
export function ConnectionStatus({ state }: { state: StreamState }) {
  const look = LOOKS[state]
  return (
    <span role="status" className="inline-flex items-center gap-2 rounded-full border border-line bg-card px-3 py-1 text-sm font-bold text-heading">
      <span className="relative flex size-2.5">
        {look.pulse && <span aria-hidden="true" className={cn('absolute inline-flex size-full animate-ping rounded-full opacity-60', look.dot)} />}
        <span aria-hidden="true" className={cn('relative inline-flex size-2.5 rounded-full', look.dot)} />
      </span>
      {look.label}
    </span>
  )
}

import { cn } from '../../utils/cn'
import { Spinner } from './Spinner'

interface LoadingStateProps {
  /** what is loading, e.g. "Loading your deliveries…" */
  label?: string
  /** at least the height of what is coming (e.g. "min-h-64"), so the page does not jump when it arrives */
  className?: string
}

/** What a page or section shows while its data loads for the first time: the app's spinner and a short text. */
export function LoadingState({ label = 'Loading…', className }: LoadingStateProps) {
  return (
    <div role="status" className={cn('flex flex-col items-center justify-center gap-3 p-4 text-center', className)}>
      {/* The spinner in a soft green halo */}
      <span className="grid size-14 place-items-center rounded-full bg-brand-light shadow-lg shadow-brand/15">
        <Spinner className="text-brand" />
      </span>
      <span className="text-sm font-bold text-heading">{label}</span>
    </div>
  )
}

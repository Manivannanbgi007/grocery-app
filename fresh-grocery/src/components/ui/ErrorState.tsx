import { RotateCw } from 'lucide-react'
import { cn } from '../../utils/cn'
import { Button } from './Button'
import { EmojiIllustration } from './EmptyState'

interface ErrorStateProps {
  /** the failed request's error: its message is shown (ApiError messages are written for customers) */
  error?: unknown
  title?: string
  /** shows a "Try again" button */
  onRetry?: () => void
  /** a smaller version for widgets and menus */
  compact?: boolean
  className?: string
}

const FALLBACK_MESSAGE = 'Something went wrong. Please try again.'

/** What a section shows when its data could not be loaded. */
export function ErrorState({ error, title = 'Could not load this', onRetry, compact = false, className }: ErrorStateProps) {
  const message = error instanceof Error && error.message ? error.message : FALLBACK_MESSAGE
  return (
    <div role="alert" className={cn('flex flex-col items-center gap-2 text-center', compact ? 'p-4' : 'px-4 py-12', className)}>
      {!compact && <EmojiIllustration emoji="🥀" tone="warm" />}
      <h2 className={compact ? 'text-base' : 'mt-2 text-xl'}>{title}</h2>
      <p className="max-w-md text-sm text-body">{message}</p>
      {onRetry && (
        <Button variant="light" size="sm" onClick={onRetry} className="mt-1">
          <RotateCw className="size-4" aria-hidden="true" />
          Try again
        </Button>
      )}
    </div>
  )
}

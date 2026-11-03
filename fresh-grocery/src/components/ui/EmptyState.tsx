import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface EmptyStateProps {
  /** a big emoji above the title (shown in a soft floating circle), or your own icon element (shown as it is) */
  icon?: ReactNode
  title: string
  message?: ReactNode
  /** e.g. a "Continue shopping" button */
  action?: ReactNode
  className?: string
}

/**
 * A small illustration: the emoji in a soft green circle that floats, with two sparkles around it. Decoration only.
 * `tone` is "warm" for errors.
 */
export function EmojiIllustration({ emoji, tone = 'fresh' }: { emoji: ReactNode; tone?: 'fresh' | 'warm' }) {
  return (
    <div aria-hidden="true" className="relative motion-safe:animate-float">
      <div
        className={cn(
          'grid size-24 place-items-center rounded-full bg-linear-to-br text-5xl shadow-inner ring-8',
          tone === 'fresh' ? 'from-brand-light to-tile-1 ring-brand-light/50' : 'from-hot/15 to-new-light ring-hot/10',
        )}
      >
        {emoji}
      </div>
      <span className={cn('absolute -top-1 -right-2 text-lg', tone === 'fresh' ? 'text-accent' : 'text-hot')}>✦</span>
      <span className={cn('absolute bottom-1 -left-3 text-sm', tone === 'fresh' ? 'text-brand' : 'text-new')}>✦</span>
    </div>
  )
}

/** What a page shows when there is nothing to list: "Your cart is empty", "No products found" ... */
export function EmptyState({ icon, title, message, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-center gap-3 px-4 py-12 text-center', className)}>
      {/* An emoji gets the illustration; an element (e.g. a pulsing icon) is shown as it is */}
      {typeof icon === 'string' ? <EmojiIllustration emoji={icon} /> : icon && <div aria-hidden="true">{icon}</div>}
      <h2 className="mt-2 text-xl">{title}</h2>
      {message && <div className="max-w-md text-body">{message}</div>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}

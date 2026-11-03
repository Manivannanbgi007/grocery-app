import { ArrowLeft, ArrowRight } from 'lucide-react'
import { cn } from '../../utils/cn'

interface ScrollArrowsProps {
  /** what scrolls, for screen readers: "categories" -> "Scroll categories left" */
  label: string
  canPrev: boolean
  canNext: boolean
  onPrev: () => void
  onNext: () => void
  className?: string
}

/** The round ← → buttons next to a sideways-scrolling row (see useScrollRow). */
export function ScrollArrows({ label, canPrev, canNext, onPrev, onNext, className }: ScrollArrowsProps) {
  const arrow =
    'flex size-10 items-center justify-center rounded-full border border-line bg-card text-heading transition-colors hover:bg-brand hover:text-white disabled:cursor-default disabled:opacity-40 disabled:hover:bg-surface disabled:hover:text-heading'
  return (
    <div className={cn('flex gap-2', className)}>
      <button type="button" className={arrow} onClick={onPrev} disabled={!canPrev} aria-label={`Scroll ${label} left`}>
        <ArrowLeft className="size-4" aria-hidden="true" />
      </button>
      <button type="button" className={arrow} onClick={onNext} disabled={!canNext} aria-label={`Scroll ${label} right`}>
        <ArrowRight className="size-4" aria-hidden="true" />
      </button>
    </div>
  )
}

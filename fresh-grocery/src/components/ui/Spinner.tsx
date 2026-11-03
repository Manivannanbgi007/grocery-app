import { cn } from '../../utils/cn'

const SIZES = {
  sm: 'size-4', // in a button or a line of text
  md: 'size-8', // a section that loads
  lg: 'size-10', // a whole page
}

interface SpinnerProps {
  size?: keyof typeof SIZES
  className?: string
}

/**
 * The app's one loading spinner: a faint ring with a turning arc, in the current text colour (e.g. `text-brand`, or white
 * on a green button). Decoration only: the text next to it, or the button's aria-busy, tells screen readers.
 */
export function Spinner({ size = 'md', className }: SpinnerProps) {
  return (
    <svg viewBox="0 0 24 24" fill="none" className={cn('shrink-0 animate-spin motion-reduce:[animation-duration:2s]', SIZES[size], className)} aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="3" className="opacity-20" />
      <path d="M12 3a9 9 0 0 1 9 9" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  )
}

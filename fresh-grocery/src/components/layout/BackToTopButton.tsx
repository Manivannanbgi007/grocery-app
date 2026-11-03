import { ArrowUp } from 'lucide-react'
import { useScrolledPast } from '../../hooks/useScrolledPast'

const SHOW_AFTER_PX = 600

/** A round button at the bottom right that scrolls back to the top; appears once the page is scrolled down. */
export function BackToTopButton() {
  const visible = useScrolledPast(SHOW_AFTER_PX)

  if (!visible) return null

  return (
    <button
      type="button"
      onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      className="fixed right-5 bottom-20 z-40 flex size-11 items-center justify-center rounded-full border-2 border-heading bg-card text-heading shadow-md hover:border-brand hover:text-brand-text"
      aria-label="Back to top"
    >
      <ArrowUp className="size-5" aria-hidden="true" />
    </button>
  )
}

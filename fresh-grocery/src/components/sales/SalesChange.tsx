import { ArrowDown, ArrowUp } from 'lucide-react'
import { cn } from '../../utils/cn'
import { formatPrice } from '../../utils/format'

/**
 * How a day's sales changed from the day before: "↑ ₹300" in green, "↓ ₹120" in pink, "no change", or "–" when there
 * is no day before. Screen readers hear "up ₹300" / "down ₹120".
 */
export function SalesChange({ change }: { change: number | null }) {
  if (change === null) {
    return (
      <span>
        <span aria-hidden="true">–</span>
        <span className="sr-only">no day before</span>
      </span>
    )
  }
  if (change === 0) return <span>no change</span>
  const up = change > 0
  const Arrow = up ? ArrowUp : ArrowDown
  return (
    <span className={cn('inline-flex items-center gap-0.5 font-bold whitespace-nowrap', up ? 'text-brand-text' : 'text-hot-text')}>
      <Arrow className="size-3 shrink-0" aria-hidden="true" />
      <span className="sr-only">{up ? 'up' : 'down'}</span> {formatPrice(Math.abs(change))}
    </span>
  )
}

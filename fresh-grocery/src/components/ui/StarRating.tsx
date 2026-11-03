import { Star } from 'lucide-react'
import { cn } from '../../utils/cn'

interface StarRatingProps {
  /** average stars from 0 to 5; null when there are no reviews */
  value: number | null
  /** number of reviews; shown in brackets when given */
  count?: number
  className?: string
}

const FIVE = [1, 2, 3, 4, 5]

/**
 * Five stars, filled up to the rating (4.3 fills four stars and a bit of the fifth).
 * Two rows of stars are stacked: grey ones, and yellow ones cut off at the rating's width.
 */
export function StarRating({ value, count, className }: StarRatingProps) {
  const rating = value ?? 0
  const label = value === null ? 'No reviews yet' : `Rated ${value} out of 5${count !== undefined ? ` from ${count} ${count === 1 ? 'review' : 'reviews'}` : ''}`

  return (
    <span className={cn('inline-flex items-center gap-1.5', className)} role="img" aria-label={label}>
      <span className="relative inline-flex">
        <span className="flex text-line">
          {FIVE.map((n) => (
            <Star key={n} className="size-3.5 fill-current" aria-hidden="true" />
          ))}
        </span>
        <span className="absolute inset-0 flex overflow-hidden text-accent" style={{ width: `${(rating / 5) * 100}%` }}>
          {FIVE.map((n) => (
            <Star key={n} className="size-3.5 shrink-0 fill-current" aria-hidden="true" />
          ))}
        </span>
      </span>
      {count !== undefined && <span className="text-xs text-body">({count})</span>}
    </span>
  )
}

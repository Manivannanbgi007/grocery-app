import { useId } from 'react'
import { Link } from 'react-router'
import type { ProductSearchParams } from '../../api/types'
import { useProductSearch } from '../../hooks/useProductSearch'
import { useScrollRow } from '../../hooks/useScrollRow'
import { cn } from '../../utils/cn'
import { ErrorState } from '../ui/ErrorState'
import { ScrollArrows } from '../ui/ScrollArrows'
import { Skeleton } from '../ui/Skeleton'
import { ProductListItem } from './ProductListItem'

interface ProductRailProps {
  /** the heading, e.g. "New arrivals" */
  title: string
  /** which products, e.g. { sort: 'NEWEST', inStockOnly: true, size: 10 } */
  search: ProductSearchParams
  /** where "See all" goes, e.g. the shop sorted the same way */
  seeAllTo: string
  className?: string
}

const ITEM_CLASSES = 'flex w-72 shrink-0 snap-start items-center rounded-xl border border-line p-2.5 sm:p-3'

/**
 * A card with a title, "See all" and a row of small products that scrolls sideways (the ← → buttons scroll it too).
 * Shows nothing when there are no products.
 */
export function ProductRail({ title, search, seeAllTo, className }: ProductRailProps) {
  const headingId = useId()
  const { data, isPending, error, refetch } = useProductSearch(search)
  const products = data?.content ?? []
  const { rowRef, canPrev, canNext, scrollPrev, scrollNext } = useScrollRow<HTMLUListElement>(products.length)

  if (!isPending && !error && products.length === 0) return null

  return (
    <section aria-labelledby={headingId} className={cn('min-w-0 rounded-2xl border border-line bg-card p-3 sm:p-4', className)}>
      <div className="mb-2 flex items-center justify-between gap-3 sm:mb-3">
        <h2 id={headingId} className="text-lg">
          {title}
        </h2>
        <div className="flex items-center gap-3">
          <Link to={seeAllTo} className="text-sm font-bold whitespace-nowrap text-brand-text hover:underline">
            See all <span className="sr-only">{title.toLowerCase()}</span>
          </Link>
          {/* Phones swipe the row; the half-shown next product says there is more */}
          <ScrollArrows label={title.toLowerCase()} canPrev={canPrev} canNext={canNext} onPrev={scrollPrev} onNext={scrollNext} className="hidden sm:flex" />
        </div>
      </div>

      {error ? (
        <ErrorState compact title="Could not load" error={error} onRetry={() => void refetch()} />
      ) : (
        // relative: hidden screen reader texts (position: absolute) stay inside the row instead of widening the page
        <ul ref={rowRef} className="scrollbar-none relative flex snap-x gap-3 overflow-x-auto">
          {isPending
            ? Array.from({ length: 3 }, (_, index) => (
                <li key={index} className={cn(ITEM_CLASSES, 'gap-4')}>
                  <Skeleton className="size-20 shrink-0 rounded-xl" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-4/5" />
                    <Skeleton className="h-4 w-1/2" />
                  </div>
                </li>
              ))
            : products.map((product) => (
                <li key={product.id} className={cn(ITEM_CLASSES, 'transition-colors hover:border-brand-soft')}>
                  <ProductListItem product={product} addButton className="w-full" />
                </li>
              ))}
        </ul>
      )}
    </section>
  )
}

import { ChevronDown } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { useCategories } from '../../../hooks/useCategories'
import { useCategoryCounts } from '../../../hooks/useCategoryCounts'
import { cn } from '../../../utils/cn'
import { categoryEmoji } from '../../../utils/emoji'
import { useShopFilters } from '../useShopFilters'
import { SidebarWidget } from './SidebarWidget'

/** Categories listed before "Show all", so the filters below the list are not far down the page. */
const FIRST_CATEGORIES = 8

/**
 * "Product Categories": the categories with their number of products; the chosen one is highlighted.
 * The first 8 are listed, "Show all" lists the rest; the chosen category is always in the list.
 * showCounts=false sends no requests for the numbers (one per category): they show only when the shop has loaded them
 * already (they are cached). The product page uses it, so opening a product does not send dozens of requests at once.
 */
export function CategoryWidget({ showCounts = true }: { showCounts?: boolean }) {
  const { filters, linkWith } = useShopFilters()
  const { data: categories = [], isPending, isError, error, refetch } = useCategories()
  const counts = useCategoryCounts(categories, showCounts)
  const [showAll, setShowAll] = useState(false)
  const shown = showAll ? categories : categories.filter((category, index) => index < FIRST_CATEGORIES || category.id === filters.category)

  const row = 'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors'
  const idle = 'text-heading hover:bg-brand-light hover:text-brand-text'
  const active = 'bg-brand-light font-bold text-brand-text'

  return (
    <SidebarWidget title="Product Categories">
      {isError && <ErrorState compact title="Could not load the categories" error={error} onRetry={() => void refetch()} />}
      <ul aria-label="Product categories" className="space-y-0.5">
        <li>
          {/* Another category may not have the chosen brand, so changing the category also drops the brand */}
          <Link to={linkWith({ category: undefined, brand: undefined })} aria-current={!filters.category ? 'true' : undefined} className={cn(row, !filters.category ? active : idle)}>
            <span aria-hidden="true">🛒</span>
            <span className="flex-1">All categories</span>
          </Link>
        </li>
        {isPending
          ? Array.from({ length: 6 }, (_, index) => (
              <li key={index}>
                <Skeleton className="h-9" />
              </li>
            ))
          : shown.map((category) => {
              const selected = filters.category === category.id
              const count = counts.get(category.id)
              return (
                <li key={category.id}>
                  <Link
                    to={linkWith({ category: category.id, brand: undefined })}
                    aria-current={selected ? 'true' : undefined}
                    aria-label={count === undefined ? category.name : `${category.name}, ${count} products`}
                    className={cn(row, selected ? active : idle)}
                  >
                    <span aria-hidden="true">{categoryEmoji(category.slug)}</span>
                    <span className="flex-1">{category.name}</span>
                    {count !== undefined && (
                      <span className="flex h-6 min-w-6 items-center justify-center rounded-full bg-brand-soft px-1.5 text-xs font-bold text-heading">{count}</span>
                    )}
                  </Link>
                </li>
              )
            })}
      </ul>
      {categories.length > FIRST_CATEGORIES && (
        <button
          type="button"
          onClick={() => setShowAll(!showAll)}
          aria-expanded={showAll}
          className="mt-2 inline-flex items-center gap-1 px-3 text-sm font-bold text-brand-text hover:underline"
        >
          {showAll ? 'Show fewer' : `Show all ${categories.length} categories`}
          <ChevronDown className={cn('size-4 transition-transform', showAll && 'rotate-180')} aria-hidden="true" />
        </button>
      )}
    </SidebarWidget>
  )
}

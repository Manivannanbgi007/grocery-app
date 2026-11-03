import { X } from 'lucide-react'
import { Link } from 'react-router'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { useCategories } from '../../hooks/useCategories'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { shopLink } from '../../utils/links'
import { hasActiveFilters, priceRangeLabel } from './shopFilters'
import { useShopFilters } from './useShopFilters'

/** The chosen filters as chips: click one to remove it, or "Clear all" (the last row of ShopFilterBar). */
export function ActiveFilters({ className }: { className?: string }) {
  const { filters, linkWith } = useShopFilters()
  const { data: categories = [] } = useCategories()
  const storeName = useStoreName(filters.store)

  if (!hasActiveFilters(filters)) return null

  const categoryName = categories.find((category) => category.id === filters.category)?.name
  const chips: Array<{ label: string; to: string }> = []
  if (filters.q) chips.push({ label: `“${filters.q}”`, to: linkWith({ q: undefined }) })
  if (filters.category) chips.push({ label: categoryName ?? 'Category', to: linkWith({ category: undefined }) })
  if (filters.store) chips.push({ label: storeName ?? 'Store', to: linkWith({ store: undefined }) })
  if (filters.brand) chips.push({ label: filters.brand, to: linkWith({ brand: undefined }) })
  const priceRange = priceRangeLabel(filters)
  if (priceRange) chips.push({ label: priceRange, to: linkWith({ minPrice: undefined, maxPrice: undefined }) })
  if (filters.inStock) chips.push({ label: 'In stock only', to: linkWith({ inStock: false }) })

  return (
    <ul aria-label="Active filters" className={cn('flex flex-wrap items-center gap-2', className)}>
      {chips.map((chip) => (
        <li key={chip.label}>
          <Link
            to={chip.to}
            aria-label={`Remove filter: ${chip.label}`}
            className="inline-flex h-8 items-center gap-1.5 rounded-full bg-brand-light px-3 text-sm font-bold text-brand-text hover:bg-brand hover:text-white"
          >
            {chip.label}
            <X className="size-3.5" aria-hidden="true" />
          </Link>
        </li>
      ))}
      <li>
        {/* Clearing keeps the chosen sort order */}
        <Link to={shopLink({ sort: filters.sort })} className={buttonClasses('outline', 'sm', 'rounded-full')}>
          <X className="size-3.5" aria-hidden="true" />
          Clear all
        </Link>
      </li>
    </ul>
  )
}

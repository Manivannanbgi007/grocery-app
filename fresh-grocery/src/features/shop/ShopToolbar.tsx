import { useId } from 'react'
import type { ProductSort } from '../../api/types'
import { Select } from '../../components/ui/Select'
import { SORT_OPTIONS } from './shopFilters'
import { useShopFilters } from './useShopFilters'

/** "We found 59 items for you!" and the sort order. */
export function ShopToolbar({ total }: { total: number | undefined }) {
  const { filters, apply } = useShopFilters()
  const selectId = useId()

  return (
    <div className="flex flex-wrap items-center justify-between gap-4">
      {/* aria-live: screen readers announce the new number when the filters change */}
      <p aria-live="polite" className="text-body">
        {total === undefined ? (
          'Loading products…'
        ) : (
          <>
            We found <strong className="text-brand-text">{total}</strong> {total === 1 ? 'item' : 'items'} for you!
          </>
        )}
      </p>

      <div className="flex items-center gap-2">
        <span id={selectId} className="text-sm whitespace-nowrap">
          Sort by:
        </span>
        <Select size="sm" labelledBy={selectId} value={filters.sort} onChange={(sort) => apply({ sort: sort as ProductSort })} options={SORT_OPTIONS} fitOptions />
      </div>
    </div>
  )
}

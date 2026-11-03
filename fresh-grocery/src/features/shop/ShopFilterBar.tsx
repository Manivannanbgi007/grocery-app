import { ChevronDown, IndianRupee, Store, Tag } from 'lucide-react'
import { useId, useState, type ReactNode } from 'react'
import { useStoreName, useStores } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { ActiveFilters } from './ActiveFilters'
import { BrandOptions } from './filters/BrandOptions'
import { PriceForm } from './filters/PriceForm'
import { StoreOptions } from './filters/StoreOptions'
import { priceRangeLabel } from './shopFilters'
import { useShopFilters } from './useShopFilters'

type FilterName = 'store' | 'brand' | 'price'

/** The screen reader name of the choices of each button. */
const CHOICES_LABELS: Record<FilterName, string> = {
  store: 'Choose a store',
  brand: 'Choose a brand',
  price: 'Choose a price range',
}

interface FilterButtonProps {
  id: string
  label: string
  /** the chosen value, shown after the label ("Store: Fresh Mart"); the button is green while there is one */
  value: string | undefined
  icon: ReactNode
  open: boolean
  choicesId: string
  onClick: () => void
}

/** A round button of the filter bar that opens (and closes) its choices. */
function FilterButton({ id, label, value, icon, open, choicesId, onClick }: FilterButtonProps) {
  return (
    <button
      type="button"
      id={id}
      aria-expanded={open}
      aria-controls={open ? choicesId : undefined}
      onClick={onClick}
      className={cn(
        'inline-flex h-9 max-w-full items-center gap-1.5 rounded-full border px-3.5 font-heading text-sm font-bold transition-colors',
        value ? 'border-brand bg-brand-light text-brand-text' : 'border-line bg-card text-heading hover:border-brand hover:text-brand-text',
        open && 'border-brand ring-1 ring-brand',
      )}
    >
      {icon}
      <span className="truncate">
        {label}
        {value && <span className="font-normal">: {value}</span>}
      </span>
      <ChevronDown className={cn('size-4 shrink-0 transition-transform', open && 'rotate-180')} aria-hidden="true" />
    </button>
  )
}

/**
 * The shop's filters above the products, on every screen size: Store, Brand and Price open their choices inside
 * the bar, below the buttons (not as a floating menu, so they never go off a phone's screen); "In stock only"
 * works at once. The chosen filters follow as chips with "Clear all". Everything is in the URL (useShopFilters).
 */
export function ShopFilterBar() {
  const { filters, apply } = useShopFilters()
  const { data: stores = [] } = useStores()
  const storeName = useStoreName(filters.store)
  const [openFilter, setOpenFilter] = useState<FilterName | null>(null)
  const id = useId()
  const choicesId = `${id}-choices`

  function toggle(name: FilterName) {
    setOpenFilter(openFilter === name ? null : name)
  }

  /** After a choice: close the choices, and put the keyboard focus back on their button. */
  function close() {
    if (openFilter) document.getElementById(`${id}-${openFilter}`)?.focus()
    setOpenFilter(null)
  }

  function filterButton(name: FilterName, label: string, value: string | undefined, icon: ReactNode) {
    return <FilterButton id={`${id}-${name}`} label={label} value={value} icon={icon} open={openFilter === name} choicesId={choicesId} onClick={() => toggle(name)} />
  }

  return (
    <section aria-label="Filters" className="rounded-2xl border border-line bg-card p-3 sm:p-4">
      <div className="flex flex-wrap items-center gap-2">
        {/* With one store there is nothing to choose */}
        {stores.length > 1 && filterButton('store', 'Store', storeName, <Store className="size-4 shrink-0" aria-hidden="true" />)}
        {filterButton('brand', 'Brand', filters.brand, <Tag className="size-4 shrink-0" aria-hidden="true" />)}
        {filterButton('price', 'Price', priceRangeLabel(filters), <IndianRupee className="size-4 shrink-0" aria-hidden="true" />)}
        <label className="inline-flex h-9 cursor-pointer items-center gap-2 rounded-full border border-line px-3.5 font-heading text-sm font-bold text-heading transition-colors hover:border-brand has-checked:border-brand has-checked:bg-brand-light has-checked:text-brand-text">
          <input type="checkbox" checked={filters.inStock} onChange={(event) => apply({ inStock: event.target.checked })} className="size-4 accent-brand" />
          In stock only
        </label>
      </div>

      {openFilter && (
        <div id={choicesId} role="group" aria-label={CHOICES_LABELS[openFilter]} className="mt-3 border-t border-line pt-4">
          {openFilter === 'store' && <StoreOptions onChoose={close} />}
          {openFilter === 'brand' && <BrandOptions onChoose={close} />}
          {openFilter === 'price' && (
            // A new key empties the boxes when the price in the URL changes (a chip, "Clear all", the Back button)
            <PriceForm
              key={`${filters.minPrice ?? ''}-${filters.maxPrice ?? ''}`}
              minPrice={filters.minPrice}
              maxPrice={filters.maxPrice}
              onApply={(changes) => {
                apply(changes)
                close()
              }}
            />
          )}
        </div>
      )}

      <ActiveFilters className="mt-3 border-t border-line pt-3" />
    </section>
  )
}

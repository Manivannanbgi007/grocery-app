import { Link } from 'react-router'
import { useStores } from '../../../hooks/useStores'
import { cn } from '../../../utils/cn'
import { useShopFilters } from '../useShopFilters'

interface StoreOptionsProps {
  /** called after a store is clicked (the filter bar closes its choices) */
  onChoose: () => void
}

/** The filter bar's store choices: show the products of one store (click it again to see every store's products). */
export function StoreOptions({ onChoose }: StoreOptionsProps) {
  const { filters, linkWith } = useShopFilters()
  const { data: stores = [] } = useStores()

  return (
    <>
      <ul aria-label="Stores" className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
        {stores.map((store) => {
          const selected = store.id === filters.store
          return (
            <li key={store.id}>
              <Link
                to={linkWith({ store: selected ? undefined : store.id })}
                onClick={onChoose}
                aria-current={selected ? 'true' : undefined}
                className={cn(
                  'flex items-center justify-between gap-2 rounded-xl border px-3 py-2.5 text-sm transition-colors',
                  selected ? 'border-brand bg-brand text-white' : 'border-line text-heading hover:border-brand hover:bg-brand-light hover:text-brand-text',
                )}
              >
                <span className="font-bold">{store.name}</span>
                <span className="text-xs whitespace-nowrap">{store.openNow ? 'Open now' : 'Closed now'}</span>
              </Link>
            </li>
          )
        })}
      </ul>
      <Link to="/stores" className="mt-3 inline-block text-sm font-bold text-brand-text hover:underline">
        About our stores
      </Link>
    </>
  )
}

import { Store as StoreIcon } from 'lucide-react'
import { Link } from 'react-router'
import { useStores } from '../../hooks/useStores'

/** "Sold by Fresh Mart": where the product comes from, whether the store is open, and the one-store cart rule. */
export function StoreInfo({ storeId }: { storeId: number }) {
  const { data: stores } = useStores()
  const store = stores?.find((item) => item.id === storeId)
  if (!store) return null

  return (
    <div className="flex gap-4 rounded-2xl border border-line bg-card p-5">
      <StoreIcon className="size-8 shrink-0 text-brand" strokeWidth={1.5} aria-hidden="true" />
      <div className="space-y-1 text-sm">
        <p>
          Sold by{' '}
          <Link to={`/stores/${store.id}`} className="font-heading text-base font-bold text-heading hover:text-brand-text hover:underline">
            {store.name}
          </Link>
          {' · '}
          <span className={store.openNow ? 'font-bold text-brand-text' : 'font-bold text-hot'}>{store.openNow ? 'Open now' : 'Closed now'}</span>
        </p>
        <p>
          {store.addressLine1}, {store.city}
        </p>
        <p>One order holds products of one store, so your cart can only contain products from one store at a time.</p>
      </div>
    </div>
  )
}

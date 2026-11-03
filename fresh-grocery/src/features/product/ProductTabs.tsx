import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import type { Product } from '../../api/types'
import { StockText } from '../../components/product/StockText'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { formatUnit } from '../../utils/format'
import { shopLink } from '../../utils/links'
import { ReviewList } from './ReviewList'

export type ProductTab = 'description' | 'details' | 'reviews'

interface ProductTabsProps {
  product: Product
  tab: ProductTab
  onTabChange: (tab: ProductTab) => void
}

/**
 * Description, Additional information and Reviews in one card (the WAI-ARIA tabs pattern): the tabs are one Tab stop,
 * ← → Home End move between them. The page keeps which tab is open, so the stars at the top can open the reviews.
 */
export function ProductTabs({ product, tab, onTabChange }: ProductTabsProps) {
  const id = useId()
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({})
  const tabs: { name: ProductTab; label: string }[] = [
    { name: 'description', label: 'Description' },
    { name: 'details', label: 'Additional information' },
    { name: 'reviews', label: `Reviews (${product.ratingCount})` },
  ]

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const index = tabs.findIndex((item) => item.name === tab)
    const next = { ArrowRight: index + 1, ArrowLeft: index - 1, Home: 0, End: tabs.length - 1 }[event.key]
    if (next === undefined) return
    event.preventDefault()
    const target = tabs[(next + tabs.length) % tabs.length].name
    onTabChange(target)
    tabRefs.current[target]?.focus()
  }

  return (
    <div className="rounded-2xl border border-line bg-card p-5 sm:p-8">
      <div role="tablist" aria-label="About this product" onKeyDown={handleKeyDown} className="flex flex-wrap gap-2 sm:gap-3">
        {tabs.map((item) => {
          const selected = item.name === tab
          return (
            <button
              key={item.name}
              ref={(element) => {
                tabRefs.current[item.name] = element
              }}
              type="button"
              role="tab"
              id={`${id}-${item.name}-tab`}
              aria-selected={selected}
              aria-controls={`${id}-${item.name}-panel`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onTabChange(item.name)}
              className={cn(
                'rounded-full border px-4 py-2 font-heading text-sm font-bold transition-colors sm:px-6 sm:py-2.5 sm:text-base',
                selected ? 'border-line bg-card text-brand-text shadow-md shadow-heading/10' : 'border-line text-body hover:text-brand-text',
              )}
            >
              {item.label}
            </button>
          )
        })}
      </div>

      <div role="tabpanel" id={`${id}-${tab}-panel`} aria-labelledby={`${id}-${tab}-tab`} tabIndex={0} className="mt-8 rounded-md focus-visible:outline-offset-4">
        {tab === 'description' && <DescriptionPanel product={product} />}
        {tab === 'details' && <DetailsPanel product={product} />}
        {tab === 'reviews' && <ReviewList productId={product.id} />}
      </div>
    </div>
  )
}

function DescriptionPanel({ product }: { product: Product }) {
  const storeName = useStoreName(product.storeId) ?? 'The store'
  return (
    <div className="max-w-3xl space-y-4 leading-relaxed">
      <h3 className="text-xl">
        {product.name}, {formatUnit(product.unit, product.unitQuantity)}
      </h3>
      <p>{product.description ?? 'The store has not written a description of this product yet.'}</p>
      <h3 className="pt-2 text-xl">How it reaches you</h3>
      <p>
        {storeName} packs your order and a delivery partner brings it to your door. You can follow the delivery live on a map, and pay online or in cash on delivery. One
        order holds the products of one store.
      </p>
    </div>
  )
}

function DetailsPanel({ product }: { product: Product }) {
  const storeName = useStoreName(product.storeId)
  const rows: [string, ReactNode][] = [
    ['Brand', product.brand ?? '–'],
    ['Pack size', formatUnit(product.unit, product.unitQuantity)],
    [
      'Category',
      <Link key="category" to={shopLink({ category: product.categoryId })} className="text-brand-text hover:underline">
        {product.categoryName}
      </Link>,
    ],
    ['SKU', product.sku],
    ['GST', `${product.taxPercentage} % (included in the price)`],
    [
      'Sold by',
      <Link key="store" to={`/stores/${product.storeId}`} className="text-brand-text hover:underline">
        {storeName ?? 'The store'}
      </Link>,
    ],
    ['Stock', <StockText key="stock" product={product} />],
  ]

  return (
    <table className="w-full max-w-2xl text-left text-sm">
      <caption className="sr-only">Additional information</caption>
      <tbody className="divide-y divide-line">
        {rows.map(([label, value]) => (
          <tr key={label}>
            <th scope="row" className="w-40 py-3 pr-4 font-heading font-bold text-heading">
              {label}
            </th>
            <td className="py-3 text-body">{value}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

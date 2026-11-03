import { X } from 'lucide-react'
import { Link } from 'react-router'
import type { Product } from '../../../api/types'
import { useProductSearch } from '../../../hooks/useProductSearch'
import { cn } from '../../../utils/cn'
import { useShopFilters } from '../useShopFilters'

const MAX_BRANDS = 15

/** The brands of these products, the most frequent first. */
function topBrands(products: Product[]): string[] {
  const counts = new Map<string, number>()
  for (const product of products) {
    if (product.brand) counts.set(product.brand, (counts.get(product.brand) ?? 0) + 1)
  }
  return [...counts.entries()]
    .sort(([brandA, countA], [brandB, countB]) => countB - countA || brandA.localeCompare(brandB))
    .slice(0, MAX_BRANDS)
    .map(([brand]) => brand)
}

interface BrandOptionsProps {
  /** called after a brand is clicked (the filter bar closes its choices) */
  onChoose: () => void
}

/**
 * The filter bar's brand choices: click a brand to show only its products, click it again to show all.
 * There is no brands endpoint, so the brands are collected from up to 100 products of the chosen category
 * (loaded when the choices open).
 */
export function BrandOptions({ onChoose }: BrandOptionsProps) {
  const { filters, linkWith } = useShopFilters()
  const { data, isPending } = useProductSearch({ categoryId: filters.category, size: 100 })
  const brands = topBrands(data?.content ?? [])
  // The chosen brand stays visible even if it is not among the top ones
  if (filters.brand && !brands.includes(filters.brand)) brands.unshift(filters.brand)

  if (brands.length === 0) return <p className="text-sm">{isPending ? 'Loading the brands…' : 'These products have no brand names.'}</p>

  return (
    <ul aria-label="Brands" className="flex flex-wrap gap-2">
      {brands.map((brand) => {
        const selected = brand === filters.brand
        return (
          <li key={brand}>
            <Link
              to={linkWith({ brand: selected ? undefined : brand })}
              onClick={onChoose}
              aria-current={selected ? 'true' : undefined}
              aria-label={selected ? `Remove brand filter: ${brand}` : undefined}
              className={cn(
                'inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-sm transition-colors',
                selected ? 'border-brand bg-brand text-white' : 'border-line text-brand-text hover:border-brand hover:bg-brand-light',
              )}
            >
              {selected && <X className="size-3.5" aria-hidden="true" />}
              {brand}
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

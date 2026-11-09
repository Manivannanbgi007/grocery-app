import type { Product } from '../../api/types'
import { cn } from '../../utils/cn'
import { ProductCard } from './ProductCard'
import { ProductCardSkeleton } from './ProductCardSkeleton'

/** Cards per row: small cards fit more in a row (2 on phones, up to 6 from 1280 px). */
const COLUMNS = {
  regular: 'grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5',
  small: 'grid-cols-2 gap-2 sm:grid-cols-3 sm:gap-3 md:grid-cols-4 xl:grid-cols-6',
}

interface ProductGridProps {
  products: Product[] | undefined
  /** true while the first page loads: skeleton cards are shown */
  isPending: boolean
  skeletonCount?: number
  /** true while another page or filter loads: the current cards stay, faded, and the list says it is busy */
  isUpdating?: boolean
  /** small = the denser card (ProductCard size="small") and more cards per row */
  cardSize?: 'regular' | 'small'
  className?: string
}

/** Product cards in responsive columns: 2 on phones, up to 5 (small cards: 6) on wide screens. Loading, empty and error states are up to the caller. */
export function ProductGrid({ products, isPending, skeletonCount = 10, isUpdating = false, cardSize = 'regular', className }: ProductGridProps) {
  return (
    <ul aria-busy={isUpdating || undefined} className={cn('grid transition-opacity', COLUMNS[cardSize], isUpdating && 'opacity-50', className)}>
      {isPending
        ? Array.from({ length: skeletonCount }, (_, index) => (
            <li key={index}>
              <ProductCardSkeleton size={cardSize} />
            </li>
          ))
        : products?.map((product, index) => (
            // The cards rise in one after another; the stagger stops growing after 12, so a long page never waits
            <li key={product.id} className="motion-safe:animate-rise" style={{ animationDelay: `${Math.min(index, 11) * 40}ms` }}>
              <ProductCard product={product} size={cardSize} />
            </li>
          ))}
    </ul>
  )
}

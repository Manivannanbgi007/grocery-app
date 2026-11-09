import { Link } from 'react-router'
import type { Product } from '../../api/types'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { formatUnit } from '../../utils/format'
import { Badge } from '../ui/Badge'
import { Price } from '../ui/Price'
import { StarRating } from '../ui/StarRating'
import { AddToCartButton } from './AddToCartButton'
import { CompareButton } from './CompareButton'
import { productBadge } from './productBadge'
import { ProductImage } from './ProductImage'
import { WishlistButton } from './WishlistButton'

interface ProductCardProps {
  product: Product
  /** compact = small "Add" next to the price (grids); full = big "Add to cart" under the price (carousels) */
  addButton?: 'compact' | 'full'
  /**
   * regular = the card of the shop's grids; small = a denser card for many products in a row (Popular Products):
   * less padding, a smaller picture and no category label (the category tabs above the row already say it)
   */
  size?: 'regular' | 'small'
}

/**
 * One product in a grid. The picture fills the top of the card at 4:3 (not square), with the badge, the heart and the
 * compare button on it, and the category as a small label in its corner. Under it: name, pack size and store on one
 * line, stars, then price and Add. The store name matters: a cart can only hold products of one store.
 *
 * Movement (all motion-safe, so "reduce motion" keeps the card still): on hover the card lifts a little, its picture
 * zooms in, and with a mouse the compare button slides in (styles/index.css, "card-reveal"; always shown on touch
 * screens and once the product is chosen).
 */
export function ProductCard({ product, addButton = 'compact', size = 'regular' }: ProductCardProps) {
  const storeName = useStoreName(product.storeId)
  const badge = productBadge(product)
  const productUrl = `/products/${product.id}`
  const small = size === 'small'

  return (
    <article
      className={cn(
        'group relative flex h-full flex-col overflow-hidden rounded-2xl border border-line bg-card',
        'transition duration-300 ease-out hover:border-brand-soft hover:shadow-xl hover:shadow-heading/10 focus-within:border-brand-soft motion-safe:hover:-translate-y-1',
      )}
    >
      <div className="relative">
        {/* The picture repeats the name link below, so keyboard and screen reader users skip it */}
        <Link to={productUrl} tabIndex={-1} aria-hidden="true" className="block overflow-hidden bg-linear-to-br from-surface to-brand-light/70">
          <div className="transition-transform duration-500 ease-out motion-safe:group-hover:scale-110">
            <ProductImage
              product={product}
              className={cn('rounded-none bg-transparent', small ? 'aspect-[3/2]' : 'aspect-[4/3]')}
              emojiClassName={cn('drop-shadow-md', small ? 'text-6xl' : 'text-6xl sm:text-7xl')}
            />
          </div>
        </Link>

        {badge && (
          <Badge tone={badge.tone} className={cn('absolute top-2 left-2 z-10 shadow-sm', small ? 'px-2' : 'sm:top-2.5 sm:left-2.5')}>
            {badge.label}
          </Badge>
        )}
        <div className={cn('absolute z-10 flex flex-col gap-1.5', small ? 'top-2 right-2' : 'top-2 right-2 sm:top-2.5 sm:right-2.5')}>
          <WishlistButton product={product} className="size-8 shadow-sm" />
          <CompareButton product={product} className="card-reveal size-8 shadow-sm" />
        </div>
        {!small && product.categoryName && (
          <span className="absolute bottom-2 left-2 max-w-[calc(100%-1rem)] truncate rounded-full bg-glass-strong px-2.5 py-0.5 text-[11px] font-semibold text-heading shadow-sm backdrop-blur-sm sm:bottom-2.5 sm:left-2.5">
            {product.categoryName}
          </span>
        )}
      </div>

      <div className={cn('flex flex-1 flex-col', small ? 'p-2.5' : 'p-3')}>
        <h3 className={cn('leading-snug', small ? 'text-sm' : 'text-[0.95rem]')}>
          <Link to={productUrl} className="line-clamp-2 transition-colors hover:text-brand-text">
            {product.name}
          </Link>
        </h3>

        {/* The details sit at the bottom of the card: they line up across a row of cards whether a name takes one line
            or two, and a row of short names leaves no empty line */}
        <div className="mt-auto pt-1">
          <p className="flex min-w-0 items-center gap-1.5 text-xs">
            <span className="shrink-0">{formatUnit(product.unit, product.unitQuantity)}</span>
            {storeName && (
              <>
                <span className="size-1 shrink-0 rounded-full bg-muted" aria-hidden="true" />
                <span className="truncate">
                  By <span className="text-brand-text">{storeName}</span>
                </span>
              </>
            )}
          </p>
          <StarRating value={product.ratingAverage} count={product.ratingCount} className="mt-1.5" />
        </div>

        <div className={small ? 'pt-2.5' : 'pt-3'}>
          {addButton === 'full' && !small ? (
            <>
              <Price sellingPrice={product.sellingPrice} price={product.price} />
              <AddToCartButton product={product} variant="full" className="mt-2.5 w-full" />
            </>
          ) : (
            // One line: when there is no room, the crossed-out price goes under the price, never "Add" under both
            <div className="flex items-end justify-between gap-2">
              <Price sellingPrice={product.sellingPrice} price={product.price} size={small ? 'sm' : 'md'} className="min-w-0" />
              <AddToCartButton product={product} className="shrink-0" />
            </div>
          )}
        </div>
      </div>
    </article>
  )
}

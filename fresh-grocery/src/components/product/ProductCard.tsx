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
   * less padding, a wider than high picture, no category line, the pack size and stars on one line
   */
  size?: 'regular' | 'small'
}

/**
 * One product in a grid: badge, wishlist heart and compare button, image, category, name, pack size, stars, store,
 * price and Add.
 * The store name matters: a cart can only hold products of one store.
 */
export function ProductCard({ product, addButton = 'compact', size = 'regular' }: ProductCardProps) {
  const storeName = useStoreName(product.storeId)
  const badge = productBadge(product)
  const productUrl = `/products/${product.id}`
  const small = size === 'small'

  return (
    <article
      className={cn(
        'relative flex h-full flex-col rounded-2xl border border-line bg-card transition hover:border-brand-soft hover:shadow-lg hover:shadow-heading/5',
        small ? 'p-2.5' : 'p-3 sm:p-4',
      )}
    >
      {badge && (
        <Badge tone={badge.tone} className={cn('absolute top-0 left-0 z-10 rounded-none rounded-tl-2xl rounded-br-2xl', small ? 'px-2.5 py-1' : 'px-4 py-1.5')}>
          {badge.label}
        </Badge>
      )}
      <div className={cn('absolute z-10 flex flex-col', small ? 'top-2 right-2 gap-1.5' : 'top-3 right-3 gap-2')}>
        <WishlistButton product={product} className={small ? 'size-8' : undefined} />
        <CompareButton product={product} className={small ? 'size-8' : undefined} />
      </div>

      {/* The image repeats the name link below, so keyboard and screen reader users skip it */}
      <Link to={productUrl} tabIndex={-1} aria-hidden="true" className={cn('block', !small && 'px-2 pt-5 pb-2')}>
        <ProductImage product={product} className={small ? 'aspect-[4/3]' : undefined} emojiClassName={small ? 'text-5xl' : undefined} />
      </Link>

      {!small && <p className="text-xs">{product.categoryName}</p>}
      {/* Always two lines high, so the pack size, stars, store and price line up across a row of cards */}
      <h3 className={cn('min-h-[2lh] leading-snug', small ? 'mt-2 text-sm' : 'mt-1 text-base')}>
        <Link to={productUrl} className="line-clamp-2 hover:text-brand-text">
          {product.name}
        </Link>
      </h3>
      {small ? (
        <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5">
          <p className="text-xs">{formatUnit(product.unit, product.unitQuantity)}</p>
          <StarRating value={product.ratingAverage} count={product.ratingCount} />
        </div>
      ) : (
        <>
          <p className="mt-0.5 text-xs">{formatUnit(product.unit, product.unitQuantity)}</p>
          <StarRating value={product.ratingAverage} count={product.ratingCount} className="mt-2" />
        </>
      )}
      {storeName && (
        <p className={cn('mt-1', small ? 'truncate text-xs' : 'text-sm')}>
          By <span className="text-brand-text">{storeName}</span>
        </p>
      )}

      <div className={cn('mt-auto', small ? 'pt-2.5' : 'pt-4')}>
        {small ? (
          // One line: when there is no room, the crossed-out price goes under the price, never "Add" under both
          <div className="flex items-end justify-between gap-2">
            <Price sellingPrice={product.sellingPrice} price={product.price} size="sm" className="min-w-0" />
            <AddToCartButton product={product} className="shrink-0" />
          </div>
        ) : addButton === 'compact' ? (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Price sellingPrice={product.sellingPrice} price={product.price} />
            <AddToCartButton product={product} />
          </div>
        ) : (
          <>
            <Price sellingPrice={product.sellingPrice} price={product.price} />
            <AddToCartButton product={product} variant="full" className="mt-3 w-full" />
          </>
        )}
      </div>
    </article>
  )
}

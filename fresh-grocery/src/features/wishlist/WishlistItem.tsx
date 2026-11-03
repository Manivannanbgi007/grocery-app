import { X } from 'lucide-react'
import { Link } from 'react-router'
import type { Product } from '../../api/types'
import { AddToCartButton } from '../../components/product/AddToCartButton'
import { ProductImage } from '../../components/product/ProductImage'
import { StockText } from '../../components/product/StockText'
import { Price } from '../../components/ui/Price'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { formatDate, formatUnit } from '../../utils/format'
import type { SavedProduct } from './useWishlist'

interface WishlistItemProps {
  saved: SavedProduct
  /** today's price and stock; undefined while they load */
  product: Product | undefined
  missing: boolean
  /** leave out where the product cannot be removed (a shared wishlist) */
  onRemove?: () => void
  /** a click on the product's name or picture (the pop-up closes) */
  onOpen?: () => void
  /** 0, 1, 2 …: only for the look, the rows rise in one after the other */
  order?: number
}

/**
 * One product of the wishlist, in the pop-up and on the wishlist page: remove, picture, name, prices, the day it was
 * added, the stock and Add to cart. The row lays itself out by the width of its list (@container on the <ul>), not by
 * the screen: side by side from 36rem, the stock and the button on a line of their own below that.
 */
export function WishlistItem({ saved, product, missing, onRemove, onOpen, order = 0 }: WishlistItemProps) {
  const storeName = useStoreName(saved.storeId)
  // Today's price when it has arrived; the saved one until then
  const prices = product ?? saved
  const productUrl = `/products/${saved.id}`

  return (
    // The row rises in (60 ms after the one above it) and lights up in light pink under the mouse ("reduce motion" turns
    // the rising off)
    <li
      className={cn(
        'grid items-center gap-x-4 gap-y-3 px-4 py-4 transition-colors duration-200 hover:bg-hot/5 motion-safe:animate-rise @xl:px-5',
        onRemove ? 'grid-cols-[2rem_4rem_1fr] @xl:grid-cols-[2.25rem_5rem_1fr_auto]' : 'grid-cols-[4rem_1fr] @xl:grid-cols-[5rem_1fr_auto]',
      )}
      style={{ animationDelay: `${100 + order * 60}ms` }}
    >
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remove ${saved.name} from wishlist`}
          className="flex size-8 items-center justify-center rounded-full border border-brand-soft bg-card text-brand-text transition-colors hover:border-hot hover:bg-hot hover:text-white @xl:size-9"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      )}
      <Link to={productUrl} onClick={onOpen} tabIndex={-1} aria-hidden="true" className="block overflow-hidden rounded-xl transition-transform duration-300 hover:scale-105">
        <ProductImage product={saved} emojiClassName="text-3xl @xl:text-4xl" />
      </Link>
      <div className="min-w-0 space-y-1">
        <Link to={productUrl} onClick={onOpen} className="line-clamp-2 font-heading font-bold text-heading hover:text-brand-text">
          {saved.name}
        </Link>
        <Price sellingPrice={prices.sellingPrice} price={prices.price} size="sm" />
        <p className="text-sm">
          {formatUnit(saved.unit, saved.unitQuantity)}
          {storeName && ` · ${storeName}`}
          {saved.addedAt && ` · Added ${formatDate(saved.addedAt)}`}
        </p>
      </div>
      <div className="col-span-full flex items-center justify-between gap-3 text-sm @xl:col-span-1 @xl:flex-col @xl:items-end">
        <StockText product={product} missing={missing} />
        {product && <AddToCartButton product={product} variant="full" />}
      </div>
    </li>
  )
}

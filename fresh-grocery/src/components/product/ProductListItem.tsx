import { Link } from 'react-router'
import type { Product } from '../../api/types'
import { cn } from '../../utils/cn'
import { Price } from '../ui/Price'
import { StarRating } from '../ui/StarRating'
import { AddToCartButton } from './AddToCartButton'
import { ProductImage } from './ProductImage'

interface ProductListItemProps {
  product: Product
  /** shows a small "Add" next to the price (the shop's highlights) */
  addButton?: boolean
  className?: string
}

/** A small product row (image, name, stars, price) for the home page columns, the shop sidebar and the shop's highlights. */
export function ProductListItem({ product, addButton = false, className }: ProductListItemProps) {
  const productUrl = `/products/${product.id}`
  const price = <Price sellingPrice={product.sellingPrice} price={product.price} size="sm" />
  return (
    <div className={cn('flex items-center gap-4', className)}>
      <Link to={productUrl} tabIndex={-1} aria-hidden="true" className="w-20 shrink-0 transition-transform hover:-translate-y-1">
        <ProductImage product={product} emojiClassName="text-4xl" />
      </Link>
      {/* flex-col puts the name, the stars and the price on lines of their own */}
      <div className="flex min-w-0 flex-1 flex-col items-start gap-1">
        <h4 className="text-sm leading-snug">
          <Link to={productUrl} className="line-clamp-2 hover:text-brand-text">
            {product.name}
          </Link>
        </h4>
        <StarRating value={product.ratingAverage} count={product.ratingCount} />
        {addButton ? (
          <div className="flex w-full flex-wrap items-center justify-between gap-2">
            {price}
            <AddToCartButton product={product} />
          </div>
        ) : (
          price
        )}
      </div>
    </div>
  )
}

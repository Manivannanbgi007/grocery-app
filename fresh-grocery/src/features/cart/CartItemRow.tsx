import { Trash2 } from 'lucide-react'
import { Link } from 'react-router'
import type { CartItem, Product } from '../../api/types'
import { ProductImage } from '../../components/product/ProductImage'
import { Price } from '../../components/ui/Price'
import { QuantityStepper } from '../../components/ui/QuantityStepper'
import { formatPrice, formatUnit } from '../../utils/format'

/** The cart accepts 1 to 20 of a product. */
const MAX_QUANTITY = 20

/**
 * The columns of the cart table: picture · product · price · quantity · subtotal · remove. They need the card (the
 * @container in CartPage) to be at least 48rem (768 px) wide ("@3xl:"): the card's width counts, not the screen's,
 * because next to the summary (from 1024 px) the card can be narrower than on a tablet.
 * CartPage uses the same value for the column titles, so titles and rows always line up.
 */
export const CART_COLUMNS = '@3xl:grid-cols-[4rem_minmax(0,1fr)_6.5rem_8rem_5.5rem_2.25rem]'

interface CartItemRowProps {
  item: CartItem
  /** today's product (for the stock limit and the category picture), undefined while loading */
  product: Product | undefined
  /** true while a change of this row is being saved */
  saving: boolean
  onQuantityChange: (quantity: number) => void
  onRemove: () => void
  /** 0, 1, 2 …: only for the look, the rows rise in one after the other when the page opens */
  order?: number
}

/**
 * One cart line. In a wide card a row of the table (the columns of CART_COLUMNS). In a narrow one (phone, or a small
 * laptop) two lines: picture, name and price with the remove button on the right; below them the quantity and the
 * subtotal. The labels "Price", "Quantity"
 * and "Subtotal" are for screen readers (the column titles are only drawn).
 */
export function CartItemRow({ item, product, saving, onQuantityChange, onRemove, order = 0 }: CartItemRowProps) {
  const maxQuantity = Math.min(MAX_QUANTITY, product?.availableQuantity ?? MAX_QUANTITY)
  const tooFewInStock = product !== undefined && product.availableQuantity < item.quantity
  const productUrl = `/products/${item.productId}`

  return (
    // The row rises in (60 ms after the one above it) and lights up in light orange under the mouse; "reduce motion"
    // turns the rising off
    <li
      className={`grid grid-cols-[3.5rem_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-2 px-4 py-3 transition-colors duration-200 hover:bg-new-light/50 motion-safe:animate-rise sm:px-5 @3xl:gap-x-4 ${CART_COLUMNS}`}
      style={{ animationDelay: `${150 + order * 60}ms` }}
    >
      {/* The picture grows a little under the mouse */}
      <Link
        to={productUrl}
        tabIndex={-1}
        aria-hidden="true"
        className="row-span-2 self-start overflow-hidden rounded-xl transition-transform duration-300 hover:scale-105 @3xl:row-span-1 @3xl:self-center"
      >
        <ProductImage product={{ name: item.productName, categoryId: product?.categoryId ?? 0, imageUrls: item.imageUrl ? [item.imageUrl] : [] }} emojiClassName="text-3xl" />
      </Link>

      <div className="min-w-0">
        <Link to={productUrl} className="line-clamp-2 font-heading font-bold text-heading hover:text-brand-text">
          {item.productName}
        </Link>
        <p className="text-sm">{formatUnit(item.unit, item.unitQuantity)}</p>
        {tooFewInStock && (
          <p className="mt-1 inline-flex rounded-full bg-hot/10 px-2.5 py-0.5 text-xs font-bold text-hot-text">
            {product.availableQuantity === 0 ? 'Out of stock now' : `Only ${product.availableQuantity} left in stock`}
          </p>
        )}
        {/* In a narrow card the price sits under the name; in a wide one it has its own column */}
        <div className="mt-1 @3xl:hidden">
          <Price sellingPrice={item.sellingPrice} price={item.price} size="sm" />
        </div>
      </div>

      <div className="hidden @3xl:block">
        <span className="sr-only">Price</span>
        <Price sellingPrice={item.sellingPrice} price={item.price} size="sm" />
      </div>

      {/* Phone: second line, under the name */}
      <div className="col-start-2 row-start-2 @3xl:col-start-auto @3xl:row-start-auto">
        <QuantityStepper value={item.quantity} onChange={onQuantityChange} max={Math.max(maxQuantity, 1)} disabled={saving} label={`Quantity of ${item.productName}`} />
      </div>

      <div className="col-start-3 row-start-2 text-right @3xl:col-start-auto @3xl:row-start-auto">
        <span className="sr-only">Subtotal</span>
        <span className="font-heading text-base font-bold text-heading">{formatPrice(item.lineTotal)}</span>
      </div>

      {/* Phone: top right, next to the name */}
      <button
        type="button"
        onClick={onRemove}
        disabled={saving}
        aria-label={`Remove ${item.productName} from cart`}
        title="Remove"
        className="col-start-3 row-start-1 flex size-9 items-center justify-center self-start justify-self-end rounded-full text-body transition-colors hover:bg-hot/10 hover:text-hot disabled:opacity-40 @3xl:col-start-auto @3xl:row-start-auto @3xl:self-center"
      >
        <Trash2 className="size-4" aria-hidden="true" />
      </button>
    </li>
  )
}

import { ShoppingBasket } from 'lucide-react'
import { Link } from 'react-router'
import type { OrderItem } from '../../api/types'
import { ProductImage } from '../../components/product/ProductImage'
import { formatPrice, formatUnit } from '../../utils/format'
import { ProductReviewForm } from './ProductReviewForm'

interface OrderItemsListProps {
  items: OrderItem[]
  /** set for a delivered order: each product can then be reviewed */
  reviewOrderId?: number
}

/** Picture, product, price, quantity, subtotal: in columns once the card is wide enough (its own width, like the cart). */
const COLUMNS = '@2xl:grid-cols-[4rem_minmax(0,1fr)_7rem_4rem_6.5rem]'

/** The price paid for one, and the price before the discount crossed out. */
function UnitPrice({ item }: { item: OrderItem }) {
  return (
    <>
      {formatPrice(item.unitPrice)}
      {item.price > item.unitPrice && (
        <span className="ml-1 text-muted line-through">
          <span className="sr-only">instead of </span>
          {formatPrice(item.price)}
        </span>
      )}
    </>
  )
}

/**
 * The products of an order, with the prices paid at the time (later price changes do not show here). Wide: columns
 * with titles; narrow (a phone): picture, product with "2 × ₹60", subtotal.
 */
export function OrderItemsList({ items, reviewOrderId }: OrderItemsListProps) {
  const units = items.reduce((sum, item) => sum + item.quantity, 0)
  return (
    <section aria-labelledby="order-items-title" className="@container overflow-hidden rounded-3xl border border-line bg-card">
      <h2 id="order-items-title" className="flex items-center gap-3 px-5 pt-5 text-xl sm:px-6">
        {/* Orange: the colour of the products on the customer's order page */}
        <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-xl bg-new-light text-warn-text">
          <ShoppingBasket className="size-5" />
        </span>
        <span>
          Items <span className="text-base font-normal text-body">({units})</span>
        </span>
      </h2>

      {/* Column titles: only when the rows are columns (screen readers get labels in the rows) */}
      <div aria-hidden="true" className={`mt-4 hidden gap-x-4 bg-surface px-6 py-2.5 font-heading text-xs font-bold tracking-wide text-body uppercase @2xl:grid ${COLUMNS}`}>
        <span className="col-span-2">Product</span>
        <span className="text-right">Price</span>
        <span className="text-center">Qty</span>
        <span className="text-right">Subtotal</span>
      </div>

      <ul aria-label="Products in this order" className="divide-y divide-line px-5 sm:px-6">
        {items.map((item, index) => {
          const productUrl = `/products/${item.productId}`
          return (
            // The rows rise in one after the other (60 ms apart); "reduce motion" turns it off
            <li key={item.productId} className="py-4 motion-safe:animate-rise" style={{ animationDelay: `${150 + index * 60}ms` }}>
              <div className={`grid grid-cols-[4rem_minmax(0,1fr)_auto] items-center gap-x-4 ${COLUMNS}`}>
                {/* The picture grows a little under the mouse */}
                <Link to={productUrl} tabIndex={-1} aria-hidden="true" className="block overflow-hidden rounded-xl transition-transform duration-300 hover:scale-105">
                  <ProductImage product={{ name: item.productName, categoryId: 0, imageUrls: item.imageUrl ? [item.imageUrl] : [] }} emojiClassName="text-3xl" />
                </Link>
                <div className="min-w-0">
                  <Link to={productUrl} className="line-clamp-2 font-heading font-bold text-heading hover:text-brand-text">
                    {item.productName}
                  </Link>
                  <p className="text-sm">{formatUnit(item.unit, item.unitQuantity)}</p>
                  <p className="text-sm @2xl:hidden">
                    {item.quantity} × <UnitPrice item={item} />
                  </p>
                </div>
                <p className="hidden text-right text-sm text-heading @2xl:block">
                  <span className="sr-only">Price: </span>
                  <UnitPrice item={item} />
                </p>
                <p className="hidden text-center font-heading font-bold @2xl:block">
                  <span className="sr-only">Quantity: </span>
                  {/* The quantity in a small blue pill */}
                  <span className="inline-grid min-w-8 place-items-center rounded-full bg-sale-light px-2 py-0.5 text-sale-text">{item.quantity}</span>
                </p>
                <p className="text-right font-heading text-lg font-bold text-heading">
                  <span className="sr-only">Subtotal: </span>
                  {formatPrice(item.lineTotal)}
                </p>
              </div>
              {reviewOrderId !== undefined && (
                <div className="mt-3 pl-20">
                  <ProductReviewForm orderId={reviewOrderId} item={item} />
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </section>
  )
}

import { ShoppingBasket } from 'lucide-react'
import type { CartItem } from '../../api/types'
import { ProductImage } from '../../components/product/ProductImage'
import { ToneBar, ToneIcon } from '../../components/ui/ToneIcon'
import { toneCard } from '../../components/ui/tones'
import { formatPrice, formatUnit } from '../../utils/format'

interface OrderReviewProps {
  items: CartItem[]
  storeName: string | undefined
}

/**
 * "Your order": the products about to be ordered, as the backend checked them just now. Orange, the colour of the
 * products; the rows rise in one after the other ("reduce motion" turns that off).
 */
export function OrderReview({ items, storeName }: OrderReviewProps) {
  return (
    <section aria-labelledby="order-review-title" className={toneCard('orange', 'p-5')}>
      <ToneBar tone="orange" />
      <h2 id="order-review-title" className="flex items-center gap-3 text-lg">
        <ToneIcon icon={ShoppingBasket} tone="orange" solid />
        Your order
      </h2>
      {storeName && (
        <p className="mt-1 text-sm">
          From <strong className="text-heading">{storeName}</strong>
        </p>
      )}
      <ul aria-label="Products in your order" className="mt-3 divide-y divide-line">
        {items.map((item, index) => (
          <li key={item.productId} className="flex items-center gap-3 py-3 motion-safe:animate-rise" style={{ animationDelay: `${150 + index * 60}ms` }}>
            <div className="w-14 shrink-0 overflow-hidden rounded-xl">
              <ProductImage product={{ name: item.productName, categoryId: 0, imageUrls: item.imageUrl ? [item.imageUrl] : [] }} emojiClassName="text-2xl" />
            </div>
            <div className="min-w-0 flex-1 text-sm">
              <p className="font-heading font-bold text-heading">{item.productName}</p>
              <p>
                {item.quantity} × {formatPrice(item.sellingPrice)} · {formatUnit(item.unit, item.unitQuantity)}
              </p>
            </div>
            <span className="font-heading font-bold text-heading">{formatPrice(item.lineTotal)}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

import { CircleAlert, ReceiptText, Truck } from 'lucide-react'
import type { ReactNode } from 'react'
import type { Cart } from '../../api/types'
import { usePlatformSettings } from '../../hooks/usePlatformSettings'
import { formatPrice } from '../../utils/format'

interface CartTotalsProps {
  /** the cart, or the checkout summary (it has the same totals) */
  cart: Pick<Cart, 'totals' | 'couponCode'>
  title?: string
  /** shown at the bottom of the card, e.g. the checkout button, so the total and the button stay together */
  children?: ReactNode
}

/**
 * "Cart totals", exactly as the backend calculated them. The browser only adds two hints:
 * how much more is needed for free delivery and for the minimum order.
 *
 * The look (the same colours as the cart page): savings and the coupon in pink, the way to free delivery as a blue
 * bar that fills up (it grows in when the page opens, and moves on when the cart changes), the total to pay in a green
 * band, and the minimum order in an orange box.
 */
export function CartTotals({ cart, title = 'Cart totals', children }: CartTotalsProps) {
  const { data: settings } = usePlatformSettings()
  const { totals } = cart
  const missingForFreeDelivery = settings && totals.deliveryFee > 0 ? settings.freeDeliveryFrom - totals.itemsTotal : 0
  // Only for the bar: how far the items total is on the way to free delivery, 0 to 100 %
  const freeDeliveryProgress = settings && missingForFreeDelivery > 0 ? Math.min(100, Math.round((totals.itemsTotal / settings.freeDeliveryFrom) * 100)) : 0

  const row = 'flex items-baseline justify-between gap-4 py-2'

  return (
    <div className="relative overflow-hidden rounded-3xl border border-line bg-card bg-linear-to-br from-brand-light via-card to-card p-5">
      {/* A green bar along the top: green is the colour of the money */}
      <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-linear-to-r from-brand to-sale" />
      <h2 className="flex items-center gap-3 text-lg">
        <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand text-white shadow-md shadow-brand/30">
          <ReceiptText className="size-5" />
        </span>
        {title}
      </h2>
      <dl className="mt-3 text-sm">
        <div className={row}>
          <dt>Items total</dt>
          <dd className="font-bold text-heading">{formatPrice(totals.itemsTotal)}</dd>
        </div>
        {totals.productSavings > 0 && (
          <div className={row}>
            <dt>You save on discounted products</dt>
            <dd className="rounded-full bg-hot/10 px-2.5 py-0.5 font-bold text-hot-text">{formatPrice(totals.productSavings)}</dd>
          </div>
        )}
        {totals.couponDiscount > 0 && (
          <div className={row}>
            <dt>Coupon {cart.couponCode}</dt>
            <dd className="rounded-full bg-hot/10 px-2.5 py-0.5 font-bold text-hot-text">−{formatPrice(totals.couponDiscount)}</dd>
          </div>
        )}
        <div className={row}>
          <dt>Delivery</dt>
          <dd className="min-w-0 flex-1 text-right">
            <span className="font-bold text-heading">{totals.deliveryFee > 0 ? formatPrice(totals.deliveryFee) : 'Free'}</span>
            {missingForFreeDelivery > 0 && (
              <>
                <span className="block text-xs text-sale-text">Add {formatPrice(missingForFreeDelivery)} more for free delivery</span>
                {/* The way to free delivery: a blue-to-green bar with a little truck (the sentence above says it in words) */}
                <span aria-hidden="true" className="mt-1.5 ml-auto flex w-full max-w-56 items-center gap-2">
                  <span className="h-2 flex-1 overflow-hidden rounded-full bg-sale-light">
                    <span
                      className="block h-full origin-left rounded-full bg-linear-to-r from-sale to-brand transition-[width] duration-500 motion-safe:animate-grow-x"
                      style={{ width: `${freeDeliveryProgress}%` }}
                    />
                  </span>
                  <Truck className="size-4 text-sale-text" />
                </span>
              </>
            )}
          </dd>
        </div>
        {/* The total to pay in a green band, so it is the first thing the eye finds */}
        <div className="mt-2 flex items-baseline justify-between gap-4 rounded-2xl bg-brand-light px-4 py-3">
          <dt className="font-heading text-base font-bold text-heading">Total to pay</dt>
          <dd className="font-heading text-2xl font-bold text-brand-text">{formatPrice(totals.payableAmount)}</dd>
        </div>
      </dl>
      <p className="mt-2 text-xs">Prices include GST of {formatPrice(totals.taxIncluded)}.</p>
      {!totals.minimumOrderMet && (
        <p role="status" className="mt-3 flex gap-2 rounded-2xl bg-new-light px-3 py-2.5 text-sm text-heading">
          <CircleAlert className="mt-0.5 size-4 shrink-0 text-warn-text" aria-hidden="true" />
          <span>
            The minimum order is {formatPrice(totals.minimumOrderValue)}. Add {formatPrice(totals.minimumOrderValue - totals.itemsTotal)} more to check out.
          </span>
        </p>
      )}
      {children && <div className="mt-4 space-y-2">{children}</div>}
    </div>
  )
}

import { ReceiptText } from 'lucide-react'
import type { Order } from '../../api/types'
import { formatPrice } from '../../utils/format'
import { paymentSummary } from './orderStatus'

interface OrderAmountsProps {
  order: Order
  /**
   * the line about the payment; the customer's wording by default ("Cash on delivery: pay ₹324 at the door"); null: none
   * (the customer's order page shows the payment in a card of its own)
   */
  paymentLine?: string | null
}

/** What the order costs, as the backend fixed it when the order was placed, and how it is paid. */
export function OrderAmounts({ order, paymentLine = paymentSummary(order) }: OrderAmountsProps) {
  const { amounts } = order
  const units = order.items.reduce((sum, item) => sum + item.quantity, 0)
  const row = 'flex items-baseline justify-between gap-4 py-2'

  return (
    <section aria-labelledby="order-amounts-title" className="rounded-3xl border border-line bg-card p-6">
      <h2 id="order-amounts-title" className="mb-3 flex items-center gap-3 text-xl">
        <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand-light text-brand-text">
          <ReceiptText className="size-5" />
        </span>
        Order total
      </h2>
      <dl className="border-b border-line pb-2">
        <div className={row}>
          <dt>
            Items total <span className="text-xs">({units} {units === 1 ? 'item' : 'items'})</span>
          </dt>
          <dd className="font-bold text-heading">{formatPrice(amounts.itemsTotal)}</dd>
        </div>
        {amounts.couponDiscount > 0 && (
          <div className={row}>
            <dt>Coupon {amounts.couponCode}</dt>
            <dd className="font-bold text-brand-text">−{formatPrice(amounts.couponDiscount)}</dd>
          </div>
        )}
        <div className={row}>
          <dt>Delivery</dt>
          <dd className="font-bold text-heading">{amounts.deliveryFee > 0 ? formatPrice(amounts.deliveryFee) : 'Free'}</dd>
        </div>
        <div className={row}>
          <dt>GST (included in the prices)</dt>
          <dd className="text-heading">{formatPrice(amounts.taxIncluded)}</dd>
        </div>
      </dl>
      {/* The total in a light green band, so it is the first thing the eye finds */}
      <div className="mt-3 flex items-baseline justify-between gap-4 rounded-2xl bg-brand-light px-4 py-3">
        <p className="font-heading text-lg font-bold text-heading">Total</p>
        <p className="font-heading text-2xl font-bold text-brand-text">{formatPrice(amounts.totalAmount)}</p>
      </div>
      {paymentLine && <p className="mt-3 rounded-lg bg-surface px-3 py-2 text-sm text-heading">{paymentLine}</p>}
    </section>
  )
}

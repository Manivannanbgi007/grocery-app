import type { Coupon, Product } from '../api/types'
import { formatPrice } from './format'

/** True when the product is sold below its original price (MRP). */
export function hasDiscount(product: Pick<Product, 'price' | 'sellingPrice'>): boolean {
  return product.sellingPrice < product.price
}

/** How much the customer saves on one unit: 40 - 36 -> 4. */
export function savingsOf(product: Pick<Product, 'price' | 'sellingPrice'>): number {
  return Math.round((product.price - product.sellingPrice) * 100) / 100
}

/** The discounted products, biggest discount (in %) first. The API cannot sort by discount, so the browser does it. */
export function biggestDiscounts(products: Product[]): Product[] {
  return products.filter(hasDiscount).sort((a, b) => b.discountPercentage - a.discountPercentage || savingsOf(b) - savingsOf(a))
}

/** What a coupon gives: "₹50 off" or "10% off, up to ₹100". */
export function couponBenefit(coupon: Coupon): string {
  if (coupon.discountType === 'FLAT') return `${formatPrice(coupon.discountValue)} off`
  const percent = `${coupon.discountValue}% off`
  return coupon.maxDiscountAmount ? `${percent}, up to ${formatPrice(coupon.maxDiscountAmount)}` : percent
}

/** The coupon's condition: "On orders of ₹299 or more", or null when there is none. */
export function couponCondition(coupon: Coupon): string | null {
  return coupon.minOrderAmount > 0 ? `On orders of ${formatPrice(coupon.minOrderAmount)} or more` : null
}

import type { DiscountType, CouponDetails } from '../../api/types'
import type { CouponRequest } from '../../api/vendorCoupons'
import { daysBefore, endOfIndiaDay, indiaDay, startOfIndiaDay } from '../../utils/dates'
import { removeEmpty, type FieldErrors } from '../auth/validation'

/* The coupon form: what the vendor types, and the rules of cart-service (CouponRequest and Coupon.checkSettings). */

export interface CouponValues {
  code: string
  description: string
  discountType: DiscountType
  discountValue: string
  /** PERCENTAGE only; empty = no upper limit */
  maxDiscountAmount: string
  minOrderAmount: string
  /** first and last day, India time ("2026-10-26"): the coupon runs from the start of the first to the end of the last */
  validFrom: string
  validUntil: string
  /** empty = no limit */
  usageLimit: string
  perCustomerLimit: string
}

export const COUPON_FIELDS: readonly (keyof CouponValues)[] = [
  'code',
  'description',
  'discountType',
  'discountValue',
  'maxDiscountAmount',
  'minOrderAmount',
  'validFrom',
  'validUntil',
  'usageLimit',
  'perCustomerLimit',
]

/** A new coupon: 10 % off, from today for 30 days, once per customer. */
export function newCouponValues(): CouponValues {
  const today = indiaDay()
  return {
    code: '',
    description: '',
    discountType: 'PERCENTAGE',
    discountValue: '10',
    maxDiscountAmount: '',
    minOrderAmount: '0',
    validFrom: today,
    validUntil: daysBefore(today, -30),
    usageLimit: '',
    perCustomerLimit: '1',
  }
}

export function couponValuesOf(coupon: CouponDetails): CouponValues {
  return {
    code: coupon.code,
    description: coupon.description,
    discountType: coupon.discountType,
    discountValue: String(coupon.discountValue),
    maxDiscountAmount: coupon.maxDiscountAmount === null ? '' : String(coupon.maxDiscountAmount),
    minOrderAmount: String(coupon.minOrderAmount),
    validFrom: indiaDay(coupon.validFrom),
    validUntil: indiaDay(coupon.validUntil),
    usageLimit: coupon.usageLimit === null ? '' : String(coupon.usageLimit),
    perCustomerLimit: String(coupon.perCustomerLimit),
  }
}

const MONEY = /^\d+(\.\d{1,2})?$/
const WHOLE = /^\d+$/

export function validateCoupon(values: CouponValues): FieldErrors<keyof CouponValues> {
  const value = MONEY.test(values.discountValue.trim()) ? Number(values.discountValue) : null
  const percentage = values.discountType === 'PERCENTAGE'
  return removeEmpty({
    code: /^[A-Za-z0-9]{4,20}$/.test(values.code.trim()) ? undefined : 'Use 4 to 20 letters or digits, e.g. FRESH20.',
    description: !values.description.trim() ? 'Tell customers what they get.' : values.description.trim().length > 255 ? 'Please use at most 255 characters.' : undefined,
    discountValue:
      value === null || value <= 0 ? (percentage ? 'Enter the discount in %, e.g. 10.' : 'Enter the amount in rupees, e.g. 50.') : percentage && value > 100 ? 'A percentage discount cannot be more than 100.' : undefined,
    maxDiscountAmount: percentage && values.maxDiscountAmount.trim() && !(MONEY.test(values.maxDiscountAmount.trim()) && Number(values.maxDiscountAmount) > 0) ? 'Enter an amount in rupees, or leave it empty.' : undefined,
    minOrderAmount: MONEY.test(values.minOrderAmount.trim()) ? undefined : 'Enter an amount in rupees (0 = no minimum).',
    validFrom: values.validFrom ? undefined : 'Choose the first day.',
    validUntil: !values.validUntil ? 'Choose the last day.' : values.validFrom && values.validUntil < values.validFrom ? 'The last day cannot be before the first day.' : undefined,
    usageLimit: values.usageLimit.trim() && !(WHOLE.test(values.usageLimit.trim()) && Number(values.usageLimit) >= 1) ? 'Enter a whole number, or leave it empty for no limit.' : undefined,
    perCustomerLimit: WHOLE.test(values.perCustomerLimit.trim()) && Number(values.perCustomerLimit) >= 1 && Number(values.perCustomerLimit) <= 100 ? undefined : 'Enter a number from 1 to 100.',
  })
}

export function toCouponRequest(values: CouponValues): CouponRequest {
  const percentage = values.discountType === 'PERCENTAGE'
  return {
    code: values.code.trim().toUpperCase(),
    description: values.description.trim(),
    discountType: values.discountType,
    discountValue: Number(values.discountValue),
    maxDiscountAmount: percentage && values.maxDiscountAmount.trim() ? Number(values.maxDiscountAmount) : null,
    minOrderAmount: Number(values.minOrderAmount),
    validFrom: startOfIndiaDay(values.validFrom),
    validUntil: endOfIndiaDay(values.validUntil),
    usageLimit: values.usageLimit.trim() ? Number(values.usageLimit) : null,
    perCustomerLimit: Number(values.perCustomerLimit),
  }
}

/** Where a coupon stands today: what the vendor needs to know at a glance. */
export function couponState(coupon: CouponDetails, now = Date.now()): { label: string; tone: 'brand' | 'new' | 'neutral' | 'hot' } {
  if (!coupon.active) return { label: 'Switched off', tone: 'neutral' }
  if (now < Date.parse(coupon.validFrom)) return { label: 'Not started', tone: 'new' }
  if (now > Date.parse(coupon.validUntil)) return { label: 'Ended', tone: 'neutral' }
  if (coupon.usageLimit !== null && coupon.usedCount >= coupon.usageLimit) return { label: 'Used up', tone: 'hot' }
  return { label: 'Running', tone: 'brand' }
}

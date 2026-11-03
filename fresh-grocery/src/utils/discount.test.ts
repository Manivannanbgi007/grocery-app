import { describe, expect, it } from 'vitest'
import type { Coupon, Product } from '../api/types'
import { demoProductRows, toProduct } from '../mocks/data/products'
import { biggestDiscounts, couponBenefit, couponCondition, hasDiscount, savingsOf } from './discount'
import { timeLeft } from './time'

describe('discounts', () => {
  const product = (id: number): Product => toProduct(demoProductRows.find((row) => row.id === id)!)

  it('knows when a product is discounted and by how much', () => {
    expect(hasDiscount(product(1))).toBe(true) // Tomato 40 -> 36
    expect(savingsOf(product(1))).toBe(4)
    expect(savingsOf(product(10))).toBe(34.5) // Surf Excel 230 -> 195.50
    expect(hasDiscount(product(2))).toBe(false) // Onion, no discount
  })

  it('puts the biggest discount first, and the bigger saving first when the % is equal', () => {
    const sorted = biggestDiscounts([product(2), product(1), product(10), product(125), product(108)])
    // Honey 20% · Surf Excel 15% (saves 34.50) · Strawberry 15% (saves 14.85) · Tomato 10% · (Onion left out)
    expect(sorted.map((p) => p.id)).toEqual([125, 10, 108, 1])
  })
})

describe('coupon texts', () => {
  const coupon = (changes: Partial<Coupon>): Coupon => ({
    code: 'X',
    description: null,
    storeId: null,
    discountType: 'FLAT',
    discountValue: 50,
    maxDiscountAmount: null,
    minOrderAmount: 299,
    validUntil: '2027-12-31T23:59:59Z',
    ...changes,
  })

  it('describes the benefit', () => {
    expect(couponBenefit(coupon({}))).toBe('₹50 off')
    expect(couponBenefit(coupon({ discountType: 'PERCENTAGE', discountValue: 10, maxDiscountAmount: 100 }))).toBe('10% off, up to ₹100')
    expect(couponBenefit(coupon({ discountType: 'PERCENTAGE', discountValue: 15 }))).toBe('15% off')
  })

  it('describes the minimum order, if there is one', () => {
    expect(couponCondition(coupon({}))).toBe('On orders of ₹299 or more')
    expect(couponCondition(coupon({ minOrderAmount: 0 }))).toBeNull()
  })
})

describe('timeLeft', () => {
  const now = Date.parse('2026-10-26T10:00:00Z')

  it('splits the remaining time into days, hours, minutes and seconds', () => {
    expect(timeLeft('2026-10-28T13:04:05Z', now)).toEqual({ days: 2, hours: 3, minutes: 4, seconds: 5, ended: false })
  })

  it('reports a moment in the past as ended', () => {
    expect(timeLeft('2026-10-26T09:59:59Z', now)).toMatchObject({ ended: true, days: 0 })
    expect(timeLeft('not a date', now).ended).toBe(true)
  })
})

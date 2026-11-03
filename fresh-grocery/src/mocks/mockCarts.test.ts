import { describe, expect, it } from 'vitest'
import { MockApiError } from './mockApiError'
import { addItem, applyCoupon, checkout, getCart, removeItem, updateQuantity } from './mockCarts'

/* The mock cart must calculate like cart-service, or the screens would show other numbers than with the real backend. */

const PRIYA = 4 // customer1, who has the seed cart: 2 × Tomato (36), 3 × Milk (27), 1 × Eggs (48)
const RAHUL = 7 // customer2, no cart

/** The message of the MockApiError thrown by `action`. */
function errorOf(action: () => unknown): { status: number; message: string } {
  try {
    action()
  } catch (error) {
    if (error instanceof MockApiError) return { status: error.status, message: error.message }
    throw error
  }
  throw new Error('no error was thrown')
}

describe('mock cart totals', () => {
  it('calculates the seed cart like the backend', () => {
    expect(getCart(PRIYA).totals).toEqual({
      itemsTotal: 201, // 72 + 81 + 48
      productSavings: 8, // the tomatoes: 2 × (40 - 36)
      couponDiscount: 0,
      deliveryFee: 30, // below the free delivery amount of 299
      payableAmount: 231,
      taxIncluded: 0, // these products have 0% GST
      minimumOrderValue: 99,
      minimumOrderMet: true,
    })
  })

  it('works out the GST that is included in the prices', () => {
    // Amul Butter: ₹56 with 12% GST -> 56 × 12 / 112 = 6.00
    expect(addItem(RAHUL, 7, 1, false).totals.taxIncluded).toBe(6)
  })

  it('gives free delivery from ₹299', () => {
    const cart = updateQuantity(PRIYA, 8, 3) // eggs 1 -> 3: 72 + 81 + 144 = 297
    expect(cart.totals.deliveryFee).toBe(30)
    expect(updateQuantity(PRIYA, 6, 4).totals).toMatchObject({ itemsTotal: 324, deliveryFee: 0 })
  })
})

describe('mock cart rules', () => {
  it('keeps one store per cart (409), unless the cart may be replaced', () => {
    // Organic Spinach is sold by Green Basket (store 2); the seed cart is from Fresh Mart (store 1)
    expect(errorOf(() => addItem(PRIYA, 11, 1, false)).status).toBe(409)
    const replaced = addItem(PRIYA, 11, 1, true)
    expect(replaced.items.map((item) => item.productName)).toEqual(['Organic Spinach'])
    expect(replaced.storeId).toBe(2)
  })

  it('checks the stock and the 20-per-product limit', () => {
    expect(errorOf(() => addItem(RAHUL, 7, 4, false)).message).toBe('Only 3 unit(s) of Amul Butter are available')
    expect(errorOf(() => addItem(RAHUL, 13, 1, false)).message).toBe('Pomegranate is out of stock')
    addItem(RAHUL, 2, 15, false) // Onion, 200 in stock
    expect(errorOf(() => addItem(RAHUL, 2, 6, false)).message).toBe('Quantity must be between 1 and 20')
  })

  it('refuses products that are not for sale', () => {
    expect(errorOf(() => addItem(RAHUL, 17, 1, false))).toEqual({ status: 400, message: 'Mango Alphonso is not available at the moment' })
  })

  it('needs a cart for changes', () => {
    expect(errorOf(() => updateQuantity(RAHUL, 1, 2))).toEqual({ status: 404, message: 'Your cart is empty. Add a product first.' })
    expect(errorOf(() => removeItem(PRIYA, 99))).toEqual({ status: 404, message: 'Product 99 is not in your cart' })
  })
})

describe('mock coupons', () => {
  it('applies a percentage coupon', () => {
    const cart = applyCoupon(PRIYA, 'save10') // any case is fine
    expect(cart.couponCode).toBe('SAVE10')
    expect(cart.totals).toMatchObject({ couponDiscount: 20.1, payableAmount: 210.9 })
    expect(cart.couponMessage).toBeNull()
  })

  it('explains why a coupon cannot be used', () => {
    expect(errorOf(() => applyCoupon(PRIYA, 'WELCOME50')).message).toBe('Coupon WELCOME50 needs an items total of at least 299.00 (add 98.00 more)')
    expect(errorOf(() => applyCoupon(PRIYA, 'GREEN30')).message).toBe('Coupon GREEN30 is only valid in another store')
    expect(errorOf(() => applyCoupon(PRIYA, 'SUMMER25')).message).toBe('Coupon SUMMER25 has expired')
    expect(errorOf(() => applyCoupon(PRIYA, 'MANGO15')).message).toBe('Coupon MANGO15 is not active')
    expect(errorOf(() => applyCoupon(PRIYA, 'NOPE1'))).toEqual({ status: 404, message: 'Coupon NOPE1 does not exist' })
  })

  it('keeps a coupon that no longer fits, with the reason, and gives no discount', () => {
    applyCoupon(PRIYA, 'SAVE10')
    const cart = removeItem(PRIYA, 8) // 201 - 48 = 153, below SAVE10's minimum of 199
    expect(cart.couponCode).toBe('SAVE10')
    expect(cart.couponMessage).toBe('Coupon SAVE10 needs an items total of at least 199.00 (add 46.00 more)')
    expect(cart.totals.couponDiscount).toBe(0)
  })
})

describe('mock checkout', () => {
  it('is ready for the seed cart', () => {
    expect(checkout(PRIYA)).toMatchObject({ readyForCheckout: true, problems: [] })
  })

  it('lists every problem at once', () => {
    addItem(RAHUL, 9, 1, false) // Lay's, ₹20: below the minimum order of ₹99
    addItem(RAHUL, 7, 3, false) // all 3 Amul Butters ...
    updateQuantity(RAHUL, 7, 3)
    expect(checkout(RAHUL).problems).toEqual([]) // 20 + 168 = 188: fine so far

    removeItem(RAHUL, 7)
    expect(checkout(RAHUL).problems).toEqual(['The minimum order value is 99.00 (add 79.00 more)'])
    expect(checkout(1000)).toMatchObject({ readyForCheckout: false, problems: ['Your cart is empty'] })
  })
})

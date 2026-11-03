import type { Cart, CartItem, CartTotals, CheckoutSummary, Product } from '../api/types'
import type { DemoCoupon } from './data/platform'
import { MockApiError } from './mockApiError'
import { catalogProduct } from './mockCatalog'
import { couponByCode } from './mockCoupons'
import { currentSettings } from './mockPlatform'

/*
 * The customers' carts for the mock API, kept in memory (they reset when the page reloads).
 * The calculations copy cart-service (CartPricingCalculator, Coupon, CheckoutServiceImpl) so the screens see the same
 * numbers and messages as with the real backend. The real rules stay in the backend; this only imitates them.
 */

export const MAX_QUANTITY = 20
const MAX_DIFFERENT_ITEMS = 50

interface MockCart {
  storeId: number | null
  couponCode: string | null
  items: CartItem[]
}

const roundMoney = (amount: number) => Math.round(amount * 100) / 100

/** The seed cart of customer1 (cart-service local seed): 2 tomatoes, 3 milk, 1 pack of eggs from Fresh Mart. */
function seedCarts(): Map<number, MockCart> {
  const item = (productId: number, quantity: number) => toCartItem(findProduct(productId)!, quantity)
  return new Map([[4, { storeId: 1, couponCode: null, items: [item(1, 2), item(6, 3), item(8, 1)] }]])
}

let carts = seedCarts()

/** For tests: back to the seed carts. */
export function resetMockCarts(): void {
  carts = seedCarts()
}

function findProduct(productId: number): Product | undefined {
  return catalogProduct(productId)
}

function toCartItem(product: Product, quantity: number): CartItem {
  return {
    productId: product.id,
    productName: product.name,
    unit: product.unit,
    unitQuantity: product.unitQuantity,
    imageUrl: product.imageUrls[0] ?? null,
    price: product.price,
    sellingPrice: product.sellingPrice,
    taxPercentage: product.taxPercentage,
    quantity,
    lineTotal: roundMoney(product.sellingPrice * quantity),
  }
}

// ---------- Prices (CartPricingCalculator + Coupon.discountFor) ----------

function itemsTotalOf(items: CartItem[]): number {
  return roundMoney(items.reduce((sum, item) => sum + item.lineTotal, 0))
}

function calculateTotals(items: CartItem[], couponDiscount: number): CartTotals {
  const itemsTotal = itemsTotalOf(items)
  const beforeDiscounts = items.reduce((sum, item) => sum + item.price * item.quantity, 0)
  const freeDelivery = items.length === 0 || itemsTotal >= currentSettings().freeDeliveryFrom
  const deliveryFee = freeDelivery ? 0 : currentSettings().deliveryFee
  const taxIncluded = items.reduce((sum, item) => sum + roundMoney((item.lineTotal * item.taxPercentage) / (100 + item.taxPercentage)), 0)
  return {
    itemsTotal,
    productSavings: roundMoney(beforeDiscounts - itemsTotal),
    couponDiscount: roundMoney(couponDiscount),
    deliveryFee,
    payableAmount: roundMoney(itemsTotal - couponDiscount + deliveryFee),
    taxIncluded: roundMoney(taxIncluded),
    minimumOrderValue: currentSettings().minimumOrderValue,
    minimumOrderMet: itemsTotal >= currentSettings().minimumOrderValue,
  }
}

function discountFor(coupon: DemoCoupon, itemsTotal: number): number {
  let discount = coupon.discountValue
  if (coupon.discountType === 'PERCENTAGE') {
    discount = roundMoney((itemsTotal * coupon.discountValue) / 100)
    if (coupon.maxDiscountAmount !== null && discount > coupon.maxDiscountAmount) discount = coupon.maxDiscountAmount
  }
  return Math.min(discount, itemsTotal)
}

/** Coupon.checkCanBeUsed: the first rule that fails, as a message; null when the coupon can be used. */
function couponProblem(coupon: DemoCoupon, cart: MockCart, itemsTotal: number): string | null {
  const now = Date.now()
  if (!coupon.active) return `Coupon ${coupon.code} is not active`
  if (now < Date.parse(coupon.validFrom)) return `Coupon ${coupon.code} is not valid yet`
  if (now > Date.parse(coupon.validUntil)) return `Coupon ${coupon.code} has expired`
  if (coupon.storeId !== null && coupon.storeId !== cart.storeId) return `Coupon ${coupon.code} is only valid in another store`
  if (itemsTotal < coupon.minOrderAmount) {
    const missing = (coupon.minOrderAmount - itemsTotal).toFixed(2)
    return `Coupon ${coupon.code} needs an items total of at least ${coupon.minOrderAmount.toFixed(2)} (add ${missing} more)`
  }
  return null
}

function findCoupon(code: string): DemoCoupon | undefined {
  return couponByCode(code)
}

/** The CartResponse, with the coupon checked again (the cart may have changed since it was applied). */
function toResponse(customerUserId: number, cart: MockCart | undefined): Cart {
  if (!cart || cart.items.length === 0) {
    return { customerUserId, storeId: null, items: [], itemCount: 0, couponCode: null, couponMessage: null, totals: calculateTotals([], 0) }
  }
  const itemsTotal = itemsTotalOf(cart.items)
  let discount = 0
  let couponMessage: string | null = null
  if (cart.couponCode) {
    const coupon = findCoupon(cart.couponCode)
    couponMessage = coupon ? couponProblem(coupon, cart, itemsTotal) : `Coupon ${cart.couponCode} does not exist`
    if (coupon && couponMessage === null) discount = discountFor(coupon, itemsTotal)
  }
  return {
    customerUserId,
    storeId: cart.storeId,
    items: cart.items,
    itemCount: cart.items.length,
    couponCode: cart.couponCode,
    couponMessage,
    totals: calculateTotals(cart.items, discount),
  }
}

// ---------- The cart operations (CartServiceImpl + Cart) ----------

function existingCart(customerUserId: number): MockCart {
  const cart = carts.get(customerUserId)
  if (!cart) throw new MockApiError(404, 'Your cart is empty. Add a product first.')
  return cart
}

function sellableProduct(productId: number): Product {
  const product = findProduct(productId)
  if (!product) throw new MockApiError(404, `Product not found with id: ${productId}`)
  if (product.status !== 'ACTIVE') throw new MockApiError(400, `${product.name} is not available at the moment`)
  return product
}

function checkEnoughStock(product: Product, wanted: number): void {
  if (product.availableQuantity < wanted) {
    throw new MockApiError(
      400,
      product.availableQuantity === 0 ? `${product.name} is out of stock` : `Only ${product.availableQuantity} unit(s) of ${product.name} are available`,
    )
  }
}

function checkQuantity(quantity: number): void {
  if (quantity < 1 || quantity > MAX_QUANTITY) throw new MockApiError(400, `Quantity must be between 1 and ${MAX_QUANTITY}`)
}

function clear(cart: MockCart): void {
  cart.items = []
  cart.storeId = null
  cart.couponCode = null
}

export function getCart(customerUserId: number): Cart {
  return toResponse(customerUserId, carts.get(customerUserId))
}

export function addItem(customerUserId: number, productId: number, quantity: number, replaceCart: boolean): Cart {
  const product = sellableProduct(productId)
  let cart = carts.get(customerUserId)
  if (!cart) {
    cart = { storeId: null, couponCode: null, items: [] }
    carts.set(customerUserId, cart)
  }
  const otherStore = cart.items.length > 0 && cart.storeId !== product.storeId
  if (otherStore && replaceCart) clear(cart)

  const existing = cart.items.find((item) => item.productId === productId)
  checkEnoughStock(product, (existing?.quantity ?? 0) + quantity)
  if (otherStore && !replaceCart) {
    throw new MockApiError(
      409,
      'Your cart has items from another store. An order can only contain items from one store: place that order or empty the cart first (or add with replaceCart=true).',
    )
  }
  if (existing) {
    checkQuantity(existing.quantity + quantity)
    Object.assign(existing, toCartItem(product, existing.quantity + quantity))
  } else {
    if (cart.items.length >= MAX_DIFFERENT_ITEMS) throw new MockApiError(400, `A cart can hold at most ${MAX_DIFFERENT_ITEMS} different products`)
    cart.items.push(toCartItem(product, quantity))
  }
  cart.storeId = product.storeId
  return toResponse(customerUserId, cart)
}

export function updateQuantity(customerUserId: number, productId: number, quantity: number): Cart {
  const cart = existingCart(customerUserId)
  const item = cart.items.find((line) => line.productId === productId)
  if (quantity > (item?.quantity ?? 0)) checkEnoughStock(sellableProduct(productId), quantity)
  if (!item) throw new MockApiError(404, `Product ${productId} is not in your cart`)
  checkQuantity(quantity)
  item.quantity = quantity
  item.lineTotal = roundMoney(item.sellingPrice * quantity)
  return toResponse(customerUserId, cart)
}

export function removeItem(customerUserId: number, productId: number): Cart {
  const cart = existingCart(customerUserId)
  if (!cart.items.some((line) => line.productId === productId)) throw new MockApiError(404, `Product ${productId} is not in your cart`)
  cart.items = cart.items.filter((line) => line.productId !== productId)
  if (cart.items.length === 0) clear(cart)
  return toResponse(customerUserId, cart)
}

export function clearCart(customerUserId: number): Cart {
  const cart = carts.get(customerUserId)
  if (cart) clear(cart)
  return toResponse(customerUserId, cart)
}

export function applyCoupon(customerUserId: number, code: string): Cart {
  const cart = existingCart(customerUserId)
  if (cart.items.length === 0) throw new MockApiError(400, 'Add items to your cart before applying a coupon')
  const coupon = findCoupon(code)
  if (!coupon) throw new MockApiError(404, `Coupon ${code} does not exist`)
  const problem = couponProblem(coupon, cart, itemsTotalOf(cart.items))
  if (problem) throw new MockApiError(400, problem)
  cart.couponCode = coupon.code
  return toResponse(customerUserId, cart)
}

export function removeCoupon(customerUserId: number): Cart {
  const cart = existingCart(customerUserId)
  cart.couponCode = null
  return toResponse(customerUserId, cart)
}

/** CheckoutServiceImpl: every problem is collected, so the customer sees all of them at once. */
/**
 * Like cart-service's checkoutFingerprint: a short text that changes with the store, any product, quantity or price, the
 * coupon and the totals (FNV-1a instead of SHA-256: enough for the mock).
 */
function fingerprintOf(storeId: number | null, items: CartItem[], couponCode: string | null, totals: CartTotals): string {
  const lines = [...items].sort((a, b) => a.productId - b.productId).map((item) => [item.productId, item.quantity, item.sellingPrice])
  const text = JSON.stringify([storeId, couponCode, lines, totals.itemsTotal, totals.couponDiscount, totals.deliveryFee, totals.payableAmount])
  let hash = 0x811c9dc5
  for (let i = 0; i < text.length; i++) {
    hash = Math.imul(hash ^ text.charCodeAt(i), 0x01000193) >>> 0
  }
  return `mock-${hash.toString(16).padStart(8, '0')}`
}

export function checkout(customerUserId: number): CheckoutSummary {
  const cart = carts.get(customerUserId)
  if (!cart || cart.items.length === 0) {
    const totals = calculateTotals([], 0)
    return {
      customerUserId,
      storeId: null,
      items: [],
      couponCode: null,
      totals,
      readyForCheckout: false,
      problems: ['Your cart is empty'],
      checkoutFingerprint: fingerprintOf(null, [], null, totals),
    }
  }
  const problems: string[] = []
  for (const item of cart.items) {
    const product = findProduct(item.productId)
    if (!product || product.status !== 'ACTIVE') {
      problems.push(`${item.productName} is no longer available. Please remove it from your cart.`)
    } else if (product.availableQuantity < item.quantity) {
      problems.push(product.availableQuantity === 0 ? `${product.name} is out of stock` : `Only ${product.availableQuantity} unit(s) of ${product.name} are available`)
    }
  }
  const response = toResponse(customerUserId, cart)
  if (response.couponCode && response.couponMessage) problems.push(`${response.couponMessage}. Remove the coupon to continue.`)
  const { totals } = response
  if (!totals.minimumOrderMet) {
    const missing = (totals.minimumOrderValue - totals.itemsTotal).toFixed(2)
    problems.push(`The minimum order value is ${totals.minimumOrderValue.toFixed(2)} (add ${missing} more)`)
  }
  const couponCode = response.couponMessage ? null : response.couponCode
  return {
    customerUserId,
    storeId: cart.storeId,
    items: cart.items,
    couponCode,
    totals,
    readyForCheckout: problems.length === 0,
    problems,
    checkoutFingerprint: fingerprintOf(cart.storeId, cart.items, couponCode, totals),
  }
}

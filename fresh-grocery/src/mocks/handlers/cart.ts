import { http } from 'msw'
import { validationFailed } from '../mockApiError'
import { addItem, applyCoupon, checkout, clearCart, getCart, MAX_QUANTITY, removeCoupon, removeItem, updateQuantity } from '../mockCarts'
import { asCustomer } from './withRole'

/*
 * Mock of cart-service. Like the real gateway, every cart call first needs a logged-in CUSTOMER (see asCustomer).
 * The cart rules themselves are in mockCarts.ts.
 */

/** The @Min(1) @Max(20) check of the request bodies. */
function validQuantity(quantity: unknown): number {
  if (typeof quantity !== 'number' || !Number.isInteger(quantity)) throw validationFailed([{ field: 'quantity', message: 'must not be null' }])
  if (quantity < 1) throw validationFailed([{ field: 'quantity', message: 'must be greater than or equal to 1' }])
  if (quantity > MAX_QUANTITY) throw validationFailed([{ field: 'quantity', message: `must be less than or equal to ${MAX_QUANTITY}` }])
  return quantity
}

export const cartHandlers = [
  http.get(
    '*/api/v1/cart',
    asCustomer(async (customer) => ({ data: getCart(customer.id), message: 'Cart fetched successfully' })),
  ),

  http.post(
    '*/api/v1/cart/items',
    asCustomer(async (customer, request) => {
      const body = (await request.json()) as { productId?: number; quantity?: number }
      const replaceCart = new URL(request.url).searchParams.get('replaceCart') === 'true'
      const quantity = validQuantity(body.quantity)
      return { data: addItem(customer.id, Number(body.productId), quantity, replaceCart), message: 'Product added to cart' }
    }),
  ),

  http.put(
    '*/api/v1/cart/items/:productId',
    asCustomer(async (customer, request, params) => {
      const body = (await request.json()) as { quantity?: number }
      return { data: updateQuantity(customer.id, Number(params.productId), validQuantity(body.quantity)), message: 'Quantity updated' }
    }),
  ),

  http.delete(
    '*/api/v1/cart/items/:productId',
    asCustomer(async (customer, _request, params) => ({ data: removeItem(customer.id, Number(params.productId)), message: 'Product removed from cart' })),
  ),

  http.delete(
    '*/api/v1/cart',
    asCustomer(async (customer) => ({ data: clearCart(customer.id), message: 'Cart emptied' })),
  ),

  http.put(
    '*/api/v1/cart/coupon',
    asCustomer(async (customer, request) => {
      const body = (await request.json()) as { code?: string }
      const code = body.code?.trim() ?? ''
      if (!/^[A-Za-z0-9]{4,20}$/.test(code)) throw validationFailed([{ field: 'code', message: 'must be 4-20 letters or digits' }])
      return { data: applyCoupon(customer.id, code), message: 'Coupon applied' }
    }),
  ),

  http.delete(
    '*/api/v1/cart/coupon',
    asCustomer(async (customer) => ({ data: removeCoupon(customer.id), message: 'Coupon removed' })),
  ),

  http.get(
    '*/api/v1/cart/checkout',
    asCustomer(async (customer) => ({ data: checkout(customer.id), message: 'Checkout summary prepared' })),
  ),
]

import { apiRequest } from './client'
import type { Cart, CheckoutSummary } from './types'

/*
 * The logged-in customer's cart (cart-service). Every change returns the whole updated cart,
 * so the screen simply shows what came back; the backend does all the calculations.
 */

export function getCart(signal?: AbortSignal): Promise<Cart> {
  return apiRequest<Cart>('/api/v1/cart', { signal })
}

/**
 * Adds `quantity` units (on top of what is already in the cart).
 * Fails with 409 when the cart holds products of another store; with replaceCart=true the cart is emptied first.
 */
export function addCartItem(productId: number, quantity: number, replaceCart = false): Promise<Cart> {
  return apiRequest<Cart>('/api/v1/cart/items', { method: 'POST', query: { replaceCart: replaceCart || undefined }, body: { productId, quantity } })
}

/** Sets the quantity of a product in the cart (1 to 20). */
export function updateCartItem(productId: number, quantity: number): Promise<Cart> {
  return apiRequest<Cart>(`/api/v1/cart/items/${productId}`, { method: 'PUT', body: { quantity } })
}

export function removeCartItem(productId: number): Promise<Cart> {
  return apiRequest<Cart>(`/api/v1/cart/items/${productId}`, { method: 'DELETE' })
}

export function clearCart(): Promise<Cart> {
  return apiRequest<Cart>('/api/v1/cart', { method: 'DELETE' })
}

/** Fails with the reason when the coupon cannot be used, e.g. "Coupon SAVE10 needs an items total of at least 199.00 ...". */
export function applyCoupon(code: string): Promise<Cart> {
  return apiRequest<Cart>('/api/v1/cart/coupon', { method: 'PUT', body: { code } })
}

export function removeCoupon(): Promise<Cart> {
  return apiRequest<Cart>('/api/v1/cart/coupon', { method: 'DELETE' })
}

/** The cart checked against today's catalog: readyForCheckout, or the problems to fix first. */
export function getCheckoutSummary(signal?: AbortSignal): Promise<CheckoutSummary> {
  return apiRequest<CheckoutSummary>('/api/v1/cart/checkout', { signal })
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { addCartItem, applyCoupon, clearCart, getCart, getCheckoutSummary, removeCartItem, removeCoupon, updateCartItem } from '../../api/cart'
import { isApiError } from '../../api/client'
import { queryKeys } from '../../api/queryKeys'
import type { Cart } from '../../api/types'
import { useAuth } from '../auth/authContext'

/*
 * The cart lives in the React Query cache under queryKeys.cart(userId).
 * Every change returns the whole new cart, which simply replaces the cached one (setQueryData),
 * so the header counter and the cart page always show the same cart without extra requests.
 */

/** The logged-in customer's cart (nothing is loaded for a guest or another kind of account). */
export function useCart() {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.cart(customer?.id ?? 0),
    queryFn: ({ signal }) => getCart(signal),
    enabled: customer !== null,
  })
}

/**
 * The cart checked against today's catalog: ready to order, or the problems to fix first (out of stock, minimum order ...).
 * Checked again every time the checkout page opens (staleTime 0), as prices and stock change.
 */
export function useCheckoutSummary() {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.checkout(customer?.id ?? 0),
    queryFn: ({ signal }) => getCheckoutSummary(signal),
    enabled: customer !== null,
    staleTime: 0,
  })
}

/** Stores the cart that a change returned. */
function useStoreCart() {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  return (cart: Cart) => queryClient.setQueryData(queryKeys.cart(customer?.id ?? 0), cart)
}

/** Shows what went wrong. A 401 is left out: the login has expired and the AuthProvider already says so. */
export function showCartError(error: Error) {
  if (!isApiError(error, 401)) toast.error(error.message)
}

export interface AddToCartInput {
  productId: number
  quantity: number
  /** true = empty a cart of another store first (after the customer agreed) */
  replaceCart?: boolean
}

/** Adding a product (the caller handles the errors: a 409 opens the "other store" dialog). */
export function useAddToCart() {
  const storeCart = useStoreCart()
  return useMutation({
    mutationFn: ({ productId, quantity, replaceCart }: AddToCartInput) => addCartItem(productId, quantity, replaceCart),
    onSuccess: storeCart,
  })
}

/** The changes made on the cart page. */
export function useCartChanges() {
  const storeCart = useStoreCart()
  return {
    updateQuantity: useMutation({
      mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) => updateCartItem(productId, quantity),
      onSuccess: storeCart,
      onError: showCartError,
    }),
    removeItem: useMutation({
      mutationFn: (productId: number) => removeCartItem(productId),
      onSuccess: storeCart,
      onError: showCartError,
    }),
    emptyCart: useMutation({ mutationFn: clearCart, onSuccess: storeCart, onError: showCartError }),
    // Coupon errors are shown next to the coupon box, so there is no toast here
    applyCoupon: useMutation({ mutationFn: (code: string) => applyCoupon(code), onSuccess: storeCart }),
    removeCoupon: useMutation({ mutationFn: removeCoupon, onSuccess: storeCart, onError: showCartError }),
  }
}

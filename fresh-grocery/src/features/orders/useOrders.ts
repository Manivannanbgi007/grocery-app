import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { isApiError } from '../../api/client'
import { getMyDeliveryRating, rateDelivery, type DeliveryRatingRequest } from '../../api/deliveryRatings'
import { cancelMyOrder, downloadInvoice, getMyOrder, getMyOrders, placeOrder, type PlaceOrderRequest } from '../../api/orders'
import { getMyPayment, getRazorpayCheckout, payWithMockGateway, verifyRazorpayPayment } from '../../api/payments'
import { queryKeys } from '../../api/queryKeys'
import { writeReview, type ReviewRequest } from '../../api/reviews'
import type { Cart, Order, OrderStatus, Payment } from '../../api/types'
import { saveFile } from '../../utils/download'
import { useAuth } from '../auth/authContext'
import { isFinished, pollInterval } from './orderStatus'
import { loadRazorpay, openRazorpayCheckout, type RazorpayOptions } from './razorpayCheckout'

/*
 * Orders move on in the background (stock, payment, store, delivery), so the order page asks again from time to time
 * (see pollInterval). The answers are cached per customer and order under queryKeys.order / queryKeys.payment.
 */

/** Orders per page in the order history. */
export const ORDERS_PER_PAGE = 10

/** Places the order. The caller handles the errors (cart problems, an address the store does not deliver to). */
export function usePlaceOrder() {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: PlaceOrderRequest) => placeOrder(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.allOrderLists(customer?.id ?? 0) }),
  })
}

/** One page of the order history (page is 1-based, like the page numbers people see). */
export function useMyOrders(status: OrderStatus | undefined, page: number, size = ORDERS_PER_PAGE) {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.orders(customer?.id ?? 0, { status, page }, size),
    queryFn: ({ signal }) => getMyOrders({ status, page: page - 1, size }, signal),
    enabled: customer !== null,
    // Keep the current page on screen while the next one loads
    placeholderData: keepPreviousData,
  })
}

/** How many of the newest orders the spending chart reads (order-service's largest page). */
export const SPENDING_ORDERS = 100

/** The customer's newest orders for "Your spending" on My orders (reloaded with the order history). */
export function useOrdersForSpending() {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.ordersForSpending(customer?.id ?? 0),
    queryFn: ({ signal }) => getMyOrders({ page: 0, size: SPENDING_ORDERS }, signal),
    enabled: customer !== null,
  })
}

/**
 * The customer's open orders (not delivered, failed, cancelled or rejected), newest first: "Track my order" and "On its
 * way" on My orders. Read from the newest 100 orders that "Your spending" loads anyway, so it costs no extra request.
 */
export function useOpenOrders() {
  const query = useOrdersForSpending()
  const openOrders = query.data?.content.filter((order) => !isFinished(order.status)) ?? []
  return { ...query, openOrders }
}

/** Cancels the order; the answer (the cancelled order) replaces the cached one, and the history is loaded again. */
export function useCancelOrder(orderId: number) {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (reason: string | null) => cancelMyOrder(orderId, reason),
    onSuccess: (order: Order) => {
      queryClient.setQueryData(queryKeys.order(customer?.id ?? 0, orderId), order)
      return queryClient.invalidateQueries({ queryKey: queryKeys.allOrderLists(customer?.id ?? 0) })
    },
    // Refused (e.g. the order left the store in the meantime): show what the order looks like now
    onError: () => queryClient.invalidateQueries({ queryKey: queryKeys.order(customer?.id ?? 0, orderId) }),
  })
}

/** Downloads the invoice PDF and hands it to the browser to save. */
export function useDownloadInvoice(orderId: number) {
  return useMutation({
    mutationFn: () => downloadInvoice(orderId),
    onSuccess: ({ blob, fileName }) => saveFile(blob, fileName ?? `invoice-order-${orderId}.pdf`),
  })
}

/** The customer's rating of the delivery; 404 while the order has no delivery yet. */
export function useDeliveryRating(orderId: number, enabled: boolean) {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.deliveryRating(customer?.id ?? 0, orderId),
    queryFn: ({ signal }) => getMyDeliveryRating(orderId, signal),
    enabled: enabled && customer !== null,
  })
}

export function useRateDelivery(orderId: number) {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: Omit<DeliveryRatingRequest, 'orderId'>) => rateDelivery({ ...request, orderId }),
    onSuccess: (rating) => queryClient.setQueryData(queryKeys.deliveryRating(customer?.id ?? 0, orderId), rating),
  })
}

/** Reviews a product of a delivered order; that product's reviews are loaded again the next time they are shown. */
export function useWriteReview(productId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: ReviewRequest) => writeReview(productId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.product(productId) }),
  })
}

/** One of the customer's orders, asked for again while it is moving. */
export function useOrder(orderId: number) {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: queryKeys.order(customer?.id ?? 0, orderId),
    queryFn: ({ signal }) => getMyOrder(orderId, signal),
    enabled: customer !== null,
    refetchInterval: (current) => pollInterval(current.state.data),
  })

  // The backend empties the cart in the background, a moment after the order is placed. So the header counter
  // catches up, the cart is loaded again when the order moves on, and after every new answer while the order is
  // still PENDING (an online order stays PENDING until it is paid) as long as the cached cart still has products.
  const status = query.data?.status
  const answeredAt = query.dataUpdatedAt
  const userId = customer?.id ?? 0
  useEffect(() => {
    if (!status) return
    void queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
    // Each step also sends a notification: update the bell now rather than at its next minute
    void queryClient.invalidateQueries({ queryKey: queryKeys.notifications(userId) })
  }, [status, userId, queryClient])
  useEffect(() => {
    const cachedCart = queryClient.getQueryData<Cart>(queryKeys.cart(userId))
    if (status === 'PENDING' && cachedCart && cachedCart.itemCount > 0) void queryClient.invalidateQueries({ queryKey: queryKeys.cart(userId) })
  }, [status, answeredAt, userId, queryClient])

  return query
}

const PAYMENT_POLL_MS = 3000
/** While a payment waits for the customer, the chat asks less often: they pay on another page, which can take minutes */
const AWAITING_PAYMENT_POLL_MS = 5000

/**
 * Ask again while the payment does not exist yet (404 for a moment after the order) or the stock is still being reserved.
 * With `untilSettled` also while it waits for the customer, until it is paid, failed or cancelled.
 */
function paymentPollInterval(payment: Payment | undefined, error: Error | null, untilSettled: boolean): number | false {
  if (isApiError(error, 404) || payment?.status === 'PENDING') return PAYMENT_POLL_MS
  if (untilSettled && payment?.status === 'AWAITING_PAYMENT') return AWAITING_PAYMENT_POLL_MS
  return false
}

/**
 * The payment of an order; only loaded while `enabled` (an online payment that may still be due). `untilSettled`: keep
 * asking payment-service until the payment is paid or failed (the chat, where the customer pays on another page and
 * Razorpay's webhook can confirm it later).
 */
export function usePayment(orderId: number, enabled: boolean, untilSettled = false) {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.payment(customer?.id ?? 0, orderId),
    queryFn: ({ signal }) => getMyPayment(orderId, signal),
    enabled: enabled && customer !== null,
    refetchInterval: (current) => paymentPollInterval(current.state.data, current.state.error, untilSettled),
  })
}

/** Pays with the mock gateway (success = false: the bank declines). Then the order is loaded again at once. */
export function usePayNow(orderId: number) {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (success: boolean) => payWithMockGateway(orderId, success),
    onSuccess: (payment) => {
      queryClient.setQueryData(queryKeys.payment(customer?.id ?? 0, orderId), payment)
      return queryClient.invalidateQueries({ queryKey: queryKeys.order(customer?.id ?? 0, orderId) })
    },
  })
}

/**
 * Pays with Razorpay: the details for Checkout, Razorpay's window, then payment-service checks what it returned.
 * Fails with RazorpayClosedError when the customer closes the window. The payment and the order are loaded again
 * afterwards in every case (a failed attempt leaves its reason on the payment).
 */
export function usePayWithRazorpay(orderId: number) {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (prefill: RazorpayOptions['prefill']) => {
      const details = await getRazorpayCheckout(orderId)
      const Razorpay = await loadRazorpay()
      const result = await openRazorpayCheckout(Razorpay, details, prefill)
      return verifyRazorpayPayment(orderId, result)
    },
    onSuccess: (payment) => queryClient.setQueryData(queryKeys.payment(customer?.id ?? 0, orderId), payment),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.payment(customer?.id ?? 0, orderId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.order(customer?.id ?? 0, orderId) })
    },
  })
}

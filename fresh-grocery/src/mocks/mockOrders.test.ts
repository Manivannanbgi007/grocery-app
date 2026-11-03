import { describe, expect, it } from 'vitest'
import { MockApiError } from './mockApiError'
import { addItem, checkout, getCart } from './mockCarts'
import { cancelMyOrder, getOrder, getPayment, listOrders, orderWithInvoice, payWithMockGateway, placeOrder, withPaymentUrl } from './mockOrders'
import { listNotifications } from './mockNotifications'
import { getDeliveryRating, rateDelivery, writeReview } from './mockRatings'

/* The mock order saga must behave like order-service + payment-service, or the screens would be built for other answers. */

const PRIYA = 4 // customer1: seed cart (₹201 + ₹30 delivery), home address 1 (560038), work address 2 (560034)
const RAHUL = 7 // customer2: no cart, home address 3

function errorOf(action: () => unknown): { status: number; message: string; details: string[] } {
  try {
    action()
  } catch (error) {
    if (error instanceof MockApiError) return { status: error.status, message: error.message, details: error.details.map((detail) => detail.message) }
    throw error
  }
  throw new Error('no error was thrown')
}

describe('mock orders', () => {
  it('turns the cart into a PENDING order and empties the cart', () => {
    const order = placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: ' Ring twice ' })
    expect(order).toMatchObject({ id: 1001, status: 'PENDING', paymentStatus: 'PENDING', storeName: 'Fresh Mart', customerNote: 'Ring twice', cancellable: true })
    expect(order.amounts).toMatchObject({ itemsTotal: 201, deliveryFee: 30, totalAmount: 231 })
    expect(order.items.map((item) => [item.productName, item.quantity, item.unitPrice])).toEqual([
      ['Tomato', 2, 36],
      ['Amul Taaza Toned Milk', 3, 27],
      ['Farm Eggs', 1, 48],
    ])
    expect(order.storeAddress).toBe('45, 100 Feet Road, Indiranagar, Opposite CMH Hospital, Bengaluru 560038')
    expect(getCart(PRIYA).items).toHaveLength(0)
  })

  it('confirms a cash-on-delivery order the next time it is read, and notifies the customer', () => {
    placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null })
    expect(getOrder(PRIYA, 1001)).toMatchObject({ status: 'CONFIRMED', paymentStatus: 'CASH_ON_DELIVERY' })
    expect(getPayment(PRIYA, 1001)).toMatchObject({ status: 'CASH_ON_DELIVERY', canPayNow: false })
    expect(getOrder(PRIYA, 1001).timeline.map((entry) => entry.status)).toEqual(['PENDING', 'CONFIRMED'])
    expect(listNotifications(PRIYA, true)[0]).toMatchObject({ type: 'ORDER_CONFIRMED', title: 'Order #1001 confirmed', message: 'Please pay ₹231 in cash on delivery. We will tell you when the store accepts it.', referenceId: 1001 })
  })

  it('opens an online payment, which confirms the order once paid', () => {
    placeOrder(PRIYA, { addressId: 1, paymentMethod: 'ONLINE', note: null })
    const payment = getPayment(PRIYA, 1001)
    expect(payment).toMatchObject({ status: 'AWAITING_PAYMENT', canPayNow: true, amount: 231 })
    expect(Date.parse(payment.payBefore!) - Date.now()).toBeGreaterThan(14 * 60 * 1000)
    expect(getOrder(PRIYA, 1001).status).toBe('PENDING')

    expect(payWithMockGateway(PRIYA, 1001, true)).toMatchObject({ status: 'PAID', canPayNow: false })
    expect(getOrder(PRIYA, 1001)).toMatchObject({ status: 'CONFIRMED', paymentStatus: 'PAID' })
    expect(errorOf(() => payWithMockGateway(PRIYA, 1001, true)).message).toBe('This payment cannot be paid (status PAID)')
  })

  it('names the page to pay on only while an online order is not paid yet, like order-service', () => {
    expect(withPaymentUrl(placeOrder(PRIYA, { addressId: 1, paymentMethod: 'ONLINE', note: null })).paymentUrl).toBe('/orders/1001')
    getPayment(PRIYA, 1001)
    payWithMockGateway(PRIYA, 1001, true)
    expect(withPaymentUrl(getOrder(PRIYA, 1001)).paymentUrl).toBeNull()

    addItem(PRIYA, 1, 3, false) // 3 tomatoes, ₹108: above the minimum order value
    expect(withPaymentUrl(placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null })).paymentUrl).toBeNull()
  })

  it('cancels the order when the bank declines', () => {
    placeOrder(PRIYA, { addressId: 1, paymentMethod: 'ONLINE', note: null })
    getPayment(PRIYA, 1001)
    payWithMockGateway(PRIYA, 1001, false)
    expect(getOrder(PRIYA, 1001)).toMatchObject({
      status: 'CANCELLED',
      paymentStatus: 'FAILED',
      cancelledBy: 'SYSTEM',
      statusReason: 'Payment failed: The bank declined the payment (mock)',
      cancellable: false,
    })
  })

  it('refunds a paid order that the customer cancels, and refuses to cancel it twice', () => {
    placeOrder(PRIYA, { addressId: 1, paymentMethod: 'ONLINE', note: null })
    getPayment(PRIYA, 1001)
    payWithMockGateway(PRIYA, 1001, true)
    expect(cancelMyOrder(PRIYA, 1001, '')).toMatchObject({ status: 'CANCELLED', paymentStatus: 'REFUNDED', statusReason: 'Cancelled by the customer', cancelledBy: 'CUSTOMER' })
    expect(getPayment(PRIYA, 1001).status).toBe('REFUNDED')
    expect(errorOf(() => cancelMyOrder(PRIYA, 1001, null))).toMatchObject({ status: 400, message: 'An order that is CANCELLED can no longer be cancelled' })
  })

  it('refuses an address the store does not deliver to, an empty cart and unknown orders', () => {
    expect(errorOf(() => placeOrder(PRIYA, { addressId: 2, paymentMethod: 'COD', note: null }))).toMatchObject({
      status: 400,
      message: 'Fresh Mart does not deliver to PIN code 560034. Please choose another address.',
    })
    expect(errorOf(() => placeOrder(RAHUL, { addressId: 3, paymentMethod: 'COD', note: null }))).toMatchObject({ status: 409, details: ['Your cart is empty'] })
    expect(errorOf(() => placeOrder(PRIYA, { addressId: 3, paymentMethod: 'COD', note: null }))).toMatchObject({ status: 404, message: 'Address not found with id: 3' })
    expect(errorOf(() => getOrder(RAHUL, 1001))).toMatchObject({ status: 404, message: 'Order not found with id: 1001' })
  })

  it('lists only the customer’s own orders, newest first (after the two seed orders of customer1)', () => {
    placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null })
    expect(listOrders(PRIYA, null).map((order) => [order.id, order.status])).toEqual([
      [1001, 'CONFIRMED'],
      [902, 'CANCELLED'],
      [901, 'DELIVERED'],
    ])
    expect(listOrders(PRIYA, null)[0]).toMatchObject({ itemCount: 6, totalAmount: 231 })
    expect(listOrders(PRIYA, 'CANCELLED').map((order) => order.id)).toEqual([902])
    expect(listOrders(RAHUL, null)).toEqual([])
  })

  it('places one order for one Idempotency-Key, also when the request comes again after the cart was emptied', () => {
    const first = placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null }, 'chat-card-0001')
    const again = placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null }, 'chat-card-0001')

    expect(again.id).toBe(first.id)
    expect(listOrders(PRIYA, null).filter((order) => order.id >= 1001)).toHaveLength(1)
    expect(errorOf(() => placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null }, 'a b')).status).toBe(400)
  })

  it('refuses an order from a summary of a cart that changed since', () => {
    const summary = checkout(PRIYA)
    addItem(PRIYA, 1, 1, false) // one more tomato after the summary

    const error = errorOf(() =>
      placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null, expectedCheckoutFingerprint: summary.checkoutFingerprint }, 'chat-card-0002'),
    )

    expect(error).toMatchObject({ status: 409, message: 'Your cart or its prices changed since the summary. Please check the new total.' })
    expect(getCart(PRIYA).items).toHaveLength(3)
  })

  it('refuses the same cart ordered again a moment later as a double order', () => {
    const lines = getCart(PRIYA).items.map((item) => [item.productId, item.quantity] as const)
    const first = placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null }, 'chat-card-0003')
    for (const [productId, quantity] of lines) addItem(PRIYA, productId, quantity, false) // the same cart again

    expect(errorOf(() => placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null }, 'chat-card-0004'))).toMatchObject({
      status: 409,
      message: `You placed this order a moment ago (order #${first.id}).`,
    })
  })

  it('has an invoice only once the store accepted the order', () => {
    expect(orderWithInvoice(PRIYA, 901).id).toBe(901)
    expect(errorOf(() => orderWithInvoice(PRIYA, 902)).message).toBe('Order 902 has no invoice (it is CANCELLED). Invoices exist once the store accepted the order.')
  })
})

describe('mock ratings', () => {
  it('rates the delivery of a delivered order once', () => {
    expect(getDeliveryRating(PRIYA, 901)).toMatchObject({ partnerName: 'Arjun Singh', rating: null })
    expect(rateDelivery(PRIYA, { orderId: 901, rating: 5, comment: ' Fast ' })).toMatchObject({ rating: 5, comment: 'Fast' })
    expect(errorOf(() => rateDelivery(PRIYA, { orderId: 901, rating: 4, comment: null })).message).toBe('You have already rated this delivery')
    // No partner was ever assigned to the cancelled order, so it has no delivery
    expect(errorOf(() => getDeliveryRating(PRIYA, 902))).toMatchObject({ status: 404, message: 'No delivery found for order 902' })
  })

  it('reviews a product of a delivered order once, under a short public name', () => {
    expect(writeReview(PRIYA, 1, { orderId: 901, rating: 4, comment: null })).toMatchObject({ reviewerName: 'Priya S.', orderId: 901, status: 'VISIBLE' })
    expect(errorOf(() => writeReview(PRIYA, 1, { orderId: 901, rating: 5, comment: null }))).toMatchObject({ status: 409 })
    expect(errorOf(() => writeReview(PRIYA, 2, { orderId: 901, rating: 5, comment: null })).message).toBe('Product 2 is not part of order 901')
    expect(errorOf(() => writeReview(PRIYA, 5, { orderId: 902, rating: 5, comment: null })).message).toBe('You can review the products of an order once it is delivered')
    expect(errorOf(() => writeReview(PRIYA, 1, { orderId: 901, rating: 6, comment: null })).details).toEqual(['must be less than or equal to 5'])
  })
})

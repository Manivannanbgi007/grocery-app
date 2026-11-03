import type { PlaceOrderRequest } from '../api/orders'
import type { ApiErrorDetail, ChangedBy, GeoPoint, Order, OrderItem, OrderStatus, OrderSummary, Payment, Store, TimelineEntry } from '../api/types'
import { formatPrice } from '../utils/format'
import { demoAddresses } from './data/addresses'
import { demoStores } from './data/stores'
import { MockApiError, validationFailed } from './mockApiError'
import { checkout, clearCart } from './mockCarts'
import { catalogProduct } from './mockCatalog'
import { findAddress } from './mockCustomers'
import { cancelDeliveryOfOrder, createDelivery } from './mockDelivery'
import { notify } from './mockNotifications'
import { deliversTo, storeById } from './mockStores'

/*
 * The customers' orders and their payments for the mock API, kept in memory (they reset when the page reloads).
 * Copies order-service and payment-service closely enough for the screens:
 * - placing an order answers with a PENDING order and empties the cart;
 * - the background steps of the real backend (stock reserved, then cash on delivery accepted or the online payment
 *   opened) happen the next time the order or its payment is read;
 * - the store's steps (accept or reject, pack, ready for pickup) come from the mock vendor panel; "ready for pickup" makes
 *   a delivery job (mockDelivery.ts) that an admin can give to a partner; the partner's steps never happen in the mock.
 *   customer1 has two older orders (901 delivered, 902 cancelled) and Fresh Mart a new one waiting (903 of customer3),
 *   so the order history and the vendor's queue have something to show.
 * - admins see and cancel every order, and see every payment.
 */

const ONLINE_PAYMENT_MINUTES = 15
const DEFAULT_CANCEL_REASON = 'Cancelled by the customer'
const MOCK_DECLINE_REASON = 'The bank declined the payment (mock)'

/** OrderStatus.isCancellable: "cancel before dispatch", until the order leaves the store. */
const CANCELLABLE: OrderStatus[] = ['PENDING', 'CONFIRMED', 'ACCEPTED', 'PACKED', 'READY_FOR_PICKUP']

/** Order.hasInvoice: once the store accepted the order. */
const WITH_INVOICE: OrderStatus[] = ['ACCEPTED', 'PACKED', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED']

interface MockOrder {
  order: Order
  payment: Payment
}

const now = () => new Date().toISOString()
const roundMoney = (amount: number) => Math.round(amount * 100) / 100

// ---------- The seed orders ----------

function orderItem(productId: number, quantity: number): OrderItem {
  const product = catalogProduct(productId)!
  return {
    productId,
    productName: product.name,
    unit: product.unit,
    unitQuantity: product.unitQuantity,
    imageUrl: product.imageUrls[0] ?? null,
    price: product.price,
    unitPrice: product.sellingPrice,
    taxPercentage: product.taxPercentage,
    quantity,
    lineTotal: roundMoney(product.sellingPrice * quantity),
  }
}

/** A timeline that started `daysAgo` days ago; each step [status, note, by, minutes after the order]. */
function pastTimeline(daysAgo: number, steps: [OrderStatus, string, ChangedBy, number][]): TimelineEntry[] {
  const start = Date.now() - daysAgo * 24 * 60 * 60 * 1000
  return steps.map(([status, note, changedBy, minutes]) => ({ status, note, changedBy, at: new Date(start + minutes * 60 * 1000).toISOString() }))
}

function pastOrder(id: number, status: OrderStatus, items: OrderItem[], timeline: TimelineEntry[], details: Partial<Order>): MockOrder {
  const store = demoStores[0]
  const home = demoAddresses[4][0]
  const itemsTotal = roundMoney(items.reduce((sum, item) => sum + item.lineTotal, 0))
  const order: Order = {
    id,
    customerUserId: 4,
    storeId: store.id,
    storeName: store.name,
    storePhone: store.phone,
    storeAddress: oneLineAddress(store),
    status,
    paymentMethod: 'COD',
    paymentStatus: 'PAID',
    items,
    deliveryAddress: {
      contactName: home.contactName,
      contactPhone: home.contactPhone,
      addressLine1: home.addressLine1,
      addressLine2: home.addressLine2,
      landmark: home.landmark,
      city: home.city,
      state: home.state,
      pincode: home.pincode,
    },
    amounts: { itemsTotal, couponCode: null, couponDiscount: 0, deliveryFee: 30, taxIncluded: 0, totalAmount: itemsTotal + 30 },
    customerNote: null,
    statusReason: null,
    cancelledBy: null,
    cancellable: false,
    deliveryPartnerName: null,
    deliveryPartnerPhone: null,
    timeline,
    placedAt: timeline[0].at,
    deliveredAt: null,
    ...details,
  }
  const payment: Payment = {
    id: id + 4000,
    orderId: id,
    customerUserId: order.customerUserId,
    storeId: store.id,
    amount: order.amounts.totalAmount,
    method: 'COD',
    status: order.paymentStatus === 'PAID' ? 'COLLECTED' : order.paymentStatus === 'CASH_ON_DELIVERY' ? 'CASH_ON_DELIVERY' : 'CANCELLED',
    canPayNow: false,
    gatewayOrderId: null,
    gatewayPaymentId: null,
    payBefore: null,
    failureReason: null,
    refundReference: null,
    paidAt: order.deliveredAt,
    refundedAt: null,
    createdAt: order.placedAt,
  }
  return { order, payment }
}

function seedOrders(): MockOrder[] {
  const delivered = pastTimeline(3, [
    ['PENDING', 'Order placed', 'CUSTOMER', 0],
    ['CONFIRMED', 'Confirmed - pay cash on delivery', 'SYSTEM', 1],
    ['ACCEPTED', 'The store accepted your order', 'VENDOR', 6],
    ['PACKED', 'Your items are packed', 'VENDOR', 20],
    ['READY_FOR_PICKUP', 'Waiting for a delivery partner', 'VENDOR', 25],
    ['OUT_FOR_DELIVERY', 'On the way to you with Arjun Singh', 'DELIVERY_PARTNER', 40],
    ['DELIVERED', 'Delivered', 'DELIVERY_PARTNER', 65],
  ])
  const cancelled = pastTimeline(2, [
    ['PENDING', 'Order placed', 'CUSTOMER', 0],
    ['CONFIRMED', 'Confirmed - pay cash on delivery', 'SYSTEM', 1],
    ['CANCELLED', 'Ordered by mistake', 'CUSTOMER', 4],
  ])
  // 20 minutes ago: a new order of customer3 waiting for Fresh Mart (so the vendor's queue has work in mock mode)
  const waiting = pastTimeline(20 / (24 * 60), [
    ['PENDING', 'Order placed', 'CUSTOMER', 0],
    ['CONFIRMED', 'Confirmed - pay cash on delivery', 'SYSTEM', 1],
  ])
  return [
    pastOrder(901, 'DELIVERED', [orderItem(1, 2), orderItem(6, 1), orderItem(8, 1)], delivered, {
      deliveryPartnerName: 'Arjun Singh',
      deliveryPartnerPhone: '9000000005',
      deliveredAt: delivered[6].at,
    }),
    pastOrder(902, 'CANCELLED', [orderItem(5, 1)], cancelled, { paymentStatus: 'CANCELLED', statusReason: 'Ordered by mistake', cancelledBy: 'CUSTOMER' }),
    pastOrder(903, 'CONFIRMED', [orderItem(2, 2), orderItem(8, 2)], waiting, {
      customerUserId: 9,
      paymentStatus: 'CASH_ON_DELIVERY',
      cancellable: true,
      customerNote: 'Please call before coming',
      deliveryAddress: {
        contactName: 'Anita Desai',
        contactPhone: '9000000009',
        addressLine1: '22, Domlur Layout',
        addressLine2: null,
        landmark: 'Near Domlur Flyover',
        city: 'Bengaluru',
        state: 'Karnataka',
        pincode: '560071',
      },
    }),
  ]
}

let orders: MockOrder[] = seedOrders()
let nextOrderId = 1001
let nextPaymentId = 5001

/**
 * Where each order goes on the map (the address's coordinates, which order-service copies but OrderResponse does not
 * show): delivery-service gets them with OrderReadyForPickup for the tracking map.
 */
let deliveryLocations = new Map<number, GeoPoint>()

/** For tests: back to the seed orders. */
/** "customerUserId:Idempotency-Key" -> the order that request placed (order-service's unique key) */
let ordersByKey = new Map<string, number>()
/** order id -> the checkout fingerprint of the cart it was made of */
let cartOfOrder = new Map<number, string>()
/** The same cart ordered again within this time is taken for a double order */
const SAME_CART_WINDOW_MS = 2 * 60 * 1000
const IDEMPOTENCY_KEY = /^[A-Za-z0-9_-]{8,64}$/

export function resetMockOrders(): void {
  orders = seedOrders()
  nextOrderId = 1001
  nextPaymentId = 5001
  deliveryLocations = new Map()
  ordersByKey = new Map()
  cartOfOrder = new Map()
}

/** Moves the order to a status and adds the step to its tracking timeline. */
function moveTo(order: Order, status: OrderStatus, note: string, changedBy: ChangedBy): void {
  order.status = status
  order.cancellable = CANCELLABLE.includes(status)
  order.timeline.push({ status, note, changedBy, at: now() })
}

/** "45, 100 Feet Road, Indiranagar, Opposite CMH Hospital, Bengaluru 560038" (StoreInfo.oneLineAddress) */
function oneLineAddress(store: Store): string {
  const street = [store.addressLine1, store.addressLine2].filter((part) => part?.trim()).join(', ')
  return `${street}, ${store.city} ${store.pincode}`
}

/** The @Valid rules of PlaceOrderRequest. */
function checkPlaceOrderRequest(body: Partial<PlaceOrderRequest>): void {
  const errors: ApiErrorDetail[] = []
  if (body.addressId === undefined || body.addressId === null) errors.push({ field: 'addressId', message: 'must not be null' })
  else if (body.addressId <= 0) errors.push({ field: 'addressId', message: 'must be greater than 0' })
  if (!body.paymentMethod) errors.push({ field: 'paymentMethod', message: 'must not be null' })
  if (body.note && body.note.length > 250) errors.push({ field: 'note', message: 'size must be between 0 and 250' })
  if (errors.length > 0) throw validationFailed(errors)
}

/**
 * CustomerOrderServiceImpl.placeOrder: the cart (checked), the address and the store, then a PENDING order.
 * Never two orders for one request: a repeat with the same Idempotency-Key gets the first order; the same cart ordered
 * again a moment later is refused (409); with the summary's fingerprint, a cart that changed since is refused (409).
 */
export function placeOrder(customerUserId: number, request: Partial<PlaceOrderRequest>, idempotencyKey?: string): Order {
  checkPlaceOrderRequest(request)
  if (idempotencyKey !== undefined && !IDEMPOTENCY_KEY.test(idempotencyKey)) {
    throw new MockApiError(400, 'The Idempotency-Key header must be 8-64 letters, digits, - or _')
  }
  const placedBefore = idempotencyKey && ordersByKey.get(`${customerUserId}:${idempotencyKey}`)
  if (placedBefore) return findMine(customerUserId, placedBefore).order

  const summary = checkout(customerUserId)
  if (!summary.readyForCheckout) {
    const problems = summary.problems.map((problem) => ({ field: null, message: problem }))
    throw new MockApiError(409, 'Your cart cannot be ordered yet. Please fix the problems listed and try again.', problems)
  }
  if (request.expectedCheckoutFingerprint && request.expectedCheckoutFingerprint !== summary.checkoutFingerprint) {
    throw new MockApiError(409, 'Your cart or its prices changed since the summary. Please check the new total.')
  }
  const sameCart = orders.find(
    ({ order }) =>
      order.customerUserId === customerUserId &&
      cartOfOrder.get(order.id) === summary.checkoutFingerprint &&
      order.status !== 'CANCELLED' &&
      order.status !== 'REJECTED' &&
      Date.now() - Date.parse(order.placedAt) < SAME_CART_WINDOW_MS,
  )
  if (sameCart) throw new MockApiError(409, `You placed this order a moment ago (order #${sameCart.order.id}).`)
  const address = findAddress(customerUserId, request.addressId!)
  const store = storeById(summary.storeId!)
  if (!store) throw new MockApiError(400, 'The store of your cart no longer exists')
  if (store.status !== 'ACTIVE' || !store.acceptingOrders) throw new MockApiError(400, `${store.name} is not taking orders right now`)
  if (!deliversTo(store.id, address.pincode)) {
    throw new MockApiError(400, `${store.name} does not deliver to PIN code ${address.pincode}. Please choose another address.`)
  }

  const { totals } = summary
  const order: Order = {
    id: nextOrderId++,
    customerUserId,
    storeId: store.id,
    storeName: store.name,
    storePhone: store.phone,
    storeAddress: oneLineAddress(store),
    status: 'PENDING',
    paymentMethod: request.paymentMethod!,
    paymentStatus: 'PENDING',
    items: summary.items.map(({ sellingPrice, ...item }) => ({ ...item, unitPrice: sellingPrice })),
    deliveryAddress: {
      contactName: address.contactName,
      contactPhone: address.contactPhone,
      addressLine1: address.addressLine1,
      addressLine2: address.addressLine2,
      landmark: address.landmark,
      city: address.city,
      state: address.state,
      pincode: address.pincode,
    },
    amounts: {
      itemsTotal: totals.itemsTotal,
      couponCode: summary.couponCode,
      couponDiscount: totals.couponDiscount,
      deliveryFee: totals.deliveryFee,
      taxIncluded: totals.taxIncluded,
      totalAmount: totals.payableAmount,
    },
    customerNote: request.note?.trim() || null,
    statusReason: null,
    cancelledBy: null,
    cancellable: true,
    deliveryPartnerName: null,
    deliveryPartnerPhone: null,
    timeline: [],
    placedAt: now(),
    deliveredAt: null,
  }
  moveTo(order, 'PENDING', 'Order placed', 'CUSTOMER')
  if (address.latitude !== null && address.longitude !== null) {
    deliveryLocations.set(order.id, { latitude: address.latitude, longitude: address.longitude })
  }

  const payment: Payment = {
    id: nextPaymentId++,
    orderId: order.id,
    customerUserId,
    storeId: store.id,
    amount: totals.payableAmount,
    method: order.paymentMethod,
    status: 'PENDING',
    canPayNow: false,
    gatewayOrderId: null,
    gatewayPaymentId: null,
    payBefore: null,
    failureReason: null,
    refundReference: null,
    paidAt: null,
    refundedAt: null,
    createdAt: now(),
  }
  orders.push({ order, payment })
  if (summary.checkoutFingerprint) cartOfOrder.set(order.id, summary.checkoutFingerprint)
  if (idempotencyKey) ordersByKey.set(`${customerUserId}:${idempotencyKey}`, order.id)
  clearCart(customerUserId) // cart-service empties the cart when it hears OrderCreated
  return order
}

/** StockReserved: cash on delivery is accepted at once (order CONFIRMED); an online payment now waits for the customer. */
function reserveStock({ order, payment }: MockOrder): void {
  if (order.status !== 'PENDING' || payment.status !== 'PENDING') return
  if (order.paymentMethod === 'COD') {
    payment.status = 'CASH_ON_DELIVERY'
    order.paymentStatus = 'CASH_ON_DELIVERY'
    moveTo(order, 'CONFIRMED', 'Confirmed - pay cash on delivery', 'SYSTEM')
    notify(
      order.customerUserId,
      'ORDER_CONFIRMED',
      `Order #${order.id} confirmed`,
      `Please pay ${formatPrice(order.amounts.totalAmount)} in cash on delivery. We will tell you when the store accepts it.`,
      order.id,
    )
    notifyNewOrder(order)
  } else {
    payment.status = 'AWAITING_PAYMENT'
    payment.canPayNow = true
    payment.gatewayOrderId = `mock_order_${order.id}`
    payment.payBefore = new Date(Date.now() + ONLINE_PAYMENT_MINUTES * 60 * 1000).toISOString()
  }
}

/** Another customer's order looks like one that does not exist. */
function findMine(customerUserId: number, orderId: number, notFoundMessage = `Order not found with id: ${orderId}`): MockOrder {
  const found = orders.find(({ order }) => order.id === orderId && order.customerUserId === customerUserId)
  if (!found) throw new MockApiError(404, notFoundMessage)
  reserveStock(found)
  return found
}

function findMyPayment(customerUserId: number, orderId: number): MockOrder {
  return findMine(customerUserId, orderId, `No payment found for order ${orderId}. If you have just placed it, try again in a few seconds.`)
}

export function getOrder(customerUserId: number, orderId: number): Order {
  return findMine(customerUserId, orderId).order
}

/**
 * The order as order-service answers the customer: with paymentUrl, the page where an online order that is not paid
 * yet is paid (OrderMapper; Order.awaitsOnlinePayment), null otherwise.
 */
export function withPaymentUrl(order: Order): Order {
  const awaitsOnlinePayment = order.paymentMethod === 'ONLINE' && order.paymentStatus === 'PENDING'
  return { ...order, paymentUrl: awaitsOnlinePayment ? `/orders/${order.id}` : null }
}

/** The order whose invoice is asked for (InvoicePdfGenerator: only once the store accepted it). */
export function orderWithInvoice(customerUserId: number, orderId: number): Order {
  const { order } = findMine(customerUserId, orderId)
  if (!WITH_INVOICE.includes(order.status)) {
    throw new MockApiError(400, `Order ${order.id} has no invoice (it is ${order.status}). Invoices exist once the store accepted the order.`)
  }
  return order
}

function toSummary(order: Order): OrderSummary {
  return {
    id: order.id,
    customerUserId: order.customerUserId,
    storeId: order.storeId,
    storeName: order.storeName,
    status: order.status,
    paymentMethod: order.paymentMethod,
    paymentStatus: order.paymentStatus,
    itemCount: order.items.reduce((units, item) => units + item.quantity, 0),
    totalAmount: order.amounts.totalAmount,
    placedAt: order.placedAt,
  }
}

/** The orders that match, moved on first (time passes on every read), newest first, optionally one status. */
function search(matches: (order: Order) => boolean, status: OrderStatus | null): OrderSummary[] {
  return orders
    .filter(({ order }) => matches(order))
    .map((entry) => {
      reserveStock(entry)
      return entry.order
    })
    .filter((order) => status === null || order.status === status)
    .sort((a, b) => b.id - a.id)
    .map(toSummary)
}

/** The order history, newest first (optionally one status). */
export function listOrders(customerUserId: number, status: OrderStatus | null): OrderSummary[] {
  return search((order) => order.customerUserId === customerUserId, status)
}

export function getPayment(customerUserId: number, orderId: number): Payment {
  return findMyPayment(customerUserId, orderId).payment
}

/** Cancels the order; the compensation (refund of a paid order) happens at once in the mock. */
function cancel(entry: MockOrder, reason: string, cancelledBy: ChangedBy): void {
  const { order } = entry
  moveTo(order, 'CANCELLED', reason, cancelledBy)
  order.statusReason = reason
  order.cancelledBy = cancelledBy
  notify(order.customerUserId, 'ORDER_CANCELLED', `Order #${order.id} cancelled`, `Reason: ${reason}.`, order.id)
  cancelDeliveryOfOrder(order.id)
  // The store hears about it too, unless the system cancelled it (e.g. a failed payment: the store never saw that order)
  if (cancelledBy !== 'SYSTEM') {
    notifyStore(order, 'ORDER_CANCELLED', `Order #${order.id} was cancelled`, `Cancelled by the ${cancelledBy.toLowerCase()}. Reason: ${reason}. Do not pack it.`)
  }
  settlePayment(entry)
}

/** A stopped order owes nothing: a paid one is refunded (at once in the mock), an unpaid one is cancelled. */
function settlePayment({ order, payment }: MockOrder): void {
  payment.canPayNow = false
  if (payment.status === 'PAID') {
    payment.status = 'REFUNDED'
    payment.refundReference = `mock_refund_${order.id}`
    payment.refundedAt = now()
    order.paymentStatus = 'REFUNDED'
    notify(
      order.customerUserId,
      'REFUND_COMPLETED',
      `Refund for order #${order.id}`,
      `We refunded ${formatPrice(payment.amount)} (reference ${payment.refundReference}). It can take a few days to show in your account.`,
      order.id,
    )
  } else if (payment.status !== 'FAILED') {
    payment.status = 'CANCELLED'
    order.paymentStatus = 'CANCELLED'
  }
}

/** CustomerPaymentServiceImpl.payWithMockGateway: success pays the order, otherwise the bank "declines" and the order is cancelled. */
export function payWithMockGateway(customerUserId: number, orderId: number, success: boolean): Payment {
  const entry = findMyPayment(customerUserId, orderId)
  const { order, payment } = entry
  if (payment.status === 'PENDING') throw new MockApiError(400, 'We are still reserving your items. Please try again in a few seconds.')
  if (payment.status !== 'AWAITING_PAYMENT') throw new MockApiError(400, `This payment cannot be paid (status ${payment.status})`)

  payment.canPayNow = false
  if (success) {
    payment.status = 'PAID'
    payment.gatewayPaymentId = `mock_pay_${order.id}_${Date.now()}`
    payment.paidAt = now()
    order.paymentStatus = 'PAID'
    moveTo(order, 'CONFIRMED', 'Payment received', 'SYSTEM')
    notify(
      order.customerUserId,
      'ORDER_CONFIRMED',
      `Order #${order.id} confirmed`,
      `We received your payment of ${formatPrice(payment.amount)}. We will tell you when the store accepts it.`,
      order.id,
    )
    notifyNewOrder(order)
  } else {
    payment.status = 'FAILED'
    payment.failureReason = MOCK_DECLINE_REASON
    order.paymentStatus = 'FAILED'
    notify(order.customerUserId, 'PAYMENT_FAILED', `Payment for order #${order.id} failed`, `${MOCK_DECLINE_REASON}. The order was cancelled; no money was taken.`, order.id)
    cancel(entry, `Payment failed: ${MOCK_DECLINE_REASON}`, 'SYSTEM')
  }
  return payment
}

export function cancelMyOrder(customerUserId: number, orderId: number, reason: string | null | undefined): Order {
  if (reason && reason.length > 255) throw validationFailed([{ field: 'reason', message: 'size must be between 0 and 255' }])
  const entry = findMine(customerUserId, orderId)
  if (!entry.order.cancellable) throw new MockApiError(400, `An order that is ${entry.order.status} can no longer be cancelled`)
  cancel(entry, reason?.trim() || DEFAULT_CANCEL_REASON, 'CUSTOMER')
  return entry.order
}

// ---------- The store's side (VendorOrderServiceImpl) ----------

/** A notification for the owner of the order's store. */
function notifyStore(order: Order, type: string, title: string, message: string): void {
  const owner = storeById(order.storeId)?.ownerUserId
  if (owner !== undefined) notify(owner, type, title, message, order.id)
}

/** OrderConfirmed: the store gets "New order #12". */
function notifyNewOrder(order: Order): void {
  notifyStore(order, 'NEW_ORDER', `New order #${order.id}`, `A new order of ${formatPrice(order.amounts.totalAmount)} (${order.paymentMethod}) is waiting. Accept or reject it in your store panel.`)
}

/** OrderStatus.canMoveTo: the store's steps. */
const STORE_STEPS: Partial<Record<OrderStatus, OrderStatus[]>> = {
  CONFIRMED: ['ACCEPTED', 'REJECTED'],
  ACCEPTED: ['PACKED'],
  PACKED: ['READY_FOR_PICKUP'],
}

/** Another store's order looks like one that does not exist. */
function findStoreOrder(storeId: number, orderId: number): MockOrder {
  const found = orders.find(({ order }) => order.id === orderId && order.storeId === storeId)
  if (!found) throw new MockApiError(404, `Order not found with id: ${orderId}`)
  reserveStock(found)
  return found
}

/** Order.moveTo with its check: "Order 12 is PENDING, so it cannot become ACCEPTED". */
function storeStep(storeId: number, orderId: number, next: OrderStatus, note: string): MockOrder {
  const entry = findStoreOrder(storeId, orderId)
  const { order } = entry
  if (!STORE_STEPS[order.status]?.includes(next)) throw new MockApiError(400, `Order ${order.id} is ${order.status}, so it cannot become ${next}`)
  moveTo(order, next, note, 'VENDOR')
  return entry
}

/** Every order of a store, moved on first (for the mock reports). */
export function storeOrders(storeId: number): Order[] {
  return orders
    .filter(({ order }) => order.storeId === storeId)
    .map((entry) => {
      reserveStock(entry)
      return entry.order
    })
}

export function listStoreOrders(storeId: number, status: OrderStatus | null): OrderSummary[] {
  return search((order) => order.storeId === storeId, status)
}

export function getStoreOrder(storeId: number, orderId: number): Order {
  return findStoreOrder(storeId, orderId).order
}

export function storeOrderWithInvoice(storeId: number, orderId: number): Order {
  const { order } = findStoreOrder(storeId, orderId)
  if (!WITH_INVOICE.includes(order.status)) {
    throw new MockApiError(400, `Order ${order.id} has no invoice (it is ${order.status}). Invoices exist once the store accepted the order.`)
  }
  return order
}

export function acceptOrder(storeId: number, orderId: number): Order {
  const { order } = storeStep(storeId, orderId, 'ACCEPTED', 'The store accepted your order')
  notify(order.customerUserId, 'ORDER_ACCEPTED', `Order #${order.id} accepted`, 'The store is preparing your order.', order.id)
  return order
}

/** The stock goes back and a paid order is refunded; the reason is shown to the customer. */
export function rejectOrder(storeId: number, orderId: number, reason: string | undefined): Order {
  if (!reason?.trim()) throw validationFailed([{ field: 'reason', message: 'must not be blank' }])
  if (reason.length > 255) throw validationFailed([{ field: 'reason', message: 'size must be between 0 and 255' }])
  const entry = storeStep(storeId, orderId, 'REJECTED', reason.trim())
  const { order } = entry
  order.statusReason = reason.trim()
  notify(
    order.customerUserId,
    'ORDER_REJECTED',
    `Order #${order.id} could not be accepted`,
    `The store rejected your order. Reason: ${reason.trim()}. If you paid online, the money will be refunded.`,
    order.id,
  )
  settlePayment(entry)
  return order
}

export function packOrder(storeId: number, orderId: number): Order {
  const { order } = storeStep(storeId, orderId, 'PACKED', 'Your items are packed')
  notify(order.customerUserId, 'ORDER_PACKED', `Order #${order.id} packed`, 'Your items are packed and wait for a delivery partner.', order.id)
  return order
}

/** delivery-service makes a delivery job: an online partner of the zone gets it at once, otherwise it waits. */
export function markReadyForPickup(storeId: number, orderId: number): Order {
  const { order } = storeStep(storeId, orderId, 'READY_FOR_PICKUP', 'Waiting for a delivery partner')
  const assignment = createDelivery(order, deliveryLocations.get(order.id) ?? null)
  if (assignment) partnerAssigned(order.id, assignment.partner)
  return order
}

// ---------- The admin's side (AdminOrderServiceImpl, AdminPaymentController) ----------

/** Every order, moved on first (for the mock reports and the admin's lists). */
export function allOrders(): Order[] {
  return orders.map((entry) => {
    reserveStock(entry)
    return entry.order
  })
}

/** All orders, newest first, filtered by customer, store and status. */
export function searchAllOrders(filter: { customerUserId: number | null; storeId: number | null; status: OrderStatus | null }): OrderSummary[] {
  return search((order) => (filter.customerUserId === null || order.customerUserId === filter.customerUserId) && (filter.storeId === null || order.storeId === filter.storeId), filter.status)
}

function findAnyOrder(orderId: number): MockOrder {
  const found = orders.find(({ order }) => order.id === orderId)
  if (!found) throw new MockApiError(404, `Order not found with id: ${orderId}`)
  reserveStock(found)
  return found
}

export function adminGetOrder(orderId: number): Order {
  return findAnyOrder(orderId).order
}

export function adminOrderWithInvoice(orderId: number): Order {
  const { order } = findAnyOrder(orderId)
  if (!WITH_INVOICE.includes(order.status)) {
    throw new MockApiError(400, `Order ${order.id} has no invoice (it is ${order.status}). Invoices exist once the store accepted the order.`)
  }
  return order
}

/** A problem order, cancelled by an admin (reason required) while it is still with the store; a paid one is refunded. */
export function adminCancelOrder(orderId: number, reason: unknown): Order {
  if (typeof reason !== 'string' || !reason.trim()) throw validationFailed([{ field: 'reason', message: 'must not be blank' }])
  if (reason.length > 255) throw validationFailed([{ field: 'reason', message: 'size must be between 0 and 255' }])
  const entry = findAnyOrder(orderId)
  if (!entry.order.cancellable) throw new MockApiError(400, `An order that is ${entry.order.status} can no longer be cancelled`)
  cancel(entry, reason.trim(), 'ADMIN')
  return entry.order
}

/** DeliveryAssigned: the order shows who brings it; the customer and the partner are told. */
export function partnerAssigned(orderId: number, partner: { userId: number; fullName: string; phone: string }): void {
  const { order } = findAnyOrder(orderId)
  order.deliveryPartnerName = partner.fullName
  order.deliveryPartnerPhone = partner.phone
  notify(order.customerUserId, 'DELIVERY_PARTNER_ASSIGNED', `A delivery partner for order #${order.id}`, `${partner.fullName} (${partner.phone}) will bring your order.`, order.id)
  notify(partner.userId, 'NEW_DELIVERY', `New delivery: order #${order.id}`, 'Open the delivery app to accept or reject it.', order.id)
}

// ---------- The delivery partner's side (DeliveryEventsConsumer) ----------

/** DeliveryPickedUp: the order is on its way. */
export function orderPickedUp(orderId: number, partnerName: string): void {
  const { order } = findAnyOrder(orderId)
  moveTo(order, 'OUT_FOR_DELIVERY', `On the way to you with ${partnerName}`, 'DELIVERY_PARTNER')
  notify(order.customerUserId, 'ORDER_OUT_FOR_DELIVERY', `Order #${order.id} is on the way`, 'Your order left the store.', order.id)
}

/** DeliveryCompleted: delivered; a cash order is now paid (the partner collected it). */
export function orderDelivered(orderId: number): void {
  const entry = findAnyOrder(orderId)
  const { order, payment } = entry
  moveTo(order, 'DELIVERED', 'Delivered', 'DELIVERY_PARTNER')
  order.deliveredAt = now()
  if (payment.status === 'CASH_ON_DELIVERY') {
    payment.status = 'COLLECTED'
    payment.paidAt = now()
    order.paymentStatus = 'PAID'
  }
  notify(order.customerUserId, 'ORDER_DELIVERED', `Order #${order.id} delivered`, `Enjoy your groceries! Order total: ${formatPrice(order.amounts.totalAmount)}.`, order.id)
}

/** DeliveryFailed: "Delivery failed: customer unavailable (Called twice)"; nothing is owed, a paid order is refunded. */
export function orderDeliveryFailed(orderId: number, issue: string, note: string | null): void {
  const entry = findAnyOrder(orderId)
  const { order } = entry
  const reason = `Delivery failed: ${issue.replaceAll('_', ' ').toLowerCase()}${note ? ` (${note})` : ''}`
  moveTo(order, 'DELIVERY_FAILED', reason, 'DELIVERY_PARTNER')
  order.statusReason = reason
  notify(order.customerUserId, 'ORDER_DELIVERY_FAILED', `Order #${order.id} could not be delivered`, `${reason}. If you paid online, the money will be refunded. Nothing is owed for cash on delivery.`, order.id)
  notifyStore(order, 'ORDER_DELIVERY_FAILED', `Order #${order.id} was not delivered`, `${reason}. The delivery partner brings the items back to the store.`)
  settlePayment(entry)
}

/** Every payment, newest first, filtered by status, method and store. */
export function searchPayments(filter: { status: string | null; method: string | null; storeId: number | null }): Payment[] {
  return orders
    .map((entry) => {
      reserveStock(entry)
      return entry.payment
    })
    .filter((payment) => (!filter.status || payment.status === filter.status) && (!filter.method || payment.method === filter.method) && (filter.storeId === null || payment.storeId === filter.storeId))
    .sort((a, b) => b.id - a.id)
}

export function adminGetPayment(paymentId: number): Payment {
  const found = orders.find(({ payment }) => payment.id === paymentId)
  if (!found) throw new MockApiError(404, `Payment not found with id: ${paymentId}`)
  reserveStock(found)
  return found.payment
}

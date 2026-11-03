/*
 * TypeScript copies of the backend's JSON shapes.
 * Each type names the Java record it copies, so you can look it up in the backend (or in Swagger:
 * http://localhost:8080/swagger-ui.html). Money and quantities are plain numbers; dates are ISO strings.
 */

// ---------- Common wrappers (every service) ----------

/** ErrorDetail: one validation or business error. */
export interface ApiErrorDetail {
  field: string | null
  message: string
}

/** ApiResponse: the wrapper around EVERY response body. */
export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  errors: ApiErrorDetail[] | null
  timestamp: string
  path: string | null
}

/** PageResponse: one page of a list. `page` starts at 0. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  last: boolean
}

// ---------- Catalog (product-read-service) ----------

export type ProductUnit = 'KG' | 'G' | 'L' | 'ML' | 'PIECE' | 'PACK' | 'DOZEN'

/** Customers only ever see ACTIVE products. */
export type ProductStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED' | 'DELETED'

export type ProductSort = 'RELEVANCE' | 'PRICE_LOW_TO_HIGH' | 'PRICE_HIGH_TO_LOW' | 'NAME' | 'NEWEST' | 'RATING'

/** CategoryResponse */
export interface Category {
  id: number
  name: string
  slug: string
  description: string | null
  imageUrl: string | null
  displayOrder: number | null
  active: boolean
}

/** ProductResponse */
export interface Product {
  id: number
  storeId: number
  categoryId: number
  categoryName: string
  name: string
  description: string | null
  brand: string | null
  sku: string
  unit: ProductUnit
  unitQuantity: number
  /** price before the discount (MRP) */
  price: number
  discountPercentage: number
  /** what the customer pays */
  sellingPrice: number
  taxPercentage: number
  imageUrls: string[]
  status: ProductStatus
  /** units customers can still buy (from inventory-service) */
  availableQuantity: number
  inStock: boolean
  /** at or below the store's low-stock threshold ("only a few left"), also true when out of stock */
  lowStock: boolean
  /** average stars of the visible reviews, null without reviews */
  ratingAverage: number | null
  ratingCount: number
  updatedAt: string
}

/**
 * ProductResponse of product-service: the product as saved, returned after a vendor's change. It has no stock and
 * no rating (those are in the read model, `Product`, a second or two later).
 */
export interface SavedProduct {
  id: number
  storeId: number
  categoryId: number
  categoryName: string
  name: string
  description: string | null
  brand: string | null
  sku: string
  unit: ProductUnit
  unitQuantity: number
  price: number
  discountPercentage: number
  sellingPrice: number
  taxPercentage: number
  imageUrls: string[]
  status: ProductStatus
  createdAt: string
  updatedAt: string
}

/** StockResponse (inventory-service): availableQuantity = quantityOnHand - reservedQuantity (units of open orders). */
export interface Stock {
  productId: number
  storeId: number
  quantityOnHand: number
  reservedQuantity: number
  availableQuantity: number
  /** "low stock" when the available units drop to this number */
  lowStockThreshold: number
  lowStock: boolean
  outOfStock: boolean
  updatedAt: string
}

/** The query parameters of GET /api/v1/products. Leave a field out to not filter on it. */
export interface ProductSearchParams {
  q?: string
  categoryId?: number
  storeId?: number
  brand?: string
  minPrice?: number
  maxPrice?: number
  /** true = hide products that are out of stock */
  inStockOnly?: boolean
  sort?: ProductSort
  /** 0-based */
  page?: number
  /** 1 to 100, default 20 */
  size?: number
}

/** ReviewResponse (product-service). Customers only see VISIBLE reviews. */
export interface Review {
  id: number
  productId: number
  storeId: number
  orderId: number
  reviewerName: string
  rating: number
  comment: string | null
  status: 'VISIBLE' | 'HIDDEN'
  hiddenReason: string | null
  createdAt: string
}

// ---------- Stores (store-service) ----------

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

/** OpeningHoursResponse. Times look like "07:00:00". */
export interface OpeningHours {
  dayOfWeek: DayOfWeek
  opensAt: string | null
  closesAt: string | null
  closed: boolean
}

/** PublicStoreResponse: what customers see about a store. */
export interface Store {
  id: number
  name: string
  description: string | null
  phone: string
  addressLine1: string
  addressLine2: string | null
  city: string
  state: string
  pincode: string
  latitude: number | null
  longitude: number | null
  acceptingOrders: boolean
  openNow: boolean
  openingHours: OpeningHours[]
}

// ---------- Coupons and cart (cart-service) ----------

export type DiscountType = 'PERCENTAGE' | 'FLAT'

/** PublicCouponResponse. storeId null = valid in every store. */
export interface Coupon {
  code: string
  description: string | null
  storeId: number | null
  discountType: DiscountType
  discountValue: number
  maxDiscountAmount: number | null
  minOrderAmount: number
  validUntil: string
}

/** CouponResponse: a coupon with everything the vendor (or admin) manages. storeId null = a platform coupon. */
export interface CouponDetails {
  id: number
  code: string
  description: string
  storeId: number | null
  discountType: DiscountType
  /** percent for PERCENTAGE (10 = 10 %), rupees for FLAT */
  discountValue: number
  /** the most a PERCENTAGE coupon takes off, or null */
  maxDiscountAmount: number | null
  /** the items total a cart needs (0 = no minimum) */
  minOrderAmount: number
  validFrom: string
  validUntil: string
  /** how often it may be used in total, or null for no limit */
  usageLimit: number | null
  usedCount: number
  perCustomerLimit: number
  /** false = switched off by the vendor */
  active: boolean
  createdAt: string
  updatedAt: string
}

/** CartItemResponse */
export interface CartItem {
  productId: number
  productName: string
  unit: ProductUnit
  unitQuantity: number
  imageUrl: string | null
  /** one unit before the product discount (MRP) */
  price: number
  /** one unit, what the customer pays */
  sellingPrice: number
  taxPercentage: number
  quantity: number
  /** sellingPrice x quantity */
  lineTotal: number
}

/** CartTotalsResponse: payableAmount = itemsTotal - couponDiscount + deliveryFee. All prices include tax. */
export interface CartTotals {
  itemsTotal: number
  productSavings: number
  couponDiscount: number
  deliveryFee: number
  payableAmount: number
  taxIncluded: number
  minimumOrderValue: number
  minimumOrderMet: boolean
}

/** CartResponse. storeId is null for an empty cart. */
export interface Cart {
  customerUserId: number
  storeId: number | null
  items: CartItem[]
  itemCount: number
  couponCode: string | null
  /** what happened to the coupon: "you save ..." or why it cannot be used right now */
  couponMessage: string | null
  totals: CartTotals
}

/** CheckoutSummaryResponse: the cart checked against the current catalog. */
export interface CheckoutSummary {
  customerUserId: number
  storeId: number | null
  items: CartItem[]
  couponCode: string | null
  totals: CartTotals
  readyForCheckout: boolean
  /** what must be fixed first, e.g. "Pomegranate is out of stock" */
  problems: string[]
  /** changes with any product, quantity, price, coupon or total (an order from a confirmed summary sends it back) */
  checkoutFingerprint?: string
}

/** StoreStatus: register → PENDING_APPROVAL → (admin) ACTIVE or REJECTED; an admin can also disable an ACTIVE store. */
export type StoreStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'DISABLED'

/**
 * StoreResponse: everything about a store, for its vendor (their own) and for admins (every store). The public Store
 * type leaves out the internal parts.
 */
export interface StoreDetails {
  id: number
  ownerUserId: number
  name: string
  description: string | null
  phone: string
  email: string | null
  addressLine1: string
  addressLine2: string | null
  city: string
  state: string
  pincode: string
  latitude: number | null
  longitude: number | null
  status: StoreStatus
  /** why the admin rejected or disabled it */
  statusReason: string | null
  /** the platform's share of each order, in percent */
  commissionPercentage: number
  acceptingOrders: boolean
  openNow: boolean
  openingHours: OpeningHours[]
  /** the PIN codes the store delivers to, sorted */
  deliveryPincodes: string[]
  approvedAt: string | null
  createdAt: string
}

// ---------- Reports (report-service) ----------

/** One day of DailySalesResponse. sales = what customers paid for the orders that count (items - coupons + delivery). */
export interface SalesDay {
  /** "2026-10-26" */
  date: string
  orders: number
  /** cancelled, rejected or not delivered */
  cancelledOrders: number
  salesOrders: number
  itemsTotal: number
  couponDiscounts: number
  deliveryFees: number
  sales: number
}

/** DailySalesResponse: one entry per day (days without orders have zeros). */
export interface DailySales {
  from: string
  to: string
  storeId: number | null
  days: SalesDay[]
  totalSales: number
  totalOrders: number
}

/** TopProductResponse: a best-selling product in a period. */
export interface TopProduct {
  productId: number
  productName: string
  quantity: number
  sales: number
}

// ---------- Login (auth-service) ----------

export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'VENDOR' | 'DELIVERY_PARTNER' | 'CUSTOMER'

/** UserResponse */
export interface User {
  id: number
  fullName: string
  email: string
  phone: string
  role: UserRole
  status: 'ACTIVE' | 'DISABLED'
  storeId: number | null
  lastLoginAt: string | null
  createdAt: string
}

/** AuthResponse: returned after login or registration. */
export interface AuthResult {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: User
}

/**
 * ForgotPasswordResponse: only filled in by the LOCAL backend (expose-token-in-response), so the reset can be tried
 * without reading the email. Everywhere else the answer has no data (null): the code arrives by email only.
 */
export interface ForgotPasswordResult {
  resetToken: string
  expiresAt: string
}

// ---------- Notifications (notification-service) ----------

/** NotificationResponse: one message of the bell menu. referenceId = the order it is about (for order types). */
export interface AppNotification {
  id: number
  userId: number
  /** e.g. ORDER_CONFIRMED, ORDER_DELIVERED, REFUND_COMPLETED, WELCOME (see NotificationType in notification-service) */
  type: string
  title: string
  message: string
  referenceId: number | null
  read: boolean
  emailStatus: string
  createdAt: string
}

// ---------- Platform (platform-service) ----------

/** SettingsResponse */
export interface PlatformSettings {
  currency: string
  deliveryFee: number
  freeDeliveryFrom: number
  minimumOrderValue: number
  supportEmail: string
  supportPhone: string
  updatedAt: string
}

// ---------- Shopping assistant (mcp-client-service) ----------

/** ChatReplyResponse: the assistant's answer to one question. */
export interface ChatReply {
  /** send it with the next question to continue the conversation */
  conversationId: string
  /** plain text: shown as text, never as HTML */
  answer: string
  /** true when the answer comes from the shop's live data and passed the fact check */
  grounded: boolean
  /** the tools the assistant used, e.g. ["searchProducts"] */
  toolsUsed: string[]
  /** pages of the website that fit the answer (made from the tool results; at most 4); missing from an older service */
  links?: ChatLink[]
  /** a summary under the answer that the customer confirms with a button; null or missing without one */
  action?: ChatAction | null
  /**
   * where to pay an online order that waits for its payment, exactly as order-service sent it ("/orders/207"): the line
   * shows "Pay Now" for it. Null or missing otherwise.
   */
  paymentUrl?: string | null
  /** the human approval of `action` (PENDING until the customer clicks); null or missing without a card */
  approval?: ChatApproval | null
}

/**
 * ChatApprovalResponse: the human approval of a card of the chat. The assistant only prepares an order, a cancellation
 * or a cart change; a human (the customer) approves it with Confirm or rejects it with Cancel / Keep it. The backend
 * keeps the decision, so the chat history shows it again.
 */
export type ChatApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface ChatApproval {
  /** the card's id: the decision goes to POST /api/v1/chat/approvals/{id}; it is also the card's key */
  id: string
  status: ChatApprovalStatus
  /** when the card was shown (ISO); it can be confirmed for 10 minutes */
  createdAt: string
  /** when the customer approved or rejected it (ISO); null while pending */
  decidedAt: string | null
  /** who decided (the conversation's owner); null while pending */
  decidedByUserId: number | null
}

/**
 * ChatAction: a summary the customer can confirm with a button, made by the backend from a tool result (never from the
 * model's text). Nothing is added, ordered, cancelled or removed before the click.
 * - PLACE_ORDER: the order of the cart; Confirm places it with POST /api/v1/orders (and this browser's login), sending
 *   addressId, paymentMethod, the checkoutFingerprint and an Idempotency-Key.
 * - CANCEL_ORDER: one of the customer's orders; Confirm cancels it with POST /api/v1/orders/{orderId}/cancel.
 * - ADD_TO_CART: the products to add; Confirm makes each one's packs in the cart its newQuantity (POST
 *   /api/v1/cart/items when it is not in the cart, PUT /api/v1/cart/items/{productId} when it is), so a second click
 *   adds nothing twice.
 * - REMOVE_FROM_CART: lines of the customer's cart; Confirm takes them out with DELETE /api/v1/cart/items/{productId}.
 * - SET_CART_QUANTITY: a line of the cart as it is now and its new quantity; Confirm sets it with
 *   PUT /api/v1/cart/items/{productId}.
 * - CLEAR_CART: the whole cart; Confirm empties it with DELETE /api/v1/cart.
 */
export type ChatAction =
  | PlaceOrderAction
  | CancelOrderAction
  | AddToCartAction
  | RemoveFromCartAction
  | SetCartQuantityAction
  | ClearCartAction

/** One line of a card: "2 x Tomato (1 kg): ₹72" */
export interface ChatActionLine {
  name: string
  packSize: string | null
  quantity: number
  lineTotal: string
  /** ADD_TO_CART, REMOVE_FROM_CART and SET_CART_QUANTITY: the product the Confirm button adds, takes out or changes */
  productId?: number
  /** ADD_TO_CART: the packs of this product in the cart after the click (what it has now plus `quantity`) */
  newQuantity?: number
}

interface ChatActionBase {
  store?: string
  items: ChatActionLine[]
}

export interface PlaceOrderAction extends ChatActionBase {
  kind: 'PLACE_ORDER'
  itemsTotal: string
  couponDiscount?: string
  deliveryFee: string
  /** what the order costs, as a text from the shop, e.g. "₹102" */
  payable: string
  deliverTo: string
  payment: string
  /** only when the customer placed an order a few minutes ago */
  recentOrder?: string
  addressId: number
  paymentMethod: PaymentMethod
  checkoutFingerprint: string
}

export interface CancelOrderAction extends ChatActionBase {
  kind: 'CANCEL_ORDER'
  orderId: number
  /** what the order cost, as a text from the shop, e.g. "₹138" */
  payable: string
  /** where the order is now, e.g. "Packed by the store" */
  status: string
  /** what happens to the money, e.g. "The online payment of ₹138 will be refunded." */
  refund: string
  /** the customer's reason in their words */
  reason?: string | null
}

export interface AddToCartAction extends ChatActionBase {
  kind: 'ADD_TO_CART'
  /** each product to add: `quantity` packs for `lineTotal`, then `newQuantity` packs of it in the cart */
  items: (ChatActionLine & { productId: number; newQuantity: number })[]
}

export interface RemoveFromCartAction extends ChatActionBase {
  kind: 'REMOVE_FROM_CART'
  /** the line to take out, with its productId */
  items: (ChatActionLine & { productId: number })[]
}

export interface SetCartQuantityAction extends ChatActionBase {
  kind: 'SET_CART_QUANTITY'
  /** the line as it is now, with its productId */
  items: (ChatActionLine & { productId: number })[]
  /** the new number of packs (a total, not an addition) */
  newQuantity: number
}

export interface ClearCartAction extends ChatActionBase {
  kind: 'CLEAR_CART'
  /** what the cart costs now, as a text from the shop, e.g. "₹231" */
  payable: string
}

/**
 * ChatMessageRequest.ConfirmedCard: an order card the customer confirmed, sent with their next question. This browser
 * placed or cancelled the order itself, so the assistant would not know it otherwise ("cancel my last order" right
 * after ordering). Only the kind and the number: the backend writes its own line about it. (A removal from the cart is
 * not sent: the assistant reads the cart again for every question about it.)
 */
export interface ConfirmedCard {
  kind: PlaceOrderAction['kind'] | CancelOrderAction['kind']
  orderId: number
}

/** ChatLink: a page that fits an answer, e.g. { label: 'Your wishlist', path: '/wishlist' }. Checked before it is shown. */
export interface ChatLink {
  label: string
  path: string
}

/** ConversationResponse: the messages of a conversation, oldest first (only the latest are kept). */
export interface ChatConversation {
  conversationId: string
  /** oldest first; an answer that had a card brings it back with its human approval (both missing from an older backend) */
  messages: { role: 'USER' | 'ASSISTANT'; text: string; action?: ChatAction | null; approval?: ChatApproval | null }[]
}

/** FeatureFlagResponse: a switch the super admin turns on or off. updatedByUserId is null until someone changed it. */
export interface FeatureFlag {
  key: string
  description: string
  enabled: boolean
  updatedByUserId: number | null
  updatedAt: string
}

/** AuditEntryResponse: one event of a service (source = its Kafka topic) or one change in platform-service. */
export interface AuditEntry {
  id: number
  eventId: string
  eventType: string
  /** e.g. "grocery.order.events", or "platform-service" for settings, feature flags and banners */
  source: string
  /** what the event is about: an order, store, product or user id, a flag key ... */
  aggregateId: string | null
  userId: number | null
  storeId: number | null
  /** the whole event, usually as JSON text */
  details: string
  occurredAt: string
}

/** CommissionReportResponse: the platform's commission per store, on the orders delivered in the period. */
export interface CommissionReport {
  from: string
  to: string
  stores: StoreCommission[]
  totalItems: number
  totalCommission: number
  totalStorePayout: number
}

export interface StoreCommission {
  storeId: number
  storeName: string
  deliveredOrders: number
  itemsTotal: number
  commission: number
  storePayout: number
}

/** BannerResponse. linkUrl is a backend path such as "/categories/1" (see utils/links.ts). */
export interface Banner {
  id: number
  title: string
  subtitle: string | null
  imageUrl: string | null
  linkUrl: string | null
  position: number
  startsAt: string | null
  endsAt: string | null
  active: boolean
  /** true when customers see it right now */
  live: boolean
}

// ---------- Customer profile and addresses (customer-service) ----------

export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'PREFER_NOT_TO_SAY'

/** CustomerResponse: the profile used for deliveries. The login email and password live in auth-service. */
export interface CustomerProfile {
  id: number
  userId: number
  fullName: string
  email: string
  /** contact number for deliveries */
  phone: string
  /** "1994-05-17" */
  dateOfBirth: string | null
  gender: Gender | null
  addressCount: number
  createdAt: string
}

export type AddressLabel = 'HOME' | 'WORK' | 'OTHER'

/** AddressResponse: one of the customer's delivery addresses (at most 10). */
export interface Address {
  id: number
  label: AddressLabel
  contactName: string
  contactPhone: string
  addressLine1: string
  addressLine2: string | null
  landmark: string | null
  city: string
  state: string
  pincode: string
  latitude: number | null
  longitude: number | null
  /** exactly one address is the default; checkout picks it first */
  defaultAddress: boolean
}

// ---------- Orders (order-service) ----------

/**
 * The life of an order:
 *   PENDING → CONFIRMED → ACCEPTED → PACKED → READY_FOR_PICKUP → OUT_FOR_DELIVERY → DELIVERED
 * and the end states CANCELLED (customer, admin or system), REJECTED (store) and DELIVERY_FAILED.
 * PENDING = the stock is being reserved and the payment settled (in the background, a few seconds).
 */
export type OrderStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'ACCEPTED'
  | 'PACKED'
  | 'READY_FOR_PICKUP'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'DELIVERY_FAILED'
  | 'CANCELLED'
  | 'REJECTED'

/** COD = cash on delivery; ONLINE = paid online before the store gets the order. */
export type PaymentMethod = 'COD' | 'ONLINE'

/** OrderPaymentStatus: the payment as the order shows it (payment-service has the details). */
export type OrderPaymentStatus = 'PENDING' | 'PAID' | 'CASH_ON_DELIVERY' | 'FAILED' | 'CANCELLED' | 'REFUNDED'

/** ChangedBy: who moved the order on. SYSTEM = the background steps (stock reservation, payment). */
export type ChangedBy = 'CUSTOMER' | 'VENDOR' | 'ADMIN' | 'DELIVERY_PARTNER' | 'SYSTEM'

/** OrderItemResponse: a copy of the cart line when the order was placed. */
export interface OrderItem {
  productId: number
  productName: string
  unit: ProductUnit
  unitQuantity: number
  imageUrl: string | null
  /** one unit before the product discount (MRP) */
  price: number
  /** one unit, what the customer paid */
  unitPrice: number
  taxPercentage: number
  quantity: number
  /** unitPrice x quantity */
  lineTotal: number
}

/** DeliveryAddressResponse: a copy of the address when the order was placed (later edits do not change it). */
export interface DeliveryAddress {
  contactName: string
  contactPhone: string
  addressLine1: string
  addressLine2: string | null
  landmark: string | null
  city: string
  state: string
  pincode: string
}

/** OrderAmountsResponse: totalAmount = itemsTotal - couponDiscount + deliveryFee (all prices include tax). */
export interface OrderAmounts {
  itemsTotal: number
  couponCode: string | null
  couponDiscount: number
  deliveryFee: number
  taxIncluded: number
  totalAmount: number
}

/** TimelineEntryResponse: one step of the tracking timeline, e.g. PACKED "Your items are packed" by VENDOR. */
export interface TimelineEntry {
  status: OrderStatus
  note: string | null
  changedBy: ChangedBy
  at: string
}

/** OrderResponse: everything about one order, including the tracking timeline. */
export interface Order {
  id: number
  customerUserId: number
  storeId: number
  storeName: string
  storePhone: string
  storeAddress: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: OrderPaymentStatus
  /**
   * Where the customer pays an online order that is not paid yet, e.g. "/orders/12" (the order page, where the payment
   * window opens). null when nothing is to be paid online; missing from an older order-service.
   */
  paymentUrl?: string | null
  items: OrderItem[]
  deliveryAddress: DeliveryAddress
  amounts: OrderAmounts
  customerNote: string | null
  /** why it was cancelled, rejected or not delivered */
  statusReason: string | null
  cancelledBy: ChangedBy | null
  /** true while the customer may still cancel (until the order leaves the store) */
  cancellable: boolean
  /** null until a delivery partner is assigned */
  deliveryPartnerName: string | null
  deliveryPartnerPhone: string | null
  timeline: TimelineEntry[]
  placedAt: string
  deliveredAt: string | null
}

/** OrderSummaryResponse: one line of the order history. itemCount = number of units. */
export interface OrderSummary {
  id: number
  customerUserId: number
  storeId: number
  storeName: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: OrderPaymentStatus
  itemCount: number
  totalAmount: number
  placedAt: string
}

/** DeliveryRatingResponse (delivery-service). rating is null until the customer rates the delivery. */
export interface DeliveryRating {
  orderId: number
  partnerName: string | null
  rating: number | null
  comment: string | null
  ratedAt: string | null
}

// ---------- Payments (payment-service) ----------

/**
 * The life of a payment:
 *   COD:    PENDING (stock not reserved yet) → CASH_ON_DELIVERY → COLLECTED
 *   ONLINE: PENDING → AWAITING_PAYMENT → PAID (→ REFUNDED when the order is cancelled), or FAILED
 *   CANCELLED: the order was cancelled before anything was paid
 */
export type PaymentStatus = 'PENDING' | 'AWAITING_PAYMENT' | 'PAID' | 'FAILED' | 'CASH_ON_DELIVERY' | 'COLLECTED' | 'CANCELLED' | 'REFUNDED'

/** PaymentResponse */
export interface Payment {
  id: number
  orderId: number
  customerUserId: number
  storeId: number
  amount: number
  method: PaymentMethod
  status: PaymentStatus
  /** true when an online payment is waiting for the customer */
  canPayNow: boolean
  gatewayOrderId: string | null
  gatewayPaymentId: string | null
  /** an online payment must be made before this time, or the order is cancelled */
  payBefore: string | null
  failureReason: string | null
  refundReference: string | null
  paidAt: string | null
  refundedAt: string | null
  createdAt: string
}

// ---------- Admin panel (several services) ----------

/** DashboardResponse (report-service): the admin dashboard's numbers. "Today" is the India date. */
export interface AdminDashboard {
  today: string
  totalOrders: number
  ordersToday: number
  /** today's orders that count as sales (confirmed, not cancelled, rejected or failed) */
  salesToday: number
  /** the orders delivered today: the money actually earned today */
  revenueToday: number
  activeStores: number
  /** partners whose KYC is approved */
  activeDeliveryPartners: number
  totalCustomers: number
  /** customers who ordered in the last 30 days */
  activeCustomers: number
  /** orders that ended without delivery while paid online, and the refund is not done yet */
  pendingRefunds: number
  outOfStockProducts: number
  /** from placing to delivery, over the orders delivered in the last 30 days; null without any */
  averageDeliveryMinutes: number | null
  generatedAt: string
}

/** TopStoreResponse: a store with high sales in a period. */
export interface TopStore {
  storeId: number
  storeName: string
  orders: number
  sales: number
}

/** KycStatus (delivery-service): a new partner submits their documents (PENDING), an admin approves or rejects them. */
export type KycStatus = 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED'

export type VehicleType = 'BICYCLE' | 'SCOOTER' | 'MOTORBIKE'

/** PartnerResponse: a delivery partner and their KYC. */
export interface DeliveryPartner {
  userId: number
  fullName: string
  phone: string
  vehicleType: VehicleType | null
  vehicleNumber: string | null
  drivingLicenseNumber: string | null
  zoneId: number | null
  zoneName: string | null
  kycStatus: KycStatus
  /** the reason of a rejected KYC */
  kycNote: string | null
  online: boolean
  ratingAverage: number | null
  ratingCount: number
}

/** ZoneResponse: the PIN codes a group of partners serves, and what a partner earns per delivery there. */
export interface DeliveryZone {
  id: number
  name: string
  city: string
  pincodes: string[]
  partnerPayout: number
  active: boolean
}

/**
 * DeliveryStatus: WAITING_FOR_PARTNER → ASSIGNED → ACCEPTED → PICKED_UP → OUT_FOR_DELIVERY → DELIVERED, or FAILED
 * (→ RETURNED to the store); CANCELLED when the order is cancelled first.
 */
export type DeliveryStatus = 'WAITING_FOR_PARTNER' | 'ASSIGNED' | 'ACCEPTED' | 'PICKED_UP' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED' | 'RETURNED' | 'CANCELLED'

export type DeliveryIssue = 'CUSTOMER_UNAVAILABLE' | 'ADDRESS_WRONG' | 'ITEM_DAMAGED' | 'OTHER'

/** DeliveryResponse: one delivery job (one per order that reached "ready for pickup"). */
export interface Delivery {
  id: number
  orderId: number
  status: DeliveryStatus
  storeName: string
  storePhone: string
  storeAddress: string
  customerName: string
  customerPhone: string
  customerAddress: string
  paymentMethod: PaymentMethod
  /** cash the partner collects (0 for a paid order) */
  amountToCollect: number
  partnerUserId: number | null
  issue: DeliveryIssue | null
  issueNote: string | null
  proofPhotoUrl: string | null
  partnerPayout: number | null
  customerRating: number | null
  customerComment: string | null
  assignedAt: string | null
  pickedUpAt: string | null
  deliveredAt: string | null
  createdAt: string
  /** where the store is, for the map (null when the store has no coordinates) */
  storeLocation: GeoPoint | null
  /** where the customer's address is, for the map (null when unknown) */
  customerLocation: GeoPoint | null
}

// ---------- Live delivery tracking (delivery-service, docs/delivery-tracking.md) ----------

/** A point on the map in degrees (WGS84, as GPS and OpenStreetMap use). */
export interface GeoPoint {
  latitude: number
  longitude: number
}

/** LiveLocation: the partner's last shared position. */
export interface LiveLocation {
  latitude: number
  longitude: number
  /** how far off the position may be, in meters (null when the phone did not say) */
  accuracyMeters: number | null
  /** when the phone measured it */
  recordedAt: string
  /** when the server got it ("updated 20 s ago" is counted from this) */
  receivedAt: string
}

/**
 * TrackingRoute: the way along the streets to the next stop, from the routing service. Only when the backend has one
 * (app.tracking.routing.url); otherwise null and the map draws a straight dashed line, without an arrival time.
 */
export interface TrackingRoute {
  /** PICKUP: on the way to the store; DESTINATION: on the way to the customer */
  target: 'PICKUP' | 'DESTINATION'
  distanceMeters: number
  durationSeconds: number
  estimatedArrivalAt: string
  /** the line to draw: [[latitude, longitude], ...] */
  points: [number, number][]
}

/** TrackingResponse.Place: the store (pickup) or the customer's address (destination). */
export interface TrackingPlace {
  name: string
  address: string
  /** null when there are no coordinates (the map then cannot show it) */
  location: GeoPoint | null
}

/**
 * TrackingResponse: everything the tracking map shows. The live stream sends this whole object again after every change
 * (a new position or a new status), so a page simply replaces what it has.
 */
export interface TrackingSnapshot {
  orderId: number
  deliveryId: number
  status: DeliveryStatus
  /** true while the partner shares their position (from accepting the delivery until handing it over) */
  live: boolean
  /** null until a partner is assigned */
  partner: { name: string; phone: string; vehicleType: VehicleType | null } | null
  pickup: TrackingPlace
  destination: TrackingPlace
  /** null when not live, or nothing was shared yet */
  location: LiveLocation | null
  route: TrackingRoute | null
  generatedAt: string
}

/** LocationUpdateRequest: what the partner's phone sends. */
export interface LocationUpdate {
  latitude: number
  longitude: number
  accuracyMeters: number | null
  /** when the phone measured it (ISO date-time) */
  recordedAt: string
}

/** LocationUpdateResponse: stored = false when the update was skipped (too soon, or older than the stored one). */
export interface LocationUpdateResult {
  stored: boolean
  route: TrackingRoute | null
}

/** AvailabilityResponse: whether the partner is online, and until when unless they go online again (null when offline). */
export interface Availability {
  online: boolean
  onlineUntil: string | null
}

/** One day of EarningsResponse (only days with deliveries are listed). */
export interface DailyEarning {
  /** "2026-10-26" */
  date: string
  deliveries: number
  amount: number
}

/** EarningsResponse: what a partner earned from `from` to `to` (both included, India dates). */
export interface Earnings {
  from: string
  to: string
  deliveries: number
  total: number
  days: DailyEarning[]
}

/** PaymentTotals: how many payments of one method and status, and their amount. */
export interface PaymentTotals {
  method: PaymentMethod
  status: PaymentStatus
  count: number
  amount: number
}

/** PaymentSummaryResponse: money in and out for the payments created in a period (India dates). */
export interface PaymentSummary {
  from: string
  to: string
  paidOnline: number
  collectedCash: number
  refunded: number
  /** paid online + cash collected (a refunded payment is REFUNDED, so it is in neither) */
  received: number
  totals: PaymentTotals[]
}

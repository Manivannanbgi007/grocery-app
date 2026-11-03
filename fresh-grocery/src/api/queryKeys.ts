import type { AdminUserFilter } from './adminAccounts'
import type { AdminProductFilter, AdminReviewFilter } from './adminCatalog'
import type { AdminNotificationFilter } from './adminMarketing'
import type { AdminOrderFilter, AdminPaymentFilter } from './adminOrders'
import type { AdminStoreFilter } from './adminStores'
import type { AuditLogFilter } from './superAdmin'
import type { DeliveryStatus, KycStatus, OrderStatus, ProductSearchParams, StoreStatus } from './types'
import type { VendorProductFilter } from './vendorProducts'

/*
 * Every React Query key in one place.
 * A key identifies cached data: two components that use the same key share one request and one cache entry.
 */
export const queryKeys = {
  categories: ['categories'] as const,
  /** the number of products in one category (see useCategoryCounts) */
  categoryCount: (categoryId: number) => ['categories', categoryId, 'count'] as const,
  products: (params: ProductSearchParams) => ['products', params] as const,
  /** the search box's suggestions for a typed text (see useProductSuggestions) */
  productSuggestions: (text: string, categoryId: number | undefined) => ['products', 'suggestions', text, categoryId ?? 'all'] as const,
  product: (productId: number) => ['products', 'detail', productId] as const,
  reviews: (productId: number, page: number) => ['products', 'detail', productId, 'reviews', page] as const,
  stores: ['stores'] as const,
  store: (storeId: number) => ['stores', storeId] as const,
  platformSettings: ['platform', 'settings'] as const,
  /** the social login providers the shop offers (Google, Microsoft ...) */
  socialProviders: ['auth', 'social-providers'] as const,
  featureFlags: ['platform', 'feature-flags'] as const,
  banners: ['platform', 'banners'] as const,
  coupons: (storeId?: number) => ['coupons', storeId ?? 'platform'] as const,
  /** the public coupons of every store and of the platform */
  allCoupons: ['coupons'] as const,
  /** per user, so after a logout and another login nobody sees the previous person's cart */
  cart: (userId: number) => ['cart', userId] as const,
  /** the cart checked against today's catalog (GET /cart/checkout) */
  checkout: (userId: number) => ['cart', userId, 'checkout'] as const,
  allCarts: ['cart'] as const,
  // The customer's own data, under 'me' and the user id (dropped on every login and logout, like the cart)
  addresses: (userId: number) => ['me', userId, 'addresses'] as const,
  orders: (userId: number, filter: { status?: OrderStatus; page: number }, size: number) => ['me', userId, 'orders', 'list', filter, size] as const,
  /** the newest orders for the spending chart of My orders */
  ordersForSpending: (userId: number) => ['me', userId, 'orders', 'list', 'spending'] as const,
  /** every page and filter of the order history (reloaded after placing or cancelling an order) */
  allOrderLists: (userId: number) => ['me', userId, 'orders', 'list'] as const,
  order: (userId: number, orderId: number) => ['me', userId, 'orders', orderId] as const,
  payment: (userId: number, orderId: number) => ['me', userId, 'payments', orderId] as const,
  deliveryRating: (userId: number, orderId: number) => ['me', userId, 'delivery-rating', orderId] as const,
  profile: (userId: number) => ['me', userId, 'profile'] as const,
  /** the bell menu: the unread count and the latest notifications */
  notifications: (userId: number) => ['me', userId, 'notifications'] as const,
  unreadCount: (userId: number) => ['me', userId, 'notifications', 'unread-count'] as const,
  latestNotifications: (userId: number, unreadOnly: boolean) => ['me', userId, 'notifications', 'latest', unreadOnly] as const,
  /** one page of the notifications page */
  notificationPage: (userId: number, unreadOnly: boolean, page: number, size: number) => ['me', userId, 'notifications', 'page', unreadOnly, page, size] as const,
  allMine: ['me'] as const,
  // The vendor panel, under 'vendor' and the user id (also dropped on every login and logout)
  vendorStore: (userId: number) => ['vendor', userId, 'store'] as const,
  vendorOrders: (userId: number, filter: { status?: OrderStatus; page: number }, size: number) => ['vendor', userId, 'orders', 'list', filter, size] as const,
  /** the order queue's counters ("3 new") */
  vendorOrderCount: (userId: number, status: OrderStatus) => ['vendor', userId, 'orders', 'count', status] as const,
  /** every list and counter of the queue (reloaded after each step), but not the single orders */
  allVendorOrderLists: (userId: number) => ['vendor', userId, 'orders'] as const,
  vendorOrder: (userId: number, orderId: number) => ['vendor', userId, 'order', orderId] as const,
  vendorProducts: (userId: number, filter: VendorProductFilter) => ['vendor', userId, 'products', filter] as const,
  /** every list and counter of the vendor's products */
  allVendorProducts: (userId: number) => ['vendor', userId, 'products'] as const,
  vendorProduct: (userId: number, productId: number) => ['vendor', userId, 'product', productId] as const,
  vendorStock: (userId: number, productId: number) => ['vendor', userId, 'stock', productId] as const,
  vendorCoupons: (userId: number, page: number, size: number) => ['vendor', userId, 'coupons', page, size] as const,
  allVendorCoupons: (userId: number) => ['vendor', userId, 'coupons'] as const,
  vendorReviews: (userId: number, filter: { rating?: number; page: number }, size: number) => ['vendor', userId, 'reviews', filter, size] as const,
  vendorSales: (userId: number, from: string, to: string) => ['vendor', userId, 'reports', 'sales', from, to] as const,
  vendorTopProducts: (userId: number, from: string, to: string) => ['vendor', userId, 'reports', 'top-products', from, to] as const,
  allVendor: ['vendor'] as const,
  // The admin panel, under 'admin' and the user id (also dropped on every login and logout)
  adminDashboard: (userId: number) => ['admin', userId, 'dashboard'] as const,
  adminSales: (userId: number, from: string, to: string, storeId?: number) => ['admin', userId, 'reports', 'sales', from, to, storeId ?? 'all'] as const,
  adminTopStores: (userId: number, from: string, to: string, limit: number) => ['admin', userId, 'reports', 'top-stores', from, to, limit] as const,
  adminTopProducts: (userId: number, from: string, to: string, limit: number, storeId?: number) =>
    ['admin', userId, 'reports', 'top-products', from, to, limit, storeId ?? 'all'] as const,
  adminStores: (userId: number, filter: AdminStoreFilter, size: number) => ['admin', userId, 'stores', 'list', filter, size] as const,
  /** how many stores have one status (the menu's and the dashboard's "waiting for approval") */
  adminStoreCount: (userId: number, status: StoreStatus) => ['admin', userId, 'stores', 'count', status] as const,
  /** every list and counter of stores */
  allAdminStores: (userId: number) => ['admin', userId, 'stores'] as const,
  adminStore: (userId: number, storeId: number) => ['admin', userId, 'store', storeId] as const,
  /** every store's id and name, for the store filters of the lists */
  adminStoreOptions: (userId: number) => ['admin', userId, 'stores', 'options'] as const,
  adminOrders: (userId: number, filter: AdminOrderFilter, size: number) => ['admin', userId, 'orders', 'list', filter, size] as const,
  allAdminOrders: (userId: number) => ['admin', userId, 'orders'] as const,
  adminOrder: (userId: number, orderId: number) => ['admin', userId, 'order', orderId] as const,
  adminPayments: (userId: number, filter: AdminPaymentFilter, size: number) => ['admin', userId, 'payments', 'list', filter, size] as const,
  adminPaymentSummary: (userId: number, from: string, to: string) => ['admin', userId, 'payments', 'summary', from, to] as const,
  allAdminPayments: (userId: number) => ['admin', userId, 'payments'] as const,
  adminPartners: (userId: number, filter: { kycStatus?: KycStatus; page: number }, size: number) => ['admin', userId, 'partners', 'list', filter, size] as const,
  adminPartnerCount: (userId: number, kycStatus: KycStatus) => ['admin', userId, 'partners', 'count', kycStatus] as const,
  /** every list and counter of partners */
  allAdminPartners: (userId: number) => ['admin', userId, 'partners'] as const,
  adminZones: (userId: number) => ['admin', userId, 'zones'] as const,
  adminUsers: (userId: number, filter: AdminUserFilter, size: number) => ['admin', userId, 'users', 'list', filter, size] as const,
  adminUser: (userId: number, accountId: number) => ['admin', userId, 'users', 'detail', accountId] as const,
  /** every list and detail of accounts */
  allAdminUsers: (userId: number) => ['admin', userId, 'users'] as const,
  /** the customer profile with this login email (customer-service finds it by keyword) */
  adminCustomerByEmail: (userId: number, email: string) => ['admin', userId, 'customers', 'by-email', email] as const,
  adminCustomerAddresses: (userId: number, customerId: number) => ['admin', userId, 'customers', customerId, 'addresses'] as const,
  adminProducts: (userId: number, filter: AdminProductFilter, size: number) => ['admin', userId, 'products', 'list', filter, size] as const,
  adminProductLists: (userId: number) => ['admin', userId, 'products', 'list'] as const,
  adminProduct: (userId: number, productId: number) => ['admin', userId, 'products', 'detail', productId] as const,
  /** every list and detail of products */
  allAdminProducts: (userId: number) => ['admin', userId, 'products'] as const,
  adminCategories: (userId: number) => ['admin', userId, 'categories'] as const,
  adminReviews: (userId: number, filter: AdminReviewFilter, size: number) => ['admin', userId, 'reviews', filter, size] as const,
  allAdminReviews: (userId: number) => ['admin', userId, 'reviews'] as const,
  adminCoupons: (userId: number, filter: { storeId?: number; platformOnly?: boolean; page: number }, size: number) => ['admin', userId, 'coupons', filter, size] as const,
  allAdminCoupons: (userId: number) => ['admin', userId, 'coupons'] as const,
  adminBanners: (userId: number) => ['admin', userId, 'banners'] as const,
  adminNotifications: (userId: number, filter: AdminNotificationFilter, size: number) => ['admin', userId, 'notifications', filter, size] as const,
  adminDeliveries: (userId: number, filter: { status?: DeliveryStatus; partnerUserId?: number; page: number }, size: number) =>
    ['admin', userId, 'deliveries', 'list', filter, size] as const,
  adminDeliveryCount: (userId: number, status: DeliveryStatus) => ['admin', userId, 'deliveries', 'count', status] as const,
  /** every list and counter of deliveries */
  allAdminDeliveries: (userId: number) => ['admin', userId, 'deliveries'] as const,
  // The super admin's pages (same cache root as the admin panel)
  adminSettings: (userId: number) => ['admin', userId, 'settings'] as const,
  adminFeatureFlags: (userId: number) => ['admin', userId, 'feature-flags'] as const,
  adminCommissions: (userId: number, from: string, to: string) => ['admin', userId, 'commissions', from, to] as const,
  adminAuditLog: (userId: number, filter: AuditLogFilter, size: number) => ['admin', userId, 'audit-log', filter, size] as const,
  /** the up/down of every service and the open circuit breakers (Prometheus) */
  adminSystemHealth: (userId: number) => ['admin', userId, 'system'] as const,
  allAdmin: ['admin'] as const,
  // The shopping assistant: a saved conversation, per login ('guest' or the user id)
  chatConversation: (owner: string, conversationId: string | null) => ['chat', owner, conversationId] as const,
  // The delivery panel, under 'delivery' and the user id (also dropped on every login and logout)
  deliveryProfile: (userId: number) => ['delivery', userId, 'profile'] as const,
  deliveryAvailability: (userId: number) => ['delivery', userId, 'availability'] as const,
  deliveryTasks: (userId: number, active: boolean, page: number, size: number) => ['delivery', userId, 'deliveries', active ? 'active' : 'finished', page, size] as const,
  /** every list of the partner's deliveries */
  allDeliveryTasks: (userId: number) => ['delivery', userId, 'deliveries'] as const,
  deliveryTask: (userId: number, deliveryId: number) => ['delivery', userId, 'delivery', deliveryId] as const,
  /** the partner's delivery on the map (store, customer, last shared position, route) */
  deliveryTracking: (userId: number, deliveryId: number) => ['delivery', userId, 'tracking', deliveryId] as const,
  deliveryEarnings: (userId: number, from: string, to: string) => ['delivery', userId, 'earnings', from, to] as const,
  allDeliveryEarnings: (userId: number) => ['delivery', userId, 'earnings'] as const,
  /** a proof photo, downloaded with the token */
  deliveryProofPhoto: (photoUrl: string | null) => ['delivery', 'proof', photoUrl] as const,
  allDelivery: ['delivery'] as const,
}

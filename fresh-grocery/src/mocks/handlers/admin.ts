import { http, HttpResponse } from 'msw'
import type { CategoryRequest } from '../../api/adminCatalog'
import type { ZoneRequest } from '../../api/adminDelivery'
import type { BannerRequest } from '../../api/adminMarketing'
import type { OrderStatus, ProductStatus } from '../../api/types'
import type { CouponRequest } from '../../api/vendorCoupons'
import { adminGetUser, changeUserStatus, searchUsers } from '../mockAuth'
import { allBanners, createBanner, deleteBanner, updateBanner } from '../mockBanners'
import { allCategories, blockProduct, createCategory, getAnyProduct, searchAllProducts, setCategoryActive, unblockProduct, updateCategory } from '../mockCatalog'
import { createPlatformCoupon, searchAllCoupons, setAnyCouponActive, updatePlatformCoupon } from '../mockCoupons'
import { customerAddresses, searchCustomerProfiles } from '../mockCustomers'
import { searchAllNotifications } from '../mockNotifications'
import { hideReview, searchAllReviews, showReview } from '../mockRatings'
import { approvePartner, assignDelivery, createZone, listZones, rejectPartner, searchDeliveries, searchPartners, updateZone } from '../mockDelivery'
import { mockInvoiceFileName, mockInvoicePdf } from '../mockInvoice'
import { adminCancelOrder, adminGetOrder, adminGetPayment, adminOrderWithInvoice, partnerAssigned, searchAllOrders, searchPayments } from '../mockOrders'
import { dailySales, dashboard, paymentSummary, topProducts, topStores } from '../mockReports'
import { adminStore, approveStore, disableStore, enableStore, rejectStore, searchStores } from '../mockStores'
import { intParam, paginate } from './respond'
import { asAdmin } from './withRole'

/*
 * Mock of the admin panel's endpoints (/api/v1/admin/**, for ADMIN and SUPER_ADMIN): stores, reports, orders,
 * payments, delivery, the catalog (products, categories, reviews), coupons, banners, notifications, accounts and
 * customers. The rules are in the mock service files (mockStores.ts, mockOrders.ts, mockDelivery.ts, mockCatalog.ts ...).
 */

/** An optional whole-number query parameter (an id), or null. */
function idParam(params: URLSearchParams, name: string): number | null {
  return params.get(name) ? Number(params.get(name)) : null
}

export const adminHandlers = [
  // ---------- Stores (store-service) ----------
  http.get(
    '*/api/v1/admin/stores',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const stores = searchStores({ status: params.get('status'), city: params.get('city'), keyword: params.get('keyword') })
      return { data: paginate(stores, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Stores fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/admin/stores/:storeId',
    asAdmin(async (_admin, _request, params) => ({ data: adminStore(Number(params.storeId)), message: 'Store fetched successfully' })),
  ),

  http.post(
    '*/api/v1/admin/stores/:storeId/approve',
    asAdmin(async (_admin, _request, params) => ({ data: approveStore(Number(params.storeId)), message: 'Store approved' })),
  ),

  http.post(
    '*/api/v1/admin/stores/:storeId/reject',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { reason?: unknown }
      return { data: rejectStore(Number(params.storeId), body.reason), message: 'Store rejected' }
    }),
  ),

  http.post(
    '*/api/v1/admin/stores/:storeId/disable',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { reason?: unknown }
      return { data: disableStore(Number(params.storeId), body.reason), message: 'Store disabled' }
    }),
  ),

  http.post(
    '*/api/v1/admin/stores/:storeId/enable',
    asAdmin(async (_admin, _request, params) => ({ data: enableStore(Number(params.storeId)), message: 'Store enabled' })),
  ),

  // ---------- Reports (report-service) ----------
  http.get('*/api/v1/admin/reports/dashboard', asAdmin(async () => ({ data: dashboard(), message: 'Dashboard calculated' }))),

  http.get(
    '*/api/v1/admin/reports/sales/daily',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      return { data: dailySales(idParam(params, 'storeId'), params.get('from'), params.get('to')), message: 'Daily sales calculated' }
    }),
  ),

  http.get(
    '*/api/v1/admin/reports/top-stores',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      return { data: topStores(params.get('from'), params.get('to'), intParam(params, 'limit', 10)), message: 'Top stores calculated' }
    }),
  ),

  http.get(
    '*/api/v1/admin/reports/top-products',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      return { data: topProducts(idParam(params, 'storeId'), params.get('from'), params.get('to'), intParam(params, 'limit', 10)), message: 'Top products calculated' }
    }),
  ),

  // ---------- Orders (order-service) ----------
  http.get(
    '*/api/v1/admin/orders',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const orders = searchAllOrders({ customerUserId: idParam(params, 'customerUserId'), storeId: idParam(params, 'storeId'), status: params.get('status') as OrderStatus | null })
      return { data: paginate(orders, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Orders fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/admin/orders/:orderId',
    asAdmin(async (_admin, _request, params) => ({ data: adminGetOrder(Number(params.orderId)), message: 'Order fetched successfully' })),
  ),

  http.get(
    '*/api/v1/admin/orders/:orderId/invoice',
    asAdmin(async (_admin, _request, params) => {
      const order = adminOrderWithInvoice(Number(params.orderId))
      return new HttpResponse(mockInvoicePdf(order), {
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="${mockInvoiceFileName(order)}"` },
      })
    }),
  ),

  http.post(
    '*/api/v1/admin/orders/:orderId/cancel',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { reason?: unknown }
      return { data: adminCancelOrder(Number(params.orderId), body.reason), message: 'Order cancelled' }
    }),
  ),

  // ---------- Payments (payment-service) ----------
  http.get(
    '*/api/v1/admin/payments',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const payments = searchPayments({ status: params.get('status'), method: params.get('method'), storeId: idParam(params, 'storeId') })
      return { data: paginate(payments, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Payments fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/admin/payments/summary',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      return { data: paymentSummary(params.get('from'), params.get('to')), message: 'Payment summary calculated' }
    }),
  ),

  http.get(
    '*/api/v1/admin/payments/:paymentId',
    asAdmin(async (_admin, _request, params) => ({ data: adminGetPayment(Number(params.paymentId)), message: 'Payment fetched successfully' })),
  ),

  // ---------- Delivery (delivery-service) ----------
  http.get(
    '*/api/v1/admin/delivery-partners',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      return { data: paginate(searchPartners(params.get('kycStatus')), intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Partners fetched successfully' }
    }),
  ),

  http.post(
    '*/api/v1/admin/delivery-partners/:partnerUserId/approve',
    asAdmin(async (_admin, _request, params) => ({ data: approvePartner(Number(params.partnerUserId)), message: 'KYC approved' })),
  ),

  http.post(
    '*/api/v1/admin/delivery-partners/:partnerUserId/reject',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { reason?: unknown }
      return { data: rejectPartner(Number(params.partnerUserId), body.reason), message: 'KYC rejected' }
    }),
  ),

  http.get('*/api/v1/admin/delivery-zones', asAdmin(async () => ({ data: listZones(), message: 'Zones fetched successfully' }))),

  http.post(
    '*/api/v1/admin/delivery-zones',
    asAdmin(async (_admin, request) => {
      const body = (await request.json()) as Partial<ZoneRequest>
      return { data: createZone(body), message: 'Zone created', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/admin/delivery-zones/:zoneId',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as Partial<ZoneRequest>
      return { data: updateZone(Number(params.zoneId), body), message: 'Zone updated' }
    }),
  ),

  http.get(
    '*/api/v1/admin/deliveries',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const deliveries = searchDeliveries(params.get('status'), idParam(params, 'partnerUserId'))
      return { data: paginate(deliveries, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Deliveries fetched successfully' }
    }),
  ),

  // ---------- Catalog (product-read-service, product-service) ----------
  http.get(
    '*/api/v1/admin/products',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const products = searchAllProducts({
        q: params.get('q'),
        storeId: idParam(params, 'storeId'),
        categoryId: idParam(params, 'categoryId'),
        status: params.get('status') as ProductStatus | null,
        lowStockOnly: params.get('lowStockOnly') === 'true',
      })
      return { data: paginate(products, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Products fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/admin/products/:productId',
    asAdmin(async (_admin, _request, params) => ({ data: getAnyProduct(Number(params.productId)), message: 'Product fetched successfully' })),
  ),

  http.post(
    '*/api/v1/admin/products/:productId/block',
    asAdmin(async (_admin, _request, params) => ({ data: blockProduct(Number(params.productId)), message: 'Product blocked' })),
  ),

  http.post(
    '*/api/v1/admin/products/:productId/unblock',
    asAdmin(async (_admin, _request, params) => ({ data: unblockProduct(Number(params.productId)), message: 'Product unblocked' })),
  ),

  http.get('*/api/v1/admin/categories', asAdmin(async () => ({ data: allCategories(), message: 'Categories fetched successfully' }))),

  http.post(
    '*/api/v1/admin/categories',
    asAdmin(async (_admin, request) => {
      const body = (await request.json()) as Partial<CategoryRequest>
      return { data: createCategory(body), message: 'Category created successfully', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/admin/categories/:categoryId',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as Partial<CategoryRequest>
      return { data: updateCategory(Number(params.categoryId), body), message: 'Category updated successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/admin/categories/:categoryId/status',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { active?: unknown }
      return { data: setCategoryActive(Number(params.categoryId), body.active), message: 'Category status updated successfully' }
    }),
  ),

  http.get(
    '*/api/v1/admin/reviews',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const reviews = searchAllReviews({ storeId: idParam(params, 'storeId'), productId: idParam(params, 'productId'), rating: idParam(params, 'rating'), status: params.get('status') })
      return { data: paginate(reviews, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Reviews fetched successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/admin/reviews/:reviewId/hide',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { reason?: unknown }
      return { data: hideReview(Number(params.reviewId), body.reason), message: 'Review hidden' }
    }),
  ),

  http.patch(
    '*/api/v1/admin/reviews/:reviewId/show',
    asAdmin(async (_admin, _request, params) => ({ data: showReview(Number(params.reviewId)), message: 'Review shown again' })),
  ),

  // ---------- Coupons (cart-service), banners (platform-service), notifications (notification-service) ----------
  http.get(
    '*/api/v1/admin/coupons',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const coupons = searchAllCoupons({ storeId: idParam(params, 'storeId'), platformOnly: params.get('platformOnly') === 'true' })
      return { data: paginate(coupons, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Coupons fetched successfully' }
    }),
  ),

  http.post(
    '*/api/v1/admin/coupons',
    asAdmin(async (_admin, request) => {
      const body = (await request.json()) as Partial<CouponRequest>
      return { data: createPlatformCoupon(body), message: 'Coupon created successfully', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/admin/coupons/:couponId',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as Partial<CouponRequest>
      return { data: updatePlatformCoupon(Number(params.couponId), body), message: 'Coupon updated successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/admin/coupons/:couponId/active',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { active?: unknown }
      return { data: setAnyCouponActive(Number(params.couponId), body.active), message: 'Coupon status updated successfully' }
    }),
  ),

  http.get('*/api/v1/admin/banners', asAdmin(async () => ({ data: allBanners(), message: 'Banners fetched successfully' }))),

  http.post(
    '*/api/v1/admin/banners',
    asAdmin(async (admin, request) => {
      const body = (await request.json()) as Partial<BannerRequest>
      return { data: createBanner(admin.id, body), message: 'Banner created', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/admin/banners/:bannerId',
    asAdmin(async (admin, request, params) => {
      const body = (await request.json()) as Partial<BannerRequest>
      return { data: updateBanner(admin.id, Number(params.bannerId), body), message: 'Banner updated' }
    }),
  ),

  http.delete(
    '*/api/v1/admin/banners/:bannerId',
    asAdmin(async (admin, _request, params) => {
      deleteBanner(admin.id, Number(params.bannerId))
      return { data: null, message: 'Banner deleted' }
    }),
  ),

  http.get(
    '*/api/v1/admin/notifications',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const notifications = searchAllNotifications({ userId: idParam(params, 'userId'), type: params.get('type'), emailStatus: params.get('emailStatus') })
      return { data: paginate(notifications, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Notifications fetched successfully' }
    }),
  ),

  // ---------- Accounts (auth-service) and customers (customer-service) ----------
  http.get(
    '*/api/v1/admin/users',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      const users = searchUsers({ role: params.get('role'), status: params.get('status'), keyword: params.get('keyword') })
      return { data: paginate(users, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Users fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/admin/users/:userId',
    asAdmin(async (_admin, _request, params) => ({ data: adminGetUser(Number(params.userId)), message: 'User fetched successfully' })),
  ),

  http.patch(
    '*/api/v1/admin/users/:userId/status',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { status?: unknown }
      return { data: changeUserStatus(Number(params.userId), body.status), message: 'User status updated' }
    }),
  ),

  http.get(
    '*/api/v1/admin/customers',
    asAdmin(async (_admin, request) => {
      const params = new URL(request.url).searchParams
      return { data: paginate(searchCustomerProfiles(params.get('keyword')), intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Customers fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/admin/customers/:customerId/addresses',
    asAdmin(async (_admin, _request, params) => ({ data: customerAddresses(Number(params.customerId)), message: 'Addresses fetched successfully' })),
  ),

  http.post(
    '*/api/v1/admin/deliveries/:deliveryId/assign',
    asAdmin(async (_admin, request, params) => {
      const body = (await request.json()) as { partnerUserId?: unknown }
      const { delivery, partner } = assignDelivery(Number(params.deliveryId), body.partnerUserId)
      partnerAssigned(delivery.orderId, partner)
      return { data: delivery, message: 'Delivery assigned' }
    }),
  ),
]

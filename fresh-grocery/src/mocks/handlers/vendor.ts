import { http, HttpResponse } from 'msw'
import type { OrderStatus, ProductStatus } from '../../api/types'
import type { CouponRequest } from '../../api/vendorCoupons'
import type { ProductRequest } from '../../api/vendorProducts'
import type { OpeningHoursRequest, StoreDetailsRequest } from '../../api/vendorStore'
import {
  addImage,
  addStock,
  createProduct,
  deleteProduct,
  getStock,
  getStoreProduct,
  listStoreProducts,
  removeImage,
  setProductStatus,
  setStock,
  updateProduct,
} from '../mockCatalog'
import { MockApiError } from '../mockApiError'
import { createCoupon, listStoreCoupons, setCouponActive, updateCoupon } from '../mockCoupons'
import { mockInvoiceFileName, mockInvoicePdf } from '../mockInvoice'
import { dailySales, storeReviews, topProducts } from '../mockReports'
import { acceptOrder, getStoreOrder, listStoreOrders, markReadyForPickup, packOrder, rejectOrder, storeOrderWithInvoice } from '../mockOrders'
import { storeOrderStream } from '../mockOrderStream'
import { registerStore, setAcceptingOrders, storeOfOwner, updateDeliveryAreas, updateOpeningHours, updateStore } from '../mockStores'
import { asVendor, storeIdOf } from './withRole'
import { intParam, paginate } from './respond'

/*
 * Mock of the vendor panel's endpoints (/api/v1/vendor/**): the store (store-service, by the vendor's user id), and by
 * the store id in the token its orders (order-service), products (product-service, product-read-service) and stock
 * (inventory-service). The rules are in mockStores.ts, mockOrders.ts and mockCatalog.ts.
 */

/** The uploaded file as a data URL, so the mock can show it (the real backend stores it and returns a /media/ URL). */
async function toDataUrl(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return `data:${file.type};base64,${btoa(binary)}`
}

export const vendorHandlers = [
  // ---------- My store ----------
  http.get(
    '*/api/v1/vendor/store',
    asVendor(async (vendor) => ({ data: storeOfOwner(vendor.id), message: 'Store fetched successfully' })),
  ),

  http.post(
    '*/api/v1/vendor/store',
    asVendor(async (vendor, request) => {
      const body = (await request.json()) as Partial<StoreDetailsRequest>
      return { data: registerStore(vendor.id, body), message: 'Store registered. It is now waiting for admin approval.', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/vendor/store',
    asVendor(async (vendor, request) => {
      const body = (await request.json()) as Partial<StoreDetailsRequest>
      return { data: updateStore(vendor.id, body), message: 'Store updated successfully' }
    }),
  ),

  http.put(
    '*/api/v1/vendor/store/opening-hours',
    asVendor(async (vendor, request) => {
      const body = (await request.json()) as { days?: Partial<OpeningHoursRequest>[] }
      return { data: updateOpeningHours(vendor.id, body.days), message: 'Opening hours updated successfully' }
    }),
  ),

  http.put(
    '*/api/v1/vendor/store/delivery-areas',
    asVendor(async (vendor, request) => {
      const body = (await request.json()) as { pincodes?: unknown }
      return { data: updateDeliveryAreas(vendor.id, body.pincodes), message: 'Delivery areas updated successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/vendor/store/accepting-orders',
    asVendor(async (vendor, request) => {
      const body = (await request.json()) as { acceptingOrders?: unknown }
      return { data: setAcceptingOrders(vendor.id, body.acceptingOrders), message: 'Store order intake updated successfully' }
    }),
  ),

  // ---------- My store's orders ----------
  http.get(
    '*/api/v1/vendor/orders',
    asVendor(async (vendor, request) => {
      const params = new URL(request.url).searchParams
      const status = (params.get('status') as OrderStatus | null) ?? null
      const page = paginate(listStoreOrders(storeIdOf(vendor), status), intParam(params, 'page', 0), intParam(params, 'size', 20))
      return { data: page, message: 'Orders fetched successfully' }
    }),
  ),

  // The live stream of the store's orders (Server-Sent Events). Before '/:orderId', which would take "stream" as an id
  http.get(
    '*/api/v1/vendor/orders/stream',
    asVendor(async (vendor) => new Response(storeOrderStream(storeIdOf(vendor)), { headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' } })),
  ),

  http.get(
    '*/api/v1/vendor/orders/:orderId',
    asVendor(async (vendor, _request, params) => ({ data: getStoreOrder(storeIdOf(vendor), Number(params.orderId)), message: 'Order fetched successfully' })),
  ),

  http.get(
    '*/api/v1/vendor/orders/:orderId/invoice',
    asVendor(async (vendor, _request, params) => {
      const order = storeOrderWithInvoice(storeIdOf(vendor), Number(params.orderId))
      return new HttpResponse(mockInvoicePdf(order), {
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="${mockInvoiceFileName(order)}"` },
      })
    }),
  ),

  http.post(
    '*/api/v1/vendor/orders/:orderId/accept',
    asVendor(async (vendor, _request, params) => ({ data: acceptOrder(storeIdOf(vendor), Number(params.orderId)), message: 'Order accepted' })),
  ),

  http.post(
    '*/api/v1/vendor/orders/:orderId/reject',
    asVendor(async (vendor, request, params) => {
      const body = (await request.json()) as { reason?: string }
      return { data: rejectOrder(storeIdOf(vendor), Number(params.orderId), body.reason), message: 'Order rejected' }
    }),
  ),

  http.post(
    '*/api/v1/vendor/orders/:orderId/pack',
    asVendor(async (vendor, _request, params) => ({ data: packOrder(storeIdOf(vendor), Number(params.orderId)), message: 'Order packed' })),
  ),

  http.post(
    '*/api/v1/vendor/orders/:orderId/ready-for-pickup',
    asVendor(async (vendor, _request, params) => ({ data: markReadyForPickup(storeIdOf(vendor), Number(params.orderId)), message: 'Order is ready for pickup' })),
  ),

  // ---------- My products (reading: product-read-service, writing: product-service) ----------
  http.get(
    '*/api/v1/vendor/products',
    asVendor(async (vendor, request) => {
      const params = new URL(request.url).searchParams
      const products = listStoreProducts(storeIdOf(vendor), {
        q: params.get('q'),
        status: (params.get('status') as ProductStatus | null) ?? null,
        categoryId: params.get('categoryId') ? Number(params.get('categoryId')) : null,
        lowStockOnly: params.get('lowStockOnly') === 'true',
      })
      return { data: paginate(products, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Products fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/vendor/products/:productId',
    asVendor(async (vendor, _request, params) => ({ data: getStoreProduct(storeIdOf(vendor), Number(params.productId)), message: 'Product fetched successfully' })),
  ),

  http.post(
    '*/api/v1/vendor/products',
    asVendor(async (vendor, request) => {
      const body = (await request.json()) as Partial<ProductRequest>
      return { data: createProduct(storeIdOf(vendor), body), message: 'Product created successfully', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/vendor/products/:productId',
    asVendor(async (vendor, request, params) => {
      const body = (await request.json()) as Partial<ProductRequest>
      return { data: updateProduct(storeIdOf(vendor), Number(params.productId), body), message: 'Product updated successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/vendor/products/:productId/status',
    asVendor(async (vendor, request, params) => {
      const body = (await request.json()) as { status?: unknown }
      return { data: setProductStatus(storeIdOf(vendor), Number(params.productId), body.status), message: 'Product status updated successfully' }
    }),
  ),

  http.delete(
    '*/api/v1/vendor/products/:productId',
    asVendor(async (vendor, _request, params) => {
      deleteProduct(storeIdOf(vendor), Number(params.productId))
      return { data: null, message: 'Product deleted successfully' }
    }),
  ),

  http.post(
    '*/api/v1/vendor/products/:productId/images',
    asVendor(async (vendor, request, params) => {
      const image = (await request.formData()).get('image')
      if (!(image instanceof File)) throw new MockApiError(400, "Required part 'image' is not present.")
      const saved = addImage(storeIdOf(vendor), Number(params.productId), { type: image.type, size: image.size, url: await toDataUrl(image) })
      return { data: saved, message: 'Image uploaded successfully', status: 201 }
    }),
  ),

  http.delete(
    '*/api/v1/vendor/products/:productId/images',
    asVendor(async (vendor, request, params) => {
      const imageUrl = new URL(request.url).searchParams.get('imageUrl') ?? ''
      return { data: removeImage(storeIdOf(vendor), Number(params.productId), imageUrl), message: 'Image removed successfully' }
    }),
  ),

  // ---------- Stock (inventory-service) ----------
  http.get(
    '*/api/v1/vendor/inventory/:productId',
    asVendor(async (vendor, _request, params) => ({ data: getStock(storeIdOf(vendor), Number(params.productId)), message: 'Stock fetched successfully' })),
  ),

  http.put(
    '*/api/v1/vendor/inventory/:productId',
    asVendor(async (vendor, request, params) => {
      const body = (await request.json()) as { quantityOnHand?: unknown; lowStockThreshold?: unknown }
      return { data: setStock(storeIdOf(vendor), Number(params.productId), body), message: 'Stock saved successfully' }
    }),
  ),

  http.post(
    '*/api/v1/vendor/inventory/:productId/additions',
    asVendor(async (vendor, request, params) => {
      const body = (await request.json()) as { quantity?: unknown }
      return { data: addStock(storeIdOf(vendor), Number(params.productId), body), message: 'Stock added successfully' }
    }),
  ),

  // ---------- Coupons (cart-service) ----------
  http.get(
    '*/api/v1/vendor/coupons',
    asVendor(async (vendor, request) => {
      const params = new URL(request.url).searchParams
      return { data: paginate(listStoreCoupons(storeIdOf(vendor)), intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Coupons fetched successfully' }
    }),
  ),

  http.post(
    '*/api/v1/vendor/coupons',
    asVendor(async (vendor, request) => {
      const body = (await request.json()) as Partial<CouponRequest>
      return { data: createCoupon(storeIdOf(vendor), body), message: 'Coupon created successfully', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/vendor/coupons/:couponId',
    asVendor(async (vendor, request, params) => {
      const body = (await request.json()) as Partial<CouponRequest>
      return { data: updateCoupon(storeIdOf(vendor), Number(params.couponId), body), message: 'Coupon updated successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/vendor/coupons/:couponId/active',
    asVendor(async (vendor, request, params) => {
      const body = (await request.json()) as { active?: unknown }
      return { data: setCouponActive(storeIdOf(vendor), Number(params.couponId), body.active), message: 'Coupon status updated successfully' }
    }),
  ),

  // ---------- Reports (report-service) and reviews (product-service) ----------
  http.get(
    '*/api/v1/vendor/reports/sales/daily',
    asVendor(async (vendor, request) => {
      const params = new URL(request.url).searchParams
      return { data: dailySales(storeIdOf(vendor), params.get('from'), params.get('to')), message: 'Daily sales calculated' }
    }),
  ),

  http.get(
    '*/api/v1/vendor/reports/top-products',
    asVendor(async (vendor, request) => {
      const params = new URL(request.url).searchParams
      return { data: topProducts(storeIdOf(vendor), params.get('from'), params.get('to'), intParam(params, 'limit', 10)), message: 'Top products calculated' }
    }),
  ),

  http.get(
    '*/api/v1/vendor/reviews',
    asVendor(async (vendor, request) => {
      const params = new URL(request.url).searchParams
      const productId = params.get('productId') ? Number(params.get('productId')) : null
      const rating = params.get('rating') ? Number(params.get('rating')) : null
      const reviews = storeReviews(storeIdOf(vendor), productId, rating)
      return { data: paginate(reviews, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Reviews fetched successfully' }
    }),
  ),
]

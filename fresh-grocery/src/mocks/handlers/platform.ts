import { delay, http } from 'msw'
import { liveBanners } from '../mockBanners'
import { publicCoupons } from '../mockCoupons'
import { currentSettings, publicFlags } from '../mockPlatform'
import { publicStores } from '../mockStores'
import { fail, intParam, ok, paginate } from './respond'

/* Mocks of store-service (store list), platform-service (settings, feature flags, banners) and cart-service (public coupons). */
export const platformHandlers = [
  http.get('*/api/v1/stores', async ({ request }) => {
    await delay()
    const params = new URL(request.url).searchParams
    const stores = publicStores().sort((a, b) => a.name.localeCompare(b.name))
    return ok(paginate(stores, intParam(params, 'page', 0), intParam(params, 'size', 20)), 'Stores fetched successfully')
  }),

  // One approved store (a store that is not active is "not found" too)
  http.get('*/api/v1/stores/:storeId', async ({ request, params }) => {
    await delay()
    const store = publicStores().find((candidate) => candidate.id === Number(params.storeId))
    if (!store) return fail(404, `Store not found with id: ${params.storeId}`, request)
    return ok(store, 'Store fetched successfully')
  }),

  http.get('*/api/v1/platform/settings', async () => {
    await delay()
    return ok(currentSettings(), 'Settings fetched successfully')
  }),

  http.get('*/api/v1/platform/feature-flags', async () => {
    await delay()
    return ok(publicFlags(), 'Feature flags fetched successfully')
  }),

  http.get('*/api/v1/banners', async () => {
    await delay()
    return ok(liveBanners(), 'Banners fetched successfully')
  }),

  // Without a storeId only the platform coupons; with one also that store's own coupons
  http.get('*/api/v1/coupons', async ({ request }) => {
    await delay()
    const storeId = new URL(request.url).searchParams.get('storeId')
    const coupons = publicCoupons(storeId ? Number(storeId) : null)
    return ok(coupons, 'Coupons fetched successfully')
  }),
]

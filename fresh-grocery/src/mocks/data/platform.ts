import type { Banner, Coupon, PlatformSettings } from '../../api/types'

/* Platform settings, banners and coupons, copied from the backend's local seed (platform-service, cart-service). */

export const demoSettings: PlatformSettings = {
  currency: 'INR',
  deliveryFee: 30,
  freeDeliveryFrom: 299,
  minimumOrderValue: 99,
  supportEmail: 'support@grocery.com',
  supportPhone: '1800-123-4567',
  updatedAt: '2026-01-01T00:00:00Z',
}

/** Only the live banners: the seed's third banner (Diwali sale) has ended, so the API does not return it. */
export const demoBanners: Banner[] = [
  {
    id: 1,
    title: 'Fresh vegetables, delivered today',
    subtitle: 'Order before 6 pm',
    imageUrl: 'http://localhost:8080/media/banners/vegetables.jpg',
    linkUrl: '/categories/1',
    position: 1,
    startsAt: null,
    endsAt: null,
    active: true,
    live: true,
  },
  {
    id: 2,
    title: 'WELCOME50: 50 off your first order',
    subtitle: 'On orders of 299 or more',
    imageUrl: 'http://localhost:8080/media/banners/welcome50.jpg',
    linkUrl: '/offers',
    position: 2,
    startsAt: null,
    endsAt: null,
    active: true,
    live: true,
  },
]

/** A coupon with the fields the cart checks but customers never see. */
export interface DemoCoupon extends Coupon {
  active: boolean
  validFrom: string
}

/**
 * The coupons customers can see. The seed has two more that are never public:
 * SUMMER25 (ended) and MANGO15 (switched off) - see allDemoCoupons.
 */
export const demoCoupons: Coupon[] = [
  { code: 'WELCOME50', description: 'Flat 50 off your first order of 299 or more', storeId: null, discountType: 'FLAT', discountValue: 50, maxDiscountAmount: null, minOrderAmount: 299, validUntil: '2027-12-31T23:59:59Z' },
  { code: 'SAVE10', description: '10% off (up to 100) on orders of 199 or more', storeId: null, discountType: 'PERCENTAGE', discountValue: 10, maxDiscountAmount: 100, minOrderAmount: 199, validUntil: '2027-12-31T23:59:59Z' },
  { code: 'FRESH20', description: 'Fresh Mart: 20% off (up to 60) on 249 or more', storeId: 1, discountType: 'PERCENTAGE', discountValue: 20, maxDiscountAmount: 60, minOrderAmount: 249, validUntil: '2027-12-31T23:59:59Z' },
  { code: 'GREEN30', description: 'Green Basket: flat 30 off on 199 or more', storeId: 2, discountType: 'FLAT', discountValue: 30, maxDiscountAmount: null, minOrderAmount: 199, validUntil: '2027-12-31T23:59:59Z' },
]

/** The coupons the cart checks: the public ones (all active, valid since 2026-01-01) plus the two hidden ones. */
export const allDemoCoupons: DemoCoupon[] = [
  ...demoCoupons.map((coupon) => ({ ...coupon, active: true, validFrom: '2026-01-01T00:00:00Z' })),
  { code: 'SUMMER25', description: 'Summer sale: 25% off (ended)', storeId: null, discountType: 'PERCENTAGE', discountValue: 25, maxDiscountAmount: 150, minOrderAmount: 0, validUntil: '2026-06-30T23:59:59Z', active: true, validFrom: '2026-04-01T00:00:00Z' },
  { code: 'MANGO15', description: 'Fresh Mart: 15% off mangoes (switched off)', storeId: 1, discountType: 'PERCENTAGE', discountValue: 15, maxDiscountAmount: null, minOrderAmount: 0, validUntil: '2027-12-31T23:59:59Z', active: false, validFrom: '2026-01-01T00:00:00Z' },
]

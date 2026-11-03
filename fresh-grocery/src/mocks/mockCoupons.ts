import type { CouponRequest } from '../api/vendorCoupons'
import type { ApiErrorDetail, Coupon, CouponDetails } from '../api/types'
import { allDemoCoupons } from './data/platform'
import { MockApiError, validationFailed } from './mockApiError'

/*
 * The coupons for the mock API, kept in memory: editable copies of the seed coupons (src/mocks/data/platform.ts).
 * The carts, the shop's coupon list and the vendor panel all read these, so a vendor's new coupon works at once.
 * The rules copy cart-service (CouponRequest, Coupon.checkSettings, codes unique on the whole platform, in capitals).
 */

function seedCoupons(): CouponDetails[] {
  return allDemoCoupons.map((coupon, index) => ({
    ...coupon,
    id: index + 1,
    description: coupon.description ?? '',
    usageLimit: null,
    usedCount: 0,
    perCustomerLimit: 1,
    createdAt: coupon.validFrom,
    updatedAt: coupon.validFrom,
  }))
}

let coupons = seedCoupons()
let nextCouponId = 100

/** For tests: back to the seed coupons. */
export function resetMockCoupons(): void {
  coupons = seedCoupons()
  nextCouponId = 100
}

/** The coupon with this code (any letter case), for the cart's checks. */
export function couponByCode(code: string): CouponDetails | undefined {
  return coupons.find((coupon) => coupon.code === code.toUpperCase())
}

/** What customers see (PublicCouponResponse): active and valid now; the platform's, plus one store's when given. */
export function publicCoupons(storeId: number | null): Coupon[] {
  const now = Date.now()
  return coupons
    .filter((coupon) => coupon.active && Date.parse(coupon.validFrom) <= now && now <= Date.parse(coupon.validUntil))
    .filter((coupon) => coupon.storeId === null || coupon.storeId === storeId)
    .map(({ code, description, storeId: couponStore, discountType, discountValue, maxDiscountAmount, minOrderAmount, validUntil }) => ({
      code,
      description,
      storeId: couponStore,
      discountType,
      discountValue,
      maxDiscountAmount,
      minOrderAmount,
      validUntil,
    }))
}

// ---------- The vendor's coupons ----------

export function listStoreCoupons(storeId: number): CouponDetails[] {
  return coupons.filter((coupon) => coupon.storeId === storeId).sort((a, b) => Date.parse(b.validUntil) - Date.parse(a.validUntil))
}

function storeCoupon(storeId: number, couponId: number): CouponDetails {
  const coupon = coupons.find((candidate) => candidate.id === couponId && candidate.storeId === storeId)
  if (!coupon) throw new MockApiError(404, `Coupon not found with id: ${couponId}`)
  return coupon
}

/** The @Valid rules of CouponRequest, then Coupon.checkSettings and the unique code. */
function checkCouponRequest(body: Partial<CouponRequest>, couponId: number | null): void {
  const errors: ApiErrorDetail[] = []
  if (!/^[A-Za-z0-9]{4,20}$/.test(body.code?.trim() ?? '')) errors.push({ field: 'code', message: 'must be 4-20 letters or digits' })
  if (!body.description?.trim()) errors.push({ field: 'description', message: 'must not be blank' })
  else if (body.description.length > 255) errors.push({ field: 'description', message: 'size must be between 0 and 255' })
  if (!body.discountType) errors.push({ field: 'discountType', message: 'must not be null' })
  if (typeof body.discountValue !== 'number' || body.discountValue < 0.01) errors.push({ field: 'discountValue', message: 'must be greater than or equal to 0.01' })
  if (body.maxDiscountAmount !== null && body.maxDiscountAmount !== undefined && body.maxDiscountAmount < 0.01) {
    errors.push({ field: 'maxDiscountAmount', message: 'must be greater than or equal to 0.01' })
  }
  if (typeof body.minOrderAmount !== 'number' || body.minOrderAmount < 0) errors.push({ field: 'minOrderAmount', message: 'must be greater than or equal to 0.00' })
  if (!body.validFrom) errors.push({ field: 'validFrom', message: 'must not be null' })
  if (!body.validUntil) errors.push({ field: 'validUntil', message: 'must not be null' })
  if (body.usageLimit !== null && body.usageLimit !== undefined && body.usageLimit < 1) errors.push({ field: 'usageLimit', message: 'must be greater than or equal to 1' })
  const perCustomer = body.perCustomerLimit ?? 1
  if (perCustomer < 1 || perCustomer > 100) errors.push({ field: 'perCustomerLimit', message: 'must be less than or equal to 100' })
  if (errors.length > 0) throw validationFailed(errors)

  if (body.discountType === 'PERCENTAGE' && body.discountValue! > 100) throw new MockApiError(400, 'A percentage discount cannot be more than 100')
  if (body.discountType === 'FLAT' && body.maxDiscountAmount !== null && body.maxDiscountAmount !== undefined) {
    throw new MockApiError(400, 'maxDiscountAmount is only used for PERCENTAGE coupons')
  }
  if (!(Date.parse(body.validUntil!) > Date.parse(body.validFrom!))) throw new MockApiError(400, 'validUntil must be after validFrom')
  const code = body.code!.trim().toUpperCase()
  if (coupons.some((coupon) => coupon.code === code && coupon.id !== couponId)) throw new MockApiError(409, `Coupon code ${code} is already taken. Choose another code.`)
}

/** The coupon fields of a request (code in capitals, like CouponMapper). */
function couponFields(body: CouponRequest) {
  return {
    code: body.code.trim().toUpperCase(),
    description: body.description.trim(),
    discountType: body.discountType,
    discountValue: body.discountValue,
    maxDiscountAmount: body.maxDiscountAmount ?? null,
    minOrderAmount: body.minOrderAmount,
    validFrom: body.validFrom,
    validUntil: body.validUntil,
    usageLimit: body.usageLimit ?? null,
    perCustomerLimit: body.perCustomerLimit ?? 1,
  }
}

export function createCoupon(storeId: number, body: Partial<CouponRequest>): CouponDetails {
  checkCouponRequest(body, null)
  const now = new Date().toISOString()
  const coupon: CouponDetails = { id: nextCouponId++, storeId, ...couponFields(body as CouponRequest), usedCount: 0, active: true, createdAt: now, updatedAt: now }
  coupons.push(coupon)
  return coupon
}

export function updateCoupon(storeId: number, couponId: number, body: Partial<CouponRequest>): CouponDetails {
  const coupon = storeCoupon(storeId, couponId)
  checkCouponRequest(body, couponId)
  Object.assign(coupon, couponFields(body as CouponRequest), { updatedAt: new Date().toISOString() })
  return coupon
}

export function setCouponActive(storeId: number, couponId: number, active: unknown): CouponDetails {
  if (typeof active !== 'boolean') throw validationFailed([{ field: 'active', message: 'must not be null' }])
  const coupon = storeCoupon(storeId, couponId)
  coupon.active = active
  coupon.updatedAt = new Date().toISOString()
  return coupon
}

// ---------- The admin's coupons (AdminCouponServiceImpl) ----------

/** All coupons, one store's, or the platform's only; newest first. */
export function searchAllCoupons(filter: { storeId: number | null; platformOnly: boolean }): CouponDetails[] {
  return coupons
    .filter((coupon) => (filter.platformOnly ? coupon.storeId === null : filter.storeId === null || coupon.storeId === filter.storeId))
    .sort((a, b) => b.id - a.id)
}

function anyCoupon(couponId: number): CouponDetails {
  const coupon = coupons.find((candidate) => candidate.id === couponId)
  if (!coupon) throw new MockApiError(404, `Coupon not found with id: ${couponId}`)
  return coupon
}

/** A platform coupon (storeId null): valid in every store. */
export function createPlatformCoupon(body: Partial<CouponRequest>): CouponDetails {
  checkCouponRequest(body, null)
  const now = new Date().toISOString()
  const coupon: CouponDetails = { id: nextCouponId++, storeId: null, ...couponFields(body as CouponRequest), usedCount: 0, active: true, createdAt: now, updatedAt: now }
  coupons.push(coupon)
  return coupon
}

export function updatePlatformCoupon(couponId: number, body: Partial<CouponRequest>): CouponDetails {
  const coupon = anyCoupon(couponId)
  if (coupon.storeId !== null) throw new MockApiError(400, 'Store coupons are managed by their vendor. Admins can only activate or deactivate them.')
  checkCouponRequest(body, couponId)
  Object.assign(coupon, couponFields(body as CouponRequest), { updatedAt: new Date().toISOString() })
  return coupon
}

/** Any coupon, also a store's, switched on or off. */
export function setAnyCouponActive(couponId: number, active: unknown): CouponDetails {
  if (typeof active !== 'boolean') throw validationFailed([{ field: 'active', message: 'must not be null' }])
  const coupon = anyCoupon(couponId)
  coupon.active = active
  coupon.updatedAt = new Date().toISOString()
  return coupon
}

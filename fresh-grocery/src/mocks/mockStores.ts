import type { OpeningHoursRequest, StoreDetailsRequest } from '../api/vendorStore'
import type { ApiErrorDetail, DayOfWeek, OpeningHours, Store, StoreDetails } from '../api/types'
import { demoDeliveryPincodes, demoStores } from './data/stores'
import { MockApiError, validationFailed } from './mockApiError'
import { linkStoreToVendor } from './mockAuth'
import { notify } from './mockNotifications'

/*
 * The stores for the mock API, kept in memory: editable copies of the two seed stores (vendor1 owns Fresh Mart,
 * vendor2 Green Basket) and stores registered while the mock runs (PENDING_APPROVAL until an admin approves them).
 * The shop reads the same records, so a vendor's changes (paused orders, PIN codes) reach the customers too.
 * The rules copy store-service (VendorStoreServiceImpl, OpeningHoursValidator, AdminStoreServiceImpl).
 */

const WEEK: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

/** The owners of the seed stores (auth-service seed users). */
const SEED_OWNERS: Record<number, number> = { 1: 3, 2: 6 }
const SEED_EMAILS: Record<number, string> = { 1: 'freshmart@example.com', 2: 'greenbasket@example.com' }

function seedStores(): StoreDetails[] {
  return demoStores.map((store) => ({
    ...store,
    openingHours: store.openingHours.map((day) => ({ ...day })),
    ownerUserId: SEED_OWNERS[store.id],
    email: SEED_EMAILS[store.id],
    status: 'ACTIVE',
    statusReason: null,
    commissionPercentage: 10,
    deliveryPincodes: [...demoDeliveryPincodes[store.id]].sort(),
    approvedAt: '2026-01-02T00:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
  }))
}

let stores = seedStores()
let nextStoreId = 100

/** For tests: back to the two seed stores. */
export function resetMockStores(): void {
  stores = seedStores()
  nextStoreId = 100
}

/** What customers see of the ACTIVE stores (PublicStoreResponse). */
export function publicStores(): Store[] {
  return stores
    .filter((store) => store.status === 'ACTIVE')
    .map(({ ownerUserId: _owner, email: _email, status: _status, statusReason: _reason, commissionPercentage: _commission, deliveryPincodes: _pincodes, approvedAt: _approved, createdAt: _created, ...store }) => store)
}

export function storeById(storeId: number): StoreDetails | undefined {
  return stores.find((store) => store.id === storeId)
}

export function deliversTo(storeId: number, pincode: string): boolean {
  return storeById(storeId)?.deliveryPincodes.includes(pincode) ?? false
}

/** The vendor's own store: 404 when they have not registered one. */
export function storeOfOwner(ownerUserId: number): StoreDetails {
  const store = stores.find((candidate) => candidate.ownerUserId === ownerUserId)
  if (!store) throw new MockApiError(404, 'You have not registered a store yet')
  return store
}

/** The @Valid rules of StoreDetailsRequest. */
function checkDetails(body: Partial<StoreDetailsRequest>): void {
  const errors: ApiErrorDetail[] = []
  const required = (field: keyof StoreDetailsRequest, max: number) => {
    const value = body[field]
    if (typeof value !== 'string' || !value.trim()) errors.push({ field, message: 'must not be blank' })
    else if (value.length > max) errors.push({ field, message: `size must be between 0 and ${max}` })
  }
  required('name', 100)
  if (body.description && body.description.length > 500) errors.push({ field: 'description', message: 'size must be between 0 and 500' })
  if (!/^[6-9]\d{9}$/.test(body.phone ?? '')) errors.push({ field: 'phone', message: 'must be a valid 10-digit Indian mobile number' })
  if (body.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)) errors.push({ field: 'email', message: 'must be a well-formed email address' })
  required('addressLine1', 150)
  required('city', 60)
  required('state', 60)
  if (!/^[1-9]\d{5}$/.test(body.pincode ?? '')) errors.push({ field: 'pincode', message: 'must be a valid 6-digit PIN code' })
  if (errors.length > 0) throw validationFailed(errors)
}

function copyDetails(body: StoreDetailsRequest, store: StoreDetails): void {
  Object.assign(store, {
    name: body.name.trim(),
    description: body.description?.trim() || null,
    phone: body.phone,
    email: body.email?.trim() || null,
    addressLine1: body.addressLine1.trim(),
    addressLine2: body.addressLine2?.trim() || null,
    city: body.city.trim(),
    state: body.state.trim(),
    pincode: body.pincode,
  })
}

export function registerStore(ownerUserId: number, body: Partial<StoreDetailsRequest>): StoreDetails {
  checkDetails(body)
  if (stores.some((store) => store.ownerUserId === ownerUserId)) throw new MockApiError(409, 'You already have a store. A vendor can own only one store.')
  const store: StoreDetails = {
    id: nextStoreId++,
    ownerUserId,
    name: '',
    description: null,
    phone: '',
    email: null,
    addressLine1: '',
    addressLine2: null,
    city: '',
    state: '',
    pincode: '',
    latitude: null,
    longitude: null,
    status: 'PENDING_APPROVAL',
    statusReason: null,
    commissionPercentage: 10,
    acceptingOrders: true,
    openNow: true,
    // A new store opens 08:00-21:00 every day until the vendor sets its hours
    openingHours: WEEK.map((dayOfWeek) => ({ dayOfWeek, opensAt: '08:00:00', closesAt: '21:00:00', closed: false })),
    deliveryPincodes: [body.pincode!],
    approvedAt: null,
    createdAt: new Date().toISOString(),
  }
  copyDetails(body as StoreDetailsRequest, store)
  stores.push(store)
  return store
}

/** A REJECTED store goes back for approval after the change. */
export function updateStore(ownerUserId: number, body: Partial<StoreDetailsRequest>): StoreDetails {
  checkDetails(body)
  const store = storeOfOwner(ownerUserId)
  copyDetails(body as StoreDetailsRequest, store)
  if (store.status === 'REJECTED') {
    store.status = 'PENDING_APPROVAL'
    store.statusReason = null
  }
  return store
}

/** OpeningHoursValidator + "days that are not listed are closed". Times arrive as "07:00" and are stored as "07:00:00". */
export function updateOpeningHours(ownerUserId: number, days: Partial<OpeningHoursRequest>[] | undefined): StoreDetails {
  if (!days || days.length === 0) throw validationFailed([{ field: 'days', message: 'must not be empty' }])
  const seen = new Set<string>()
  for (const day of days) {
    if (!day.dayOfWeek) throw validationFailed([{ field: 'days[].dayOfWeek', message: 'must not be null' }])
    if (seen.has(day.dayOfWeek)) throw new MockApiError(400, `${day.dayOfWeek} appears more than once`)
    seen.add(day.dayOfWeek)
    if (day.closed) continue
    if (!day.opensAt || !day.closesAt) throw new MockApiError(400, `${day.dayOfWeek}: opensAt and closesAt are required when the store is open`)
    if (day.opensAt >= day.closesAt) throw new MockApiError(400, `${day.dayOfWeek}: opensAt must be before closesAt`)
  }
  const store = storeOfOwner(ownerUserId)
  const withSeconds = (time: string) => (time.length === 5 ? `${time}:00` : time)
  store.openingHours = WEEK.map((dayOfWeek): OpeningHours => {
    const day = days.find((candidate) => candidate.dayOfWeek === dayOfWeek)
    if (!day || day.closed) return { dayOfWeek, opensAt: null, closesAt: null, closed: true }
    return { dayOfWeek, opensAt: withSeconds(day.opensAt!), closesAt: withSeconds(day.closesAt!), closed: false }
  })
  return store
}

export function updateDeliveryAreas(ownerUserId: number, pincodes: unknown): StoreDetails {
  if (!Array.isArray(pincodes) || pincodes.length === 0) throw validationFailed([{ field: 'pincodes', message: 'must not be empty' }])
  if (pincodes.length > 50) throw validationFailed([{ field: 'pincodes', message: 'size must be between 0 and 50' }])
  if (pincodes.some((pincode) => typeof pincode !== 'string' || !/^[1-9]\d{5}$/.test(pincode))) {
    throw validationFailed([{ field: 'pincodes', message: 'must be a valid 6-digit PIN code' }])
  }
  const store = storeOfOwner(ownerUserId)
  store.deliveryPincodes = [...new Set(pincodes as string[])].sort()
  return store
}

export function setAcceptingOrders(ownerUserId: number, acceptingOrders: unknown): StoreDetails {
  if (typeof acceptingOrders !== 'boolean') throw validationFailed([{ field: 'acceptingOrders', message: 'must not be null' }])
  const store = storeOfOwner(ownerUserId)
  store.acceptingOrders = acceptingOrders
  return store
}

// ----- Admin (AdminStoreServiceImpl) -----

/** Stores by status, city (exact, any case) and part of the name, oldest first. */
export function searchStores(filter: { status: string | null; city: string | null; keyword: string | null }): StoreDetails[] {
  const city = filter.city?.trim().toLowerCase()
  const keyword = filter.keyword?.trim().toLowerCase()
  return stores
    .filter((store) => !filter.status || store.status === filter.status)
    .filter((store) => !city || store.city.toLowerCase() === city)
    .filter((store) => !keyword || store.name.toLowerCase().includes(keyword))
    .sort((a, b) => a.id - b.id)
}

export function adminStore(storeId: number): StoreDetails {
  const store = storeById(storeId)
  if (!store) throw new MockApiError(404, `Store not found with id: ${storeId}`)
  return store
}

function requireStatus(store: StoreDetails, expected: StoreDetails['status'], action: string): void {
  if (store.status !== expected) throw new MockApiError(400, `Only a ${expected} store can be ${action} (current status: ${store.status})`)
}

function requireReason(reason: unknown): string {
  if (typeof reason !== 'string' || !reason.trim()) throw validationFailed([{ field: 'reason', message: 'must not be blank' }])
  if (reason.length > 255) throw validationFailed([{ field: 'reason', message: 'size must be between 0 and 255' }])
  return reason
}

/** StoreStatusChanged: the vendor is told the new status and the reason (notification-service). */
function tellVendor(store: StoreDetails): StoreDetails {
  const reason = store.statusReason ? ` Reason: ${store.statusReason}` : ''
  notify(store.ownerUserId, 'STORE_STATUS_CHANGED', `${store.name} is now ${store.status}`, `The status of your store changed to ${store.status}.${reason}`, store.id)
  return store
}

/** StoreApproved: auth-service links the store to the vendor (in the next login token), and the vendor is told. */
export function approveStore(storeId: number): StoreDetails {
  const store = adminStore(storeId)
  requireStatus(store, 'PENDING_APPROVAL', 'approved')
  store.status = 'ACTIVE'
  store.statusReason = null
  store.approvedAt = new Date().toISOString()
  linkStoreToVendor(store.ownerUserId, store.id)
  notify(store.ownerUserId, 'STORE_APPROVED', `${store.name} is approved`, `Open your vendor panel and add your products and stock. Customers can now find ${store.name}.`, store.id)
  return store
}

export function rejectStore(storeId: number, reason: unknown): StoreDetails {
  const store = adminStore(storeId)
  const why = requireReason(reason)
  requireStatus(store, 'PENDING_APPROVAL', 'rejected')
  store.status = 'REJECTED'
  store.statusReason = why
  return tellVendor(store)
}

export function disableStore(storeId: number, reason: unknown): StoreDetails {
  const store = adminStore(storeId)
  const why = requireReason(reason)
  requireStatus(store, 'ACTIVE', 'disabled')
  store.status = 'DISABLED'
  store.statusReason = why
  return tellVendor(store)
}

export function enableStore(storeId: number): StoreDetails {
  const store = adminStore(storeId)
  requireStatus(store, 'DISABLED', 'enabled')
  store.status = 'ACTIVE'
  store.statusReason = null
  return tellVendor(store)
}

/** The super admin sets the platform commission (0 to 50 %, two decimals); it counts for the orders placed from now on. */
export function setCommission(storeId: number, percentage: unknown): StoreDetails {
  const field = 'commissionPercentage'
  if (typeof percentage !== 'number') throw validationFailed([{ field, message: 'must not be null' }])
  if (percentage < 0) throw validationFailed([{ field, message: 'must be greater than or equal to 0.00' }])
  if (percentage > 50) throw validationFailed([{ field, message: 'must be less than or equal to 50.00' }])
  if (Math.round(percentage * 100) !== percentage * 100) throw validationFailed([{ field, message: 'numeric value out of bounds (<2 digits>.<2 digits> expected)' }])
  const store = adminStore(storeId)
  store.commissionPercentage = percentage
  return store
}

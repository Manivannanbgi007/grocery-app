import type { ZoneRequest } from '../api/adminDelivery'
import type { KycRequest } from '../api/delivery'
import type { ApiErrorDetail, Availability, Delivery, DeliveryIssue, DeliveryPartner, DeliveryStatus, DeliveryZone, Earnings, GeoPoint, Order, VehicleType } from '../api/types'
import { daysBetween, indiaDay } from '../utils/dates'
import { demoAddresses } from './data/addresses'
import { demoStores } from './data/stores'
import { MockApiError, validationFailed } from './mockApiError'
import { storeById } from './mockStores'
import { trackingChanged } from './mockTracking'

/*
 * Delivery for the mock API (delivery-service), kept in memory: the two seed partners (both approved, offline), the two
 * seed zones, and the delivery jobs. A job is made when a store marks an order "ready for pickup" (mockOrders calls
 * createDelivery); an online, free partner of the store's zone gets it at once, otherwise it waits (an admin can
 * assign it, or the next partner who goes online gets it). Order 901 of customer1 was delivered by Arjun Singh.
 *
 * The functions here only change deliveries. What an assignment or a step means for the order (the customer sees the
 * partner, "on the way", "delivered") is done by the caller with mockOrders.ts: they return what was assigned.
 */

/** DeliveryStatus.keepsPartnerBusy: a partner with such a job gets no other one. */
const BUSY: DeliveryStatus[] = ['ASSIGNED', 'ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY', 'FAILED']
const FINISHED: DeliveryStatus[] = ['DELIVERED', 'RETURNED']
const CANCELLABLE: DeliveryStatus[] = ['WAITING_FOR_PARTNER', 'ASSIGNED', 'ACCEPTED']
/** DeliveryStatus.canMoveTo */
const NEXT: Record<DeliveryStatus, DeliveryStatus[]> = {
  WAITING_FOR_PARTNER: ['ASSIGNED', 'CANCELLED'],
  ASSIGNED: ['ACCEPTED', 'WAITING_FOR_PARTNER', 'CANCELLED'],
  ACCEPTED: ['PICKED_UP', 'CANCELLED'],
  PICKED_UP: ['OUT_FOR_DELIVERY', 'FAILED'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
  DELIVERED: [],
  FAILED: ['RETURNED'],
  RETURNED: [],
  CANCELLED: [],
}
const ONLINE_FOR_MS = 30 * 60_000
const MAX_EARNINGS_DAYS = 92
const ISSUES: DeliveryIssue[] = ['CUSTOMER_UNAVAILABLE', 'ADDRESS_WRONG', 'ITEM_DAMAGED', 'OTHER']
const VEHICLES: VehicleType[] = ['BICYCLE', 'SCOOTER', 'MOTORBIKE']

/** A delivery given to a partner: the caller tells the order (mockOrders.partnerAssigned). */
export interface Assignment {
  delivery: Delivery
  partner: DeliveryPartner
}

function seedPartner(userId: number, fullName: string, phone: string, vehicleType: VehicleType, vehicleNumber: string, drivingLicenseNumber: string, zone: DeliveryZone): DeliveryPartner {
  return { userId, fullName, phone, vehicleType, vehicleNumber, drivingLicenseNumber, zoneId: zone.id, zoneName: zone.name, kycStatus: 'APPROVED', kycNote: null, online: false, ratingAverage: null, ratingCount: 0 }
}

function seedZones(): DeliveryZone[] {
  return [
    { id: 1, name: 'Indiranagar', city: 'Bengaluru', pincodes: ['560008', '560038', '560071'], partnerPayout: 40, active: true },
    { id: 2, name: 'Koramangala', city: 'Bengaluru', pincodes: ['560034', '560047', '560095'], partnerPayout: 40, active: true },
  ]
}

function seedPartners(seededZones: DeliveryZone[]): DeliveryPartner[] {
  return [
    seedPartner(5, 'Arjun Singh', '9000000005', 'SCOOTER', 'KA01AB1234', 'KA0120190012345', seededZones[0]),
    seedPartner(8, 'Karthik Reddy', '9000000008', 'MOTORBIKE', 'KA05CD5678', 'KA0520180067890', seededZones[1]),
  ]
}

/** "45, 100 Feet Road, Indiranagar, Opposite CMH Hospital, Bengaluru 560038" */
function oneLine(parts: { addressLine1: string; addressLine2: string | null; city: string; pincode: string }): string {
  const street = [parts.addressLine1, parts.addressLine2].filter((part) => part?.trim()).join(', ')
  return `${street}, ${parts.city} ${parts.pincode}`
}

/** A point for the map, or null when a store or address has no coordinates. */
function pointOf(place: { latitude: number | null; longitude: number | null } | undefined): GeoPoint | null {
  return place && place.latitude !== null && place.longitude !== null ? { latitude: place.latitude, longitude: place.longitude } : null
}

/** Order 901 (₹177, cash), delivered by Arjun three days ago: the same story as the seed order in mockOrders. */
function seedDeliveries(): Delivery[] {
  const store = demoStores[0]
  const home = demoAddresses[4][0]
  const start = Date.now() - 3 * 24 * 60 * 60 * 1000
  const at = (minutes: number) => new Date(start + minutes * 60 * 1000).toISOString()
  return [
    {
      id: 1,
      orderId: 901,
      status: 'DELIVERED',
      storeName: store.name,
      storePhone: store.phone,
      storeAddress: oneLine(store),
      customerName: home.contactName,
      customerPhone: home.contactPhone,
      customerAddress: oneLine(home),
      paymentMethod: 'COD',
      amountToCollect: 177,
      partnerUserId: 5,
      issue: null,
      issueNote: null,
      proofPhotoUrl: null,
      partnerPayout: 40,
      customerRating: null,
      customerComment: null,
      assignedAt: at(26),
      pickedUpAt: at(40),
      deliveredAt: at(65),
      createdAt: at(25),
      storeLocation: pointOf(store),
      customerLocation: pointOf(home),
    },
  ]
}

let zones = seedZones()
let partners = seedPartners(zones)
let deliveries = seedDeliveries()
/** The store of each job (DeliveryResponse does not show it): its zone decides who gets it and what it pays. */
let storeOfDelivery = new Map<number, number>([[1, 1]])
/** Who rejected a job: it is never offered to them again. */
let rejectedBy = new Map<number, Set<number>>()
/** Online partners (user id -> until when, like the Redis keys that expire by themselves). */
let onlineUntil = new Map<number, number>()
let nextZoneId = 3
let nextDeliveryId = 2

/** For tests: back to the seed partners, zones and deliveries, nobody online. */
export function resetMockDelivery(): void {
  zones = seedZones()
  partners = seedPartners(zones)
  deliveries = seedDeliveries()
  storeOfDelivery = new Map([[1, 1]])
  rejectedBy = new Map()
  onlineUntil = new Map()
  nextZoneId = 3
  nextDeliveryId = 2
}

// ---------- Partners ----------

const isOnline = (userId: number) => (onlineUntil.get(userId) ?? 0) > Date.now()

/** A copy of the partner with "online" worked out now (PartnerResponse). */
const withOnline = (partner: DeliveryPartner): DeliveryPartner => ({ ...partner, online: isOnline(partner.userId) })

/** UserRegistered with role DELIVERY_PARTNER: a partner record without KYC. */
export function registerPartner(userId: number, fullName: string, phone: string): DeliveryPartner {
  const partner: DeliveryPartner = { userId, fullName, phone, vehicleType: null, vehicleNumber: null, drivingLicenseNumber: null, zoneId: null, zoneName: null, kycStatus: 'NOT_SUBMITTED', kycNote: null, online: false, ratingAverage: null, ratingCount: 0 }
  partners.push(partner)
  return partner
}

function findPartner(userId: number, notFound = `No delivery partner with user id ${userId}`): DeliveryPartner {
  const partner = partners.find((candidate) => candidate.userId === userId)
  if (!partner) throw new MockApiError(404, notFound)
  return partner
}

/** The logged-in partner's profile (404 right after a registration in the real backend; the mock makes it at once). */
export function partnerProfile(userId: number): DeliveryPartner {
  return withOnline(findPartner(userId, `No delivery partner profile for user ${userId}`))
}

/** The @Valid rules of KycRequest, an active zone, and "already approved". The KYC is then PENDING. */
export function submitKyc(userId: number, body: Partial<KycRequest>): DeliveryPartner {
  const errors: ApiErrorDetail[] = []
  if (!body.vehicleType || !VEHICLES.includes(body.vehicleType)) errors.push({ field: 'vehicleType', message: 'must not be null' })
  if (!/^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}$/.test(body.vehicleNumber ?? '')) errors.push({ field: 'vehicleNumber', message: 'must look like KA01AB1234' })
  const licence = body.drivingLicenseNumber?.trim() ?? ''
  if (licence.length < 5 || licence.length > 30) errors.push({ field: 'drivingLicenseNumber', message: 'size must be between 5 and 30' })
  if (typeof body.zoneId !== 'number' || body.zoneId <= 0) errors.push({ field: 'zoneId', message: 'must not be null' })
  if (errors.length > 0) throw validationFailed(errors)

  const partner = findPartner(userId, `No delivery partner profile for user ${userId}`)
  const zone = zones.find((candidate) => candidate.id === body.zoneId && candidate.active)
  if (!zone) throw new MockApiError(404, `Delivery zone not found with id: ${body.zoneId}`)
  if (partner.kycStatus === 'APPROVED') throw new MockApiError(400, 'Your KYC is already approved. Contact support to change your details.')
  Object.assign(partner, { vehicleType: body.vehicleType, vehicleNumber: body.vehicleNumber, drivingLicenseNumber: licence, zoneId: zone.id, zoneName: zone.name, kycStatus: 'PENDING', kycNote: null })
  return withOnline(partner)
}

/** Partners, oldest first, optionally one KYC status. */
export function searchPartners(kycStatus: string | null): DeliveryPartner[] {
  return partners.filter((partner) => !kycStatus || partner.kycStatus === kycStatus).map(withOnline)
}

function requirePending(partner: DeliveryPartner): void {
  if (partner.kycStatus !== 'PENDING') throw new MockApiError(400, `KYC of partner ${partner.userId} is ${partner.kycStatus}, expected PENDING`)
}

export function approvePartner(userId: number): DeliveryPartner {
  const partner = findPartner(userId)
  requirePending(partner)
  partner.kycStatus = 'APPROVED'
  return withOnline(partner)
}

export function rejectPartner(userId: number, reason: unknown): DeliveryPartner {
  if (typeof reason !== 'string' || !reason.trim()) throw validationFailed([{ field: 'reason', message: 'must not be blank' }])
  if (reason.length > 255) throw validationFailed([{ field: 'reason', message: 'size must be between 0 and 255' }])
  const partner = findPartner(userId)
  requirePending(partner)
  partner.kycStatus = 'REJECTED'
  partner.kycNote = reason.trim()
  return withOnline(partner)
}

export function partnerById(userId: number): DeliveryPartner | undefined {
  const partner = partners.find((candidate) => candidate.userId === userId)
  return partner ? withOnline(partner) : undefined
}

// ---------- Being online ----------

export function availabilityOf(userId: number): Availability {
  return isOnline(userId) ? { online: true, onlineUntil: new Date(onlineUntil.get(userId)!).toISOString() } : { online: false, onlineUntil: null }
}

/** Online for 30 minutes (only approved partners); a job waiting in the partner's zone is offered at once. */
export function goOnline(userId: number): { availability: Availability; assignment: Assignment | null } {
  const partner = findPartner(userId, `No delivery partner profile for user ${userId}`)
  if (partner.kycStatus !== 'APPROVED') throw new MockApiError(400, `Only approved partners can go online. Your KYC is ${partner.kycStatus}`)
  onlineUntil.set(userId, Date.now() + ONLINE_FOR_MS)
  return { availability: availabilityOf(userId), assignment: offerWaitingDeliveryTo(userId) }
}

export function goOffline(userId: number): Availability {
  onlineUntil.delete(userId)
  return availabilityOf(userId)
}

// ---------- Assigning (DeliveryAssignmentService) ----------

/** The zone that covers a store's PIN code: its partners deliver the store's orders. */
function zoneOfStore(storeId: number): DeliveryZone | undefined {
  const storePincode = storeById(storeId)?.pincode
  return zones.find((zone) => storePincode !== undefined && zone.pincodes.includes(storePincode))
}

const isFree = (userId: number) => !deliveries.some((delivery) => delivery.partnerUserId === userId && BUSY.includes(delivery.status))

function assign(delivery: Delivery, partner: DeliveryPartner): Assignment {
  delivery.status = 'ASSIGNED'
  delivery.partnerUserId = partner.userId
  delivery.assignedAt = new Date().toISOString()
  trackingChanged(delivery)
  return { delivery, partner: withOnline(partner) }
}

/** Gives a waiting job to an online, free, approved partner of its zone who did not reject it (or keeps it waiting). */
function tryAssign(delivery: Delivery): Assignment | null {
  const zone = zoneOfStore(storeOfDelivery.get(delivery.id) ?? 0)
  const partner = partners.find(
    (candidate) =>
      candidate.kycStatus === 'APPROVED' &&
      candidate.zoneId === zone?.id &&
      isOnline(candidate.userId) &&
      isFree(candidate.userId) &&
      !rejectedBy.get(delivery.id)?.has(candidate.userId),
  )
  return partner ? assign(delivery, partner) : null
}

/** A partner just became free (online, or finished a job): the oldest waiting job of their zone, if any. */
export function offerWaitingDeliveryTo(userId: number): Assignment | null {
  const partner = findPartner(userId)
  if (!isOnline(userId) || !isFree(userId)) return null
  const waiting = deliveries
    .filter((delivery) => delivery.status === 'WAITING_FOR_PARTNER' && zoneOfStore(storeOfDelivery.get(delivery.id) ?? 0)?.id === partner.zoneId)
    .filter((delivery) => !rejectedBy.get(delivery.id)?.has(userId))
    .sort((a, b) => a.id - b.id)[0]
  return waiting ? assign(waiting, partner) : null
}

// ---------- Deliveries (admin and order side) ----------

/** Deliveries, newest first, optionally one status and/or one partner. */
export function searchDeliveries(status: string | null, partnerUserId: number | null): Delivery[] {
  return deliveries
    .filter((delivery) => !status || delivery.status === status)
    .filter((delivery) => partnerUserId === null || delivery.partnerUserId === partnerUserId)
    .sort((a, b) => b.id - a.id)
}

export function deliveryOfOrder(orderId: number): Delivery | undefined {
  return deliveries.find((delivery) => delivery.orderId === orderId)
}

/**
 * OrderReadyForPickup: a job for the store's zone, given to an online free partner at once when there is one.
 * customerLocation: the coordinates of the order's address (the event carries them; OrderResponse does not).
 */
export function createDelivery(order: Order, customerLocation: GeoPoint | null = null): Assignment | null {
  const delivery: Delivery = {
    id: nextDeliveryId++,
    orderId: order.id,
    status: 'WAITING_FOR_PARTNER',
    storeName: order.storeName,
    storePhone: order.storePhone,
    storeAddress: order.storeAddress,
    customerName: order.deliveryAddress.contactName,
    customerPhone: order.deliveryAddress.contactPhone,
    customerAddress: oneLine(order.deliveryAddress),
    paymentMethod: order.paymentMethod,
    amountToCollect: order.paymentMethod === 'COD' ? order.amounts.totalAmount : 0,
    partnerUserId: null,
    issue: null,
    issueNote: null,
    proofPhotoUrl: null,
    partnerPayout: null,
    customerRating: null,
    customerComment: null,
    assignedAt: null,
    pickedUpAt: null,
    deliveredAt: null,
    createdAt: new Date().toISOString(),
    storeLocation: pointOf(storeById(order.storeId)),
    customerLocation,
  }
  deliveries.push(delivery)
  storeOfDelivery.set(delivery.id, order.storeId)
  return tryAssign(delivery)
}

/** OrderCancelled: a job that was not picked up yet is cancelled. */
export function cancelDeliveryOfOrder(orderId: number): void {
  const delivery = deliveryOfOrder(orderId)
  if (delivery && CANCELLABLE.includes(delivery.status)) {
    delivery.status = 'CANCELLED'
    trackingChanged(delivery)
  }
}

/** AdminDeliveryServiceImpl.assignManually: an approved partner without another job (online or not). */
export function assignDelivery(deliveryId: number, partnerUserId: unknown): Assignment {
  if (typeof partnerUserId !== 'number' || partnerUserId <= 0) throw validationFailed([{ field: 'partnerUserId', message: 'must not be null' }])
  const delivery = deliveries.find((candidate) => candidate.id === deliveryId)
  if (!delivery) throw new MockApiError(404, `Delivery not found with id: ${deliveryId}`)
  const partner = findPartner(partnerUserId)
  if (partner.kycStatus !== 'APPROVED') throw new MockApiError(400, `Partner ${partnerUserId} is not approved`)
  if (!isFree(partnerUserId)) throw new MockApiError(400, `Partner ${partnerUserId} is busy with another delivery`)
  moveTo(delivery, 'ASSIGNED')
  return assign(delivery, partner)
}

// ---------- The partner's deliveries (PartnerDeliveryServiceImpl) ----------

/** Delivery.moveTo with its check: "Delivery 3 is ACCEPTED, so it cannot become DELIVERED". */
function moveTo(delivery: Delivery, next: DeliveryStatus): void {
  if (!NEXT[delivery.status].includes(next)) throw new MockApiError(400, `Delivery ${delivery.id} is ${delivery.status}, so it cannot become ${next}`)
  delivery.status = next
  trackingChanged(delivery) // the customer's live tracking hears it (DeliveryStatusListener)
}

/** Only the partner's own deliveries; someone else's looks like one that does not exist. */
function myDeliveryEntry(userId: number, deliveryId: number): Delivery {
  const delivery = deliveries.find((candidate) => candidate.id === deliveryId && candidate.partnerUserId === userId)
  if (!delivery) throw new MockApiError(404, `Delivery not found with id: ${deliveryId}`)
  return delivery
}

/** The current tasks (active) or the finished ones (delivered and returned), newest first. */
export function myDeliveries(userId: number, active: boolean): Delivery[] {
  return deliveries.filter((delivery) => delivery.partnerUserId === userId && (active ? BUSY : FINISHED).includes(delivery.status)).sort((a, b) => b.id - a.id)
}

export function myDelivery(userId: number, deliveryId: number): Delivery {
  return myDeliveryEntry(userId, deliveryId)
}

export function acceptDelivery(userId: number, deliveryId: number): Delivery {
  const delivery = myDeliveryEntry(userId, deliveryId)
  moveTo(delivery, 'ACCEPTED')
  return delivery
}

/** The job goes back to waiting, is never offered to this partner again, and goes to the next free partner (if any). */
export function rejectDelivery(userId: number, deliveryId: number): { delivery: Delivery; assignment: Assignment | null } {
  const delivery = myDeliveryEntry(userId, deliveryId)
  moveTo(delivery, 'WAITING_FOR_PARTNER')
  rejectedBy.set(delivery.id, new Set([...(rejectedBy.get(delivery.id) ?? []), userId]))
  delivery.partnerUserId = null
  delivery.assignedAt = null
  return { delivery, assignment: tryAssign(delivery) }
}

export function pickUpDelivery(userId: number, deliveryId: number): Delivery {
  const delivery = myDeliveryEntry(userId, deliveryId)
  moveTo(delivery, 'PICKED_UP')
  delivery.pickedUpAt = new Date().toISOString()
  return delivery
}

export function startTrip(userId: number, deliveryId: number): Delivery {
  const delivery = myDeliveryEntry(userId, deliveryId)
  moveTo(delivery, 'OUT_FOR_DELIVERY')
  return delivery
}

/** Delivered: the partner earns the zone's payout, and may get the next waiting job at once. */
export function deliverDelivery(userId: number, deliveryId: number): { delivery: Delivery; next: Assignment | null } {
  const delivery = myDeliveryEntry(userId, deliveryId)
  moveTo(delivery, 'DELIVERED')
  delivery.partnerPayout = zoneOfStore(storeOfDelivery.get(delivery.id) ?? 0)?.partnerPayout ?? 0
  delivery.deliveredAt = new Date().toISOString()
  return { delivery, next: offerWaitingDeliveryTo(userId) }
}

/** The @Valid rules of IssueRequest; the delivery FAILS and the partner takes the items back. */
export function reportDeliveryIssue(userId: number, deliveryId: number, body: { issue?: unknown; note?: unknown }): Delivery {
  if (typeof body.issue !== 'string' || !ISSUES.includes(body.issue as DeliveryIssue)) throw validationFailed([{ field: 'issue', message: 'must not be null' }])
  const note = typeof body.note === 'string' && body.note.trim() ? body.note.trim() : null
  if (note && note.length > 255) throw validationFailed([{ field: 'note', message: 'size must be between 0 and 255' }])
  const delivery = myDeliveryEntry(userId, deliveryId)
  moveTo(delivery, 'FAILED')
  delivery.issue = body.issue as DeliveryIssue
  delivery.issueNote = note
  return delivery
}

/** After a failed delivery the items are back at the store; the partner is free for the next job. */
export function returnDelivery(userId: number, deliveryId: number): { delivery: Delivery; next: Assignment | null } {
  const delivery = myDeliveryEntry(userId, deliveryId)
  moveTo(delivery, 'RETURNED')
  return { delivery, next: offerWaitingDeliveryTo(userId) }
}

/** Delivery.attachProof: only on the way or after delivering. */
export function attachProof(userId: number, deliveryId: number, photoUrl: string): Delivery {
  const delivery = myDeliveryEntry(userId, deliveryId)
  if (delivery.status !== 'OUT_FOR_DELIVERY' && delivery.status !== 'DELIVERED') throw new MockApiError(400, 'A proof of delivery can only be added on the way or after delivering')
  delivery.proofPhotoUrl = photoUrl
  return delivery
}

/** What the partner earned from `from` to `to` (India days); only days with deliveries are listed. */
export function earningsOf(userId: number, from: string | null, to: string | null): Earnings {
  if (!from || !to) throw new MockApiError(400, "Required parameter 'from' or 'to' is not present.")
  if (to < from || daysBetween(from, to).length > MAX_EARNINGS_DAYS + 1) throw new MockApiError(400, `Choose a period of 1 to ${MAX_EARNINGS_DAYS} days (from <= to)`)
  const delivered = deliveries.filter((delivery) => delivery.partnerUserId === userId && delivery.status === 'DELIVERED' && delivery.deliveredAt !== null)
  const inPeriod = delivered.filter((delivery) => {
    const day = indiaDay(delivery.deliveredAt!)
    return day >= from && day <= to
  })
  const round = (amount: number) => Math.round(amount * 100) / 100
  const days = [...new Set(inPeriod.map((delivery) => indiaDay(delivery.deliveredAt!)))].sort().map((date) => {
    const ofDay = inPeriod.filter((delivery) => indiaDay(delivery.deliveredAt!) === date)
    return { date, deliveries: ofDay.length, amount: round(ofDay.reduce((sum, delivery) => sum + (delivery.partnerPayout ?? 0), 0)) }
  })
  return { from, to, deliveries: inPeriod.length, total: round(inPeriod.reduce((sum, delivery) => sum + (delivery.partnerPayout ?? 0), 0)), days }
}

// ---------- Zones ----------

export function listZones(): DeliveryZone[] {
  return [...zones].sort((a, b) => a.id - b.id)
}

/** The @Valid rules of ZoneRequest. */
function checkZone(body: Partial<ZoneRequest>): ZoneRequest {
  const errors: ApiErrorDetail[] = []
  if (!body.name?.trim()) errors.push({ field: 'name', message: 'must not be blank' })
  else if (body.name.length > 100) errors.push({ field: 'name', message: 'size must be between 0 and 100' })
  if (!body.city?.trim()) errors.push({ field: 'city', message: 'must not be blank' })
  if (!Array.isArray(body.pincodes) || body.pincodes.length === 0) errors.push({ field: 'pincodes', message: 'must not be empty' })
  else if (body.pincodes.length > 50) errors.push({ field: 'pincodes', message: 'size must be between 0 and 50' })
  else if (body.pincodes.some((pincode) => !/^[1-9]\d{5}$/.test(pincode))) errors.push({ field: 'pincodes[]', message: 'must be a 6-digit PIN code' })
  if (typeof body.partnerPayout !== 'number') errors.push({ field: 'partnerPayout', message: 'must not be null' })
  else if (body.partnerPayout < 0) errors.push({ field: 'partnerPayout', message: 'must be greater than or equal to 0.00' })
  if (errors.length > 0) throw validationFailed(errors)
  return body as ZoneRequest
}

function copyZone(request: ZoneRequest, zone: DeliveryZone): void {
  zone.name = request.name.trim()
  zone.city = request.city.trim()
  zone.pincodes = [...new Set(request.pincodes)].sort()
  zone.partnerPayout = request.partnerPayout
  zone.active = request.active ?? true
  // The partners of the zone show its new name
  for (const partner of partners.filter((candidate) => candidate.zoneId === zone.id)) partner.zoneName = zone.name
}

export function createZone(body: Partial<ZoneRequest>): DeliveryZone {
  const request = checkZone(body)
  if (zones.some((zone) => zone.name === request.name.trim())) throw new MockApiError(409, `A zone named ${request.name.trim()} already exists`)
  const zone: DeliveryZone = { id: nextZoneId++, name: '', city: '', pincodes: [], partnerPayout: 0, active: true }
  copyZone(request, zone)
  zones.push(zone)
  return zone
}

export function updateZone(zoneId: number, body: Partial<ZoneRequest>): DeliveryZone {
  const request = checkZone(body)
  const zone = zones.find((candidate) => candidate.id === zoneId)
  if (!zone) throw new MockApiError(404, `Delivery zone not found with id: ${zoneId}`)
  if (zone.name !== request.name.trim() && zones.some((other) => other.name === request.name.trim())) throw new MockApiError(409, `A zone named ${request.name.trim()} already exists`)
  copyZone(request, zone)
  return zone
}

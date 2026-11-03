import { apiRequest } from './client'
import type { Delivery, DeliveryPartner, DeliveryStatus, DeliveryZone, KycStatus, PageResponse } from './types'

/*
 * Delivery for admins (delivery-service): the partners and their KYC, the delivery zones, and every delivery job.
 * A delivery is created when the store marks an order "ready for pickup"; delivery-service offers it to a free, online
 * partner of the store's zone, and it waits (WAITING_FOR_PARTNER) when there is none: then an admin can assign one.
 */

/** Partners, oldest first; kycStatus=PENDING is the KYC queue. */
export function searchPartners(filter: { kycStatus?: KycStatus; page: number }, size = 20, signal?: AbortSignal): Promise<PageResponse<DeliveryPartner>> {
  return apiRequest<PageResponse<DeliveryPartner>>('/api/v1/admin/delivery-partners', { query: { ...filter, size }, signal })
}

/** PENDING → APPROVED: the partner can go online and get deliveries. */
export function approvePartner(partnerUserId: number): Promise<DeliveryPartner> {
  return apiRequest<DeliveryPartner>(`/api/v1/admin/delivery-partners/${partnerUserId}/approve`, { method: 'POST' })
}

/** PENDING → REJECTED; the partner sees the reason and can submit the KYC again. */
export function rejectPartner(partnerUserId: number, reason: string): Promise<DeliveryPartner> {
  return apiRequest<DeliveryPartner>(`/api/v1/admin/delivery-partners/${partnerUserId}/reject`, { method: 'POST', body: { reason } })
}

/** ZoneRequest: pincodes are 6-digit PIN codes (1 to 50); partnerPayout is in rupees per delivery. */
export interface ZoneRequest {
  name: string
  city: string
  pincodes: string[]
  partnerPayout: number
  active: boolean
}

export function getZones(signal?: AbortSignal): Promise<DeliveryZone[]> {
  return apiRequest<DeliveryZone[]>('/api/v1/admin/delivery-zones', { signal })
}

export function createZone(request: ZoneRequest): Promise<DeliveryZone> {
  return apiRequest<DeliveryZone>('/api/v1/admin/delivery-zones', { method: 'POST', body: request })
}

export function updateZone(zoneId: number, request: ZoneRequest): Promise<DeliveryZone> {
  return apiRequest<DeliveryZone>(`/api/v1/admin/delivery-zones/${zoneId}`, { method: 'PUT', body: request })
}

/** Deliveries, newest first; status=WAITING_FOR_PARTNER finds the ones nobody took. */
export function searchDeliveries(
  filter: { status?: DeliveryStatus; partnerUserId?: number; page: number },
  size = 20,
  signal?: AbortSignal,
): Promise<PageResponse<Delivery>> {
  return apiRequest<PageResponse<Delivery>>('/api/v1/admin/deliveries', { query: { ...filter, size }, signal })
}

/** Gives a waiting delivery to an approved partner who has no other delivery (the backend says so otherwise). */
export function assignDelivery(deliveryId: number, partnerUserId: number): Promise<Delivery> {
  return apiRequest<Delivery>(`/api/v1/admin/deliveries/${deliveryId}/assign`, { method: 'POST', body: { partnerUserId } })
}

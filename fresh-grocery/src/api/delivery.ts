import { apiDownload, apiRequest, type DownloadedFile } from './client'
import type { Availability, Delivery, DeliveryIssue, DeliveryPartner, Earnings, PageResponse, VehicleType } from './types'

/*
 * The logged-in delivery partner (delivery-service, /api/v1/delivery/**): profile and KYC, being online, the partner's
 * deliveries and their steps, the proof photo, and the earnings.
 */

export function getMyPartnerProfile(signal?: AbortSignal): Promise<DeliveryPartner> {
  return apiRequest<DeliveryPartner>('/api/v1/delivery/profile', { signal })
}

/** KycRequest: vehicleNumber like "KA01AB1234", drivingLicenseNumber 5 to 30 characters, an active zone. */
export interface KycRequest {
  vehicleType: VehicleType
  vehicleNumber: string
  drivingLicenseNumber: string
  zoneId: number
}

/** Sends (or changes) the KYC: PENDING until an admin reviews it. Refused once it is approved. */
export function submitKyc(request: KycRequest): Promise<DeliveryPartner> {
  return apiRequest<DeliveryPartner>('/api/v1/delivery/profile/kyc', { method: 'PUT', body: request })
}

export function getAvailability(signal?: AbortSignal): Promise<Availability> {
  return apiRequest<Availability>('/api/v1/delivery/availability', { signal })
}

/** Online for 30 minutes (call again to stay online); only for approved partners. A waiting delivery may come at once. */
export function goOnline(): Promise<Availability> {
  return apiRequest<Availability>('/api/v1/delivery/availability/online', { method: 'POST' })
}

/** No new deliveries; the current one stays the partner's. */
export function goOffline(): Promise<Availability> {
  return apiRequest<Availability>('/api/v1/delivery/availability/offline', { method: 'POST' })
}

/** The current tasks (active) or the finished ones (delivered and returned), newest first. */
export function getMyDeliveries(params: { active: boolean; page?: number; size?: number }, signal?: AbortSignal): Promise<PageResponse<Delivery>> {
  return apiRequest<PageResponse<Delivery>>('/api/v1/delivery/deliveries', { query: { ...params }, signal })
}

export function getMyDelivery(deliveryId: number, signal?: AbortSignal): Promise<Delivery> {
  return apiRequest<Delivery>(`/api/v1/delivery/deliveries/${deliveryId}`, { signal })
}

/** The steps without details, as the last part of their address. */
export type DeliveryStep = 'accept' | 'reject' | 'pick-up' | 'start' | 'deliver' | 'return'

export function takeDeliveryStep(deliveryId: number, step: DeliveryStep): Promise<Delivery> {
  return apiRequest<Delivery>(`/api/v1/delivery/deliveries/${deliveryId}/${step}`, { method: 'POST' })
}

/** The delivery cannot be finished: FAILED, then the items go back to the store. note: at most 255 characters. */
export function reportIssue(deliveryId: number, issue: DeliveryIssue, note: string | null): Promise<Delivery> {
  return apiRequest<Delivery>(`/api/v1/delivery/deliveries/${deliveryId}/issue`, { method: 'POST', body: { issue, note } })
}

/** A proof-of-delivery photo (JPEG, PNG or WEBP, at most 5 MB), on the way or after delivering. */
export function uploadProof(deliveryId: number, photo: File): Promise<Delivery> {
  const form = new FormData()
  form.append('photo', photo)
  return apiRequest<Delivery>(`/api/v1/delivery/deliveries/${deliveryId}/proof`, { method: 'POST', body: form })
}

/**
 * The proof photo itself: only logged-in users may load it, so it is fetched with the token (an <img> cannot send it).
 * The backend gives a full gateway address; the app asks its own server for the same path (/delivery-media/...).
 */
export function downloadProofPhoto(photoUrl: string): Promise<DownloadedFile> {
  return apiDownload(new URL(photoUrl, window.location.origin).pathname)
}

/** Earnings from `from` to `to` (India dates, at most 92 days apart). */
export function getEarnings(from: string, to: string, signal?: AbortSignal): Promise<Earnings> {
  return apiRequest<Earnings>('/api/v1/delivery/earnings', { query: { from, to }, signal })
}

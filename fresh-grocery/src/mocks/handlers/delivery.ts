import { http } from 'msw'
import type { KycRequest } from '../../api/delivery'
import { MockApiError } from '../mockApiError'
import {
  acceptDelivery,
  attachProof,
  availabilityOf,
  deliverDelivery,
  earningsOf,
  goOffline,
  goOnline,
  myDeliveries,
  myDelivery,
  partnerProfile,
  pickUpDelivery,
  rejectDelivery,
  reportDeliveryIssue,
  returnDelivery,
  startTrip,
  submitKyc,
  type Assignment,
} from '../mockDelivery'
import { orderDelivered, orderDeliveryFailed, orderPickedUp, partnerAssigned } from '../mockOrders'
import { intParam, paginate } from './respond'
import { asPartner } from './withRole'

/*
 * Mock of the delivery panel's endpoints (/api/v1/delivery/**, delivery-service, for DELIVERY_PARTNER). The deliveries
 * change in mockDelivery.ts; what that means for the order (the customer sees the partner, "on the way", "delivered",
 * "not delivered") happens here with mockOrders.ts, like order-service does from delivery-service's events.
 */

/** DeliveryAssigned: the order and the customer hear who brings it. */
function told(assignment: Assignment | null): void {
  if (assignment) partnerAssigned(assignment.delivery.orderId, assignment.partner)
}

/** The uploaded file as a data URL, so the mock can show it (the real backend stores it under /delivery-media/). */
async function toDataUrl(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return `data:${file.type};base64,${btoa(binary)}`
}

export const deliveryHandlers = [
  // ---------- Profile, KYC, availability, earnings ----------
  http.get(
    '*/api/v1/delivery/profile',
    asPartner(async (partner) => ({ data: partnerProfile(partner.id), message: 'Profile fetched successfully' })),
  ),

  http.put(
    '*/api/v1/delivery/profile/kyc',
    asPartner(async (partner, request) => {
      const body = (await request.json()) as Partial<KycRequest>
      return { data: submitKyc(partner.id, body), message: 'KYC submitted. An admin will review it.' }
    }),
  ),

  http.get('*/api/v1/delivery/availability', asPartner(async (partner) => ({ data: availabilityOf(partner.id), message: 'Availability fetched' }))),

  http.post(
    '*/api/v1/delivery/availability/online',
    asPartner(async (partner) => {
      const { availability, assignment } = goOnline(partner.id)
      told(assignment)
      return { data: availability, message: 'You are online' }
    }),
  ),

  http.post('*/api/v1/delivery/availability/offline', asPartner(async (partner) => ({ data: goOffline(partner.id), message: 'You are offline' }))),

  http.get(
    '*/api/v1/delivery/earnings',
    asPartner(async (partner, request) => {
      const params = new URL(request.url).searchParams
      return { data: earningsOf(partner.id, params.get('from'), params.get('to')), message: 'Earnings calculated' }
    }),
  ),

  // ---------- Deliveries ----------
  http.get(
    '*/api/v1/delivery/deliveries',
    asPartner(async (partner, request) => {
      const params = new URL(request.url).searchParams
      const deliveries = myDeliveries(partner.id, params.get('active') !== 'false')
      return { data: paginate(deliveries, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Deliveries fetched successfully' }
    }),
  ),

  http.get(
    '*/api/v1/delivery/deliveries/:deliveryId',
    asPartner(async (partner, _request, params) => ({ data: myDelivery(partner.id, Number(params.deliveryId)), message: 'Delivery fetched successfully' })),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/accept',
    asPartner(async (partner, _request, params) => ({ data: acceptDelivery(partner.id, Number(params.deliveryId)), message: 'Delivery accepted' })),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/reject',
    asPartner(async (partner, _request, params) => {
      const { delivery, assignment } = rejectDelivery(partner.id, Number(params.deliveryId))
      told(assignment)
      return { data: delivery, message: 'Delivery rejected' }
    }),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/pick-up',
    asPartner(async (partner, _request, params) => {
      const delivery = pickUpDelivery(partner.id, Number(params.deliveryId))
      orderPickedUp(delivery.orderId, partner.fullName)
      return { data: delivery, message: 'Order picked up' }
    }),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/start',
    asPartner(async (partner, _request, params) => ({ data: startTrip(partner.id, Number(params.deliveryId)), message: 'Out for delivery' })),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/deliver',
    asPartner(async (partner, _request, params) => {
      const { delivery, next } = deliverDelivery(partner.id, Number(params.deliveryId))
      orderDelivered(delivery.orderId)
      told(next)
      return { data: delivery, message: 'Delivered' }
    }),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/issue',
    asPartner(async (partner, request, params) => {
      const body = (await request.json()) as { issue?: unknown; note?: unknown }
      const delivery = reportDeliveryIssue(partner.id, Number(params.deliveryId), body)
      orderDeliveryFailed(delivery.orderId, delivery.issue!, delivery.issueNote)
      return { data: delivery, message: 'Issue reported' }
    }),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/return',
    asPartner(async (partner, _request, params) => {
      const { delivery, next } = returnDelivery(partner.id, Number(params.deliveryId))
      told(next)
      return { data: delivery, message: 'Items returned to the store' }
    }),
  ),

  http.post(
    '*/api/v1/delivery/deliveries/:deliveryId/proof',
    asPartner(async (partner, request, params) => {
      const photo = (await request.formData()).get('photo')
      if (!(photo instanceof File)) throw new MockApiError(400, "Required part 'photo' is not present.")
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(photo.type)) throw new MockApiError(400, 'Only JPEG, PNG or WEBP photos are allowed')
      return { data: attachProof(partner.id, Number(params.deliveryId), await toDataUrl(photo)), message: 'Proof of delivery saved' }
    }),
  ),
]

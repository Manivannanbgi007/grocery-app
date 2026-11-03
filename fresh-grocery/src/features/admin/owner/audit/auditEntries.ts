/*
 * How the audit log is read: where an entry comes from (a service's Kafka topic, or platform-service's own changes),
 * which event types each source has, and the event details in a readable form.
 */

export interface AuditSource {
  value: string
  label: string
  eventTypes: string[]
}

/** The topics platform-service records (AuditEventsConsumer) and its own changes (recordChange). */
export const AUDIT_SOURCES: AuditSource[] = [
  {
    value: 'grocery.order.events',
    label: 'Orders',
    eventTypes: ['OrderCreated', 'OrderConfirmed', 'OrderAccepted', 'OrderRejected', 'OrderPacked', 'OrderReadyForPickup', 'OrderOutForDelivery', 'OrderDelivered', 'OrderDeliveryFailed', 'OrderCancelled'],
  },
  { value: 'grocery.payment.events', label: 'Payments', eventTypes: ['PaymentConfirmed', 'PaymentFailed', 'RefundCompleted'] },
  { value: 'grocery.delivery.events', label: 'Deliveries', eventTypes: ['DeliveryAssigned', 'DeliveryPickedUp', 'DeliveryCompleted', 'DeliveryFailed', 'PartnerKycReviewed'] },
  { value: 'grocery.store.events', label: 'Stores', eventTypes: ['StoreRegistered', 'StoreApproved', 'StoreStatusChanged'] },
  { value: 'grocery.inventory.events', label: 'Stock', eventTypes: ['StockReserved', 'StockReleased', 'StockReservationFailed', 'StockChanged', 'LowStockDetected'] },
  { value: 'grocery.user.events', label: 'Accounts', eventTypes: ['UserRegistered', 'PasswordResetRequested'] },
  {
    value: 'platform-service',
    label: 'Platform changes',
    eventTypes: ['PlatformSettingsUpdated', 'FeatureFlagCreated', 'FeatureFlagChanged', 'BannerCreated', 'BannerUpdated', 'BannerDeleted'],
  },
]

/** "grocery.order.events" -> "Orders" (an unknown source as it is). */
export function sourceLabel(source: string): string {
  return AUDIT_SOURCES.find((candidate) => candidate.value === source)?.label ?? source
}

/** The event types to choose from: those of one source, or all of them. */
export function eventTypesOf(source: string): string[] {
  const sources = source ? AUDIT_SOURCES.filter((candidate) => candidate.value === source) : AUDIT_SOURCES
  return sources.flatMap((candidate) => candidate.eventTypes)
}

/** "OrderReadyForPickup" -> "Order ready for pickup" */
export function eventName(eventType: string): string {
  const words = eventType.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/** The details as indented JSON when they are JSON; otherwise as they are (e.g. "SettingsRequest[currency=INR, ...]"). */
export function readableDetails(details: string): string {
  try {
    return JSON.stringify(JSON.parse(details), null, 2)
  } catch {
    return details
  }
}

/** The order an event is about (order, payment and delivery events name it in their details), or null. */
export function orderIdOf(details: string): number | null {
  try {
    const orderId = (JSON.parse(details) as { orderId?: unknown }).orderId
    return typeof orderId === 'number' ? orderId : null
  } catch {
    return null
  }
}

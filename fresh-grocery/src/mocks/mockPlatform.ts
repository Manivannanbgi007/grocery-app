import type { FeatureFlagRequest, SettingsRequest } from '../api/superAdmin'
import type { ApiErrorDetail, AuditEntry, FeatureFlag, PlatformSettings } from '../api/types'
import { demoSettings } from './data/platform'
import { demoUsers } from './data/users'
import { MockApiError, validationFailed } from './mockApiError'

/*
 * Mock of platform-service's super admin side: the settings (the mock cart uses them at once; the real cart-service a
 * moment later), the feature flags and the audit log. The audit log starts with a few events of the seed, and then
 * records what platform-service records: settings, feature flag and banner changes. Events of the other mock services
 * are not added to it.
 */

const THIS_SERVICE = 'platform-service'
const SEEDED = '2026-01-01T00:00:00Z'

let settings: PlatformSettings = { ...demoSettings }
let flags: FeatureFlag[] = seedFlags()
let auditEntries: AuditEntry[] = seedAuditEntries()
let nextAuditId = 1000

function seedFlags(): FeatureFlag[] {
  const flag = (key: string, description: string, enabled: boolean): FeatureFlag => ({ key, description, enabled, updatedByUserId: null, updatedAt: SEEDED })
  return [
    flag('CITY_HYDERABAD', 'Stores and customers in Hyderabad can use the app', false),
    flag('DELIVERY_SLOTS', 'Customers choose a delivery time slot at checkout', false),
    flag('PRODUCT_REVIEWS', 'Customers can review the products they received', true),
    flag('RAZORPAY_PAYMENTS', 'Online payments go through Razorpay instead of mock', false),
  ]
}

/** One event of another service, as the audit consumer stores it (details = the whole event as JSON). */
function eventEntry(id: number, eventType: string, topic: string, aggregateId: number, ids: { userId?: number; storeId?: number }, fields: object, occurredAt: string): AuditEntry {
  const eventId = `00000000-0000-4000-8000-${String(id).padStart(12, '0')}`
  return {
    id,
    eventId,
    eventType,
    source: topic,
    aggregateId: String(aggregateId),
    userId: ids.userId ?? null,
    storeId: ids.storeId ?? null,
    details: JSON.stringify({ eventId, occurredAt: Date.parse(occurredAt), ...fields }),
    occurredAt,
  }
}

/** The seed's registrations and store approvals, oldest first (ids 1, 2, 3 ...). */
function seedAuditEntries(): AuditEntry[] {
  const entries = demoUsers.map((user, index) =>
    eventEntry(index + 1, 'UserRegistered', 'grocery.user.events', user.id, { userId: user.id }, { userId: user.id, fullName: user.fullName, email: user.email, phone: user.phone, role: user.role }, `2026-01-01T00:0${index}:00Z`),
  )
  entries.push(
    eventEntry(entries.length + 1, 'StoreApproved', 'grocery.store.events', 1, { userId: 3, storeId: 1 }, { storeId: 1, ownerUserId: 3, storeName: 'Fresh Mart' }, '2026-01-02T00:00:00Z'),
    eventEntry(entries.length + 2, 'StoreApproved', 'grocery.store.events', 2, { userId: 6, storeId: 2 }, { storeId: 2, ownerUserId: 6, storeName: 'Green Basket' }, '2026-01-02T00:05:00Z'),
  )
  return entries
}

/** platform-service's own change (AuditLogService.recordChange): source "platform-service". */
export function recordChange(eventType: string, aggregateId: string, userId: number, details: string): void {
  auditEntries.push({
    id: nextAuditId++,
    eventId: crypto.randomUUID(),
    eventType,
    source: THIS_SERVICE,
    aggregateId,
    userId,
    storeId: null,
    details,
    occurredAt: new Date().toISOString(),
  })
}

// ---------- Settings ----------

export function currentSettings(): PlatformSettings {
  return settings
}

/** Java's BigDecimal with two decimals, as a record's toString() shows it (30 -> "30.00"). */
function money(value: number): string {
  return value.toFixed(2)
}

function amountErrors(field: string, value: unknown, max?: number): ApiErrorDetail[] {
  if (typeof value !== 'number') return [{ field, message: 'must not be null' }]
  if (value < 0) return [{ field, message: 'must be greater than or equal to 0.00' }]
  if (max !== undefined && value > max) return [{ field, message: `must be less than or equal to ${money(max)}` }]
  if (Math.round(value * 100) !== value * 100) return [{ field, message: 'numeric value out of bounds (<8 digits>.<2 digits> expected)' }]
  return []
}

/** The SettingsRequest rules, then "the minimum order cannot be above the free delivery amount". */
export function updateSettings(userId: number, body: Partial<SettingsRequest>): PlatformSettings {
  const errors: ApiErrorDetail[] = []
  if (!/^[A-Z]{3}$/.test(body.currency ?? '')) errors.push({ field: 'currency', message: 'must be a 3-letter currency code, e.g. INR' })
  errors.push(...amountErrors('deliveryFee', body.deliveryFee, 500), ...amountErrors('freeDeliveryFrom', body.freeDeliveryFrom), ...amountErrors('minimumOrderValue', body.minimumOrderValue))
  if (!body.supportEmail?.trim()) errors.push({ field: 'supportEmail', message: 'must not be blank' })
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.supportEmail)) errors.push({ field: 'supportEmail', message: 'must be a well-formed email address' })
  if (!body.supportPhone?.trim()) errors.push({ field: 'supportPhone', message: 'must not be blank' })
  else if (body.supportPhone.length > 20) errors.push({ field: 'supportPhone', message: 'size must be between 0 and 20' })
  if (errors.length > 0) throw validationFailed(errors)

  const request = body as SettingsRequest
  if (request.minimumOrderValue > request.freeDeliveryFrom) throw new MockApiError(400, 'The minimum order value cannot be higher than the free delivery amount')
  settings = { ...request, supportEmail: request.supportEmail.trim(), supportPhone: request.supportPhone.trim(), updatedAt: new Date().toISOString() }
  recordChange(
    'PlatformSettingsUpdated',
    'settings',
    userId,
    `SettingsRequest[currency=${request.currency}, deliveryFee=${money(request.deliveryFee)}, freeDeliveryFrom=${money(request.freeDeliveryFrom)}, minimumOrderValue=${money(request.minimumOrderValue)}, supportEmail=${request.supportEmail}, supportPhone=${request.supportPhone}]`,
  )
  return settings
}

// ---------- Feature flags ----------

export function allFlags(): FeatureFlag[] {
  return [...flags].sort((a, b) => a.key.localeCompare(b.key))
}

/** The public view: { "DELIVERY_SLOTS": false, ... } */
export function publicFlags(): Record<string, boolean> {
  return Object.fromEntries(allFlags().map((flag) => [flag.key, flag.enabled]))
}

export function createFlag(userId: number, body: Partial<FeatureFlagRequest>): FeatureFlag {
  const errors: ApiErrorDetail[] = []
  if (!body.key?.trim()) errors.push({ field: 'key', message: 'must not be blank' })
  else if (!/^[A-Z][A-Z0-9_]{2,59}$/.test(body.key)) errors.push({ field: 'key', message: 'must be UPPER_SNAKE_CASE, e.g. CITY_PUNE' })
  if (!body.description?.trim()) errors.push({ field: 'description', message: 'must not be blank' })
  else if (body.description.length > 255) errors.push({ field: 'description', message: 'size must be between 0 and 255' })
  if (typeof body.enabled !== 'boolean') errors.push({ field: 'enabled', message: 'must not be null' })
  if (errors.length > 0) throw validationFailed(errors)

  const request = body as FeatureFlagRequest
  if (flags.some((flag) => flag.key === request.key)) throw new MockApiError(409, `A feature flag ${request.key} already exists`)
  const flag: FeatureFlag = { key: request.key, description: request.description.trim(), enabled: request.enabled, updatedByUserId: userId, updatedAt: new Date().toISOString() }
  flags.push(flag)
  recordChange('FeatureFlagCreated', flag.key, userId, `FeatureFlagRequest[key=${request.key}, description=${request.description}, enabled=${request.enabled}]`)
  return flag
}

export function switchFlag(userId: number, key: string, enabled: unknown): FeatureFlag {
  if (typeof enabled !== 'boolean') throw validationFailed([{ field: 'enabled', message: 'must not be null' }])
  const flag = flags.find((candidate) => candidate.key === key)
  if (!flag) throw new MockApiError(404, `Feature flag ${key} does not exist`)
  flag.enabled = enabled
  flag.updatedByUserId = userId
  flag.updatedAt = new Date().toISOString()
  recordChange('FeatureFlagChanged', key, userId, `{"key": "${key}", "enabled": ${enabled}}`)
  return flag
}

// ---------- Audit log ----------

export interface AuditQuery {
  eventType: string | null
  source: string | null
  userId: string | null
  storeId: string | null
  aggregateId: string | null
  from: string | null
  to: string | null
}

/** An ISO instant parameter (2026-10-26T18:30:00Z); a plain date is refused like Spring does. */
function instantParam(name: string, value: string | null): number | null {
  if (value === null) return null
  if (!/^\d{4}-\d{2}-\d{2}T/.test(value) || Number.isNaN(Date.parse(value))) throw new MockApiError(400, `Invalid value '${value}' for parameter '${name}'`)
  return Date.parse(value)
}

/** Every filter is optional and must match exactly; newest first. */
export function searchAuditEntries(query: AuditQuery): AuditEntry[] {
  const from = instantParam('from', query.from)
  const to = instantParam('to', query.to)
  return auditEntries
    .filter((entry) => !query.eventType || entry.eventType === query.eventType)
    .filter((entry) => !query.source || entry.source === query.source)
    .filter((entry) => !query.userId || entry.userId === Number(query.userId))
    .filter((entry) => !query.storeId || entry.storeId === Number(query.storeId))
    .filter((entry) => !query.aggregateId || entry.aggregateId === query.aggregateId)
    .filter((entry) => (from === null || Date.parse(entry.occurredAt) >= from) && (to === null || Date.parse(entry.occurredAt) < to))
    .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt) || b.id - a.id)
}

/** For tests: the seed settings, flags and audit log again. */
export function resetMockPlatform(): void {
  settings = { ...demoSettings }
  flags = seedFlags()
  auditEntries = seedAuditEntries()
  nextAuditId = 1000
}

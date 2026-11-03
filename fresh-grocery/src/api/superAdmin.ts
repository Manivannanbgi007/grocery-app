import { apiRequest } from './client'
import type { AuditEntry, CommissionReport, FeatureFlag, PageResponse, PlatformSettings, StoreDetails, User, UserRole } from './types'

/*
 * The super admin's own endpoints (/api/v1/super-admin/**, only SUPER_ADMIN; an admin gets 403):
 * admins and roles (auth-service), settings, feature flags and the audit log (platform-service), the commission of a
 * store (store-service) and the commission report (report-service).
 */

/** CreateAdminRequest: the new admin logs in with this email and password. */
export interface CreateAdminRequest {
  fullName: string
  email: string
  phone: string
  password: string
}

/** A new ADMIN account, active at once. 409 when the email or the mobile number is registered already. */
export function createAdmin(request: CreateAdminRequest): Promise<User> {
  return apiRequest<User>('/api/v1/super-admin/admins', { method: 'POST', body: request })
}

/**
 * Gives an account another role (never SUPER_ADMIN; the super admin's own role cannot change). It counts from the
 * account's next login. Other services do not hear of it: a customer or partner profile is only made at sign-up.
 */
export function changeRole(userId: number, role: Exclude<UserRole, 'SUPER_ADMIN'>): Promise<User> {
  return apiRequest<User>(`/api/v1/super-admin/users/${userId}/role`, { method: 'PATCH', body: { role } })
}

/** SettingsRequest: everything of PlatformSettings but updatedAt. */
export type SettingsRequest = Omit<PlatformSettings, 'updatedAt'>

export function getSettings(signal?: AbortSignal): Promise<PlatformSettings> {
  return apiRequest<PlatformSettings>('/api/v1/super-admin/settings', { signal })
}

/** cart-service uses the new delivery fee and minimum order a moment later (PlatformSettingsChanged event). */
export function updateSettings(request: SettingsRequest): Promise<PlatformSettings> {
  return apiRequest<PlatformSettings>('/api/v1/super-admin/settings', { method: 'PUT', body: request })
}

/** Every feature flag, by key. */
export function getFeatureFlags(signal?: AbortSignal): Promise<FeatureFlag[]> {
  return apiRequest<FeatureFlag[]>('/api/v1/super-admin/feature-flags', { signal })
}

/** FeatureFlagRequest. key: UPPER_SNAKE_CASE, e.g. CITY_PUNE. 409 when the key exists already. */
export interface FeatureFlagRequest {
  key: string
  description: string
  enabled: boolean
}

export function createFeatureFlag(request: FeatureFlagRequest): Promise<FeatureFlag> {
  return apiRequest<FeatureFlag>('/api/v1/super-admin/feature-flags', { method: 'POST', body: request })
}

export function switchFeatureFlag(key: string, enabled: boolean): Promise<FeatureFlag> {
  return apiRequest<FeatureFlag>(`/api/v1/super-admin/feature-flags/${encodeURIComponent(key)}`, { method: 'PATCH', body: { enabled } })
}

/** The platform commission of a store in percent (0 to 50); it counts for the orders placed from now on. */
export function updateCommission(storeId: number, commissionPercentage: number): Promise<StoreDetails> {
  return apiRequest<StoreDetails>(`/api/v1/super-admin/stores/${storeId}/commission`, { method: 'PATCH', body: { commissionPercentage } })
}

/** Commission per store on the orders delivered from `from` to `to` (dates, at most 366 days). */
export function getCommissionReport(from: string, to: string, signal?: AbortSignal): Promise<CommissionReport> {
  return apiRequest<CommissionReport>('/api/v1/super-admin/reports/commissions', { query: { from, to }, signal })
}

/** The audit log query; every filter is optional. from/to are instants (ISO date-time), `to` not included. */
export interface AuditLogFilter {
  eventType?: string
  source?: string
  userId?: number
  storeId?: number
  aggregateId?: string
  from?: string
  to?: string
  /** 0-based */
  page: number
}

/** Audit entries, newest first. */
export function searchAuditLog(filter: AuditLogFilter, size = 20, signal?: AbortSignal): Promise<PageResponse<AuditEntry>> {
  return apiRequest<PageResponse<AuditEntry>>('/api/v1/super-admin/audit-log', { query: { ...filter, size }, signal })
}

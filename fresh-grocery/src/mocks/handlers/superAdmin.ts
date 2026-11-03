import { http } from 'msw'
import type { CreateAdminRequest, FeatureFlagRequest, SettingsRequest } from '../../api/superAdmin'
import { changeRole, createAdmin } from '../mockAuth'
import { allFlags, createFlag, currentSettings, searchAuditEntries, switchFlag, updateSettings } from '../mockPlatform'
import { commissionReport } from '../mockReports'
import { setCommission } from '../mockStores'
import { intParam, paginate } from './respond'
import { asSuperAdmin } from './withRole'

/*
 * Mocks of /api/v1/super-admin/** (auth-service, platform-service, store-service, report-service), same URLs and
 * JSON as the real backend.
 */
export const superAdminHandlers = [
  http.post(
    '*/api/v1/super-admin/admins',
    asSuperAdmin(async (_superAdmin, request) => {
      const body = (await request.json()) as Partial<CreateAdminRequest>
      return { data: createAdmin(body), message: 'Admin created successfully', status: 201 }
    }),
  ),

  http.patch(
    '*/api/v1/super-admin/users/:userId/role',
    asSuperAdmin(async (_superAdmin, request, params) => {
      const body = (await request.json()) as { role?: unknown }
      return { data: changeRole(Number(params.userId), body.role), message: 'User role updated successfully' }
    }),
  ),

  http.get('*/api/v1/super-admin/settings', asSuperAdmin(async () => ({ data: currentSettings(), message: 'Settings fetched successfully' }))),

  http.put(
    '*/api/v1/super-admin/settings',
    asSuperAdmin(async (superAdmin, request) => {
      const body = (await request.json()) as Partial<SettingsRequest>
      return { data: updateSettings(superAdmin.id, body), message: 'Settings updated' }
    }),
  ),

  http.get('*/api/v1/super-admin/feature-flags', asSuperAdmin(async () => ({ data: allFlags(), message: 'Feature flags fetched successfully' }))),

  http.post(
    '*/api/v1/super-admin/feature-flags',
    asSuperAdmin(async (superAdmin, request) => {
      const body = (await request.json()) as Partial<FeatureFlagRequest>
      return { data: createFlag(superAdmin.id, body), message: 'Feature flag created', status: 201 }
    }),
  ),

  http.patch(
    '*/api/v1/super-admin/feature-flags/:key',
    asSuperAdmin(async (superAdmin, request, params) => {
      const body = (await request.json()) as { enabled?: unknown }
      const flag = switchFlag(superAdmin.id, String(params.key), body.enabled)
      return { data: flag, message: `Feature flag ${flag.enabled ? 'enabled' : 'disabled'}` }
    }),
  ),

  http.get(
    '*/api/v1/super-admin/audit-log',
    asSuperAdmin(async (_superAdmin, request) => {
      const params = new URL(request.url).searchParams
      const entries = searchAuditEntries({
        eventType: params.get('eventType'),
        source: params.get('source'),
        userId: params.get('userId'),
        storeId: params.get('storeId'),
        aggregateId: params.get('aggregateId'),
        from: params.get('from'),
        to: params.get('to'),
      })
      return { data: paginate(entries, intParam(params, 'page', 0), intParam(params, 'size', 20)), message: 'Audit log fetched successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/super-admin/stores/:storeId/commission',
    asSuperAdmin(async (_superAdmin, request, params) => {
      const body = (await request.json()) as { commissionPercentage?: unknown }
      return { data: setCommission(Number(params.storeId), body.commissionPercentage), message: 'Commission updated successfully' }
    }),
  ),

  http.get(
    '*/api/v1/super-admin/reports/commissions',
    asSuperAdmin(async (_superAdmin, request) => {
      const params = new URL(request.url).searchParams
      return { data: commissionReport(params.get('from'), params.get('to')), message: 'Commission report calculated' }
    }),
  ),
]

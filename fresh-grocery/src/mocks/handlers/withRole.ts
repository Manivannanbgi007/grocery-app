import { delay, type HttpResponseResolver } from 'msw'
import type { UserRole } from '../../api/types'
import type { DemoUser } from '../data/users'
import { MockApiError } from '../mockApiError'
import { userFromRequest } from '../mockAuth'
import { fail, ok } from './respond'

/** What an action returns: the response data and message (and 201 for something created). */
export interface ActionResult {
  data: unknown
  message: string
  status?: number
}

/** An action returns the data for the usual JSON answer, or a finished Response (e.g. a PDF file). */
type UserAction = (user: DemoUser, request: Request, params: Record<string, string | readonly string[] | undefined>) => Promise<ActionResult | Response>

/**
 * Wraps a mock endpoint with the gateway's login check for some roles, like the real gateway does
 * (/api/v1/vendor/** for vendors, /api/v1/admin/** for admins and the super admin, the customer APIs for customers):
 * no token -> 401, another role -> 403. A MockApiError thrown by the action becomes the matching error response.
 */
function asRole(roles: UserRole[], action: UserAction): HttpResponseResolver {
  return async ({ request, params }) => {
    await delay()
    const user = userFromRequest(request)
    if (!user) return fail(401, 'Please log in to access this resource', request)
    if (!roles.includes(user.role)) return fail(403, 'You do not have permission to access this resource', request)
    try {
      const result = await action(user, request, params)
      if (result instanceof Response) return result
      return ok(result.data, result.message, result.status)
    } catch (error) {
      if (error instanceof MockApiError) return fail(error.status, error.message, request, error.details)
      throw error
    }
  }
}

export function asCustomer(action: UserAction): HttpResponseResolver {
  return asRole(['CUSTOMER'], action)
}

export function asVendor(action: UserAction): HttpResponseResolver {
  return asRole(['VENDOR'], action)
}

export function asPartner(action: UserAction): HttpResponseResolver {
  return asRole(['DELIVERY_PARTNER'], action)
}

export function asAdmin(action: UserAction): HttpResponseResolver {
  return asRole(['ADMIN', 'SUPER_ADMIN'], action)
}

/** /api/v1/super-admin/**: only the super admin (an admin gets 403). */
export function asSuperAdmin(action: UserAction): HttpResponseResolver {
  return asRole(['SUPER_ADMIN'], action)
}

/**
 * The store id of the vendor's login token (UserContextHolder.getRequiredStoreId): a vendor whose store is not approved
 * yet, or who logged in before the approval, has none.
 */
export function storeIdOf(vendor: DemoUser): number {
  if (vendor.storeId === null) throw new MockApiError(403, 'No store found for this vendor. Register a store and wait for approval, then log in again.')
  return vendor.storeId
}

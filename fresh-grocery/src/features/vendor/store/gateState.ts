import { isApiError } from '../../../api/client'
import type { User } from '../../../api/types'
import type { useMyStore } from '../useVendorStore'

/** Where the vendor stands with the store; only 'ready' opens the panel (see the table in the frontend plan, section 7). */
export type GateState = 'loading' | 'error' | 'register' | 'pending' | 'rejected' | 'disabled' | 'relogin' | 'ready'

export function gateState(query: ReturnType<typeof useMyStore>, vendor: User | null): GateState {
  if (query.isPending) return 'loading'
  if (query.isError) return isApiError(query.error, 404) ? 'register' : 'error'
  switch (query.data.status) {
    case 'PENDING_APPROVAL':
      return 'pending'
    case 'REJECTED':
      return 'rejected'
    case 'DISABLED':
      return 'disabled'
    case 'ACTIVE':
      // The store id is written into the login token at login time: a login from before the approval has none
      return vendor?.storeId ? 'ready' : 'relogin'
  }
}

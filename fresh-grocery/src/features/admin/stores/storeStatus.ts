import type { StoreStatus } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'

export const STORE_STATUS_LABELS: Record<StoreStatus, string> = {
  PENDING_APPROVAL: 'Waiting for approval',
  ACTIVE: 'Active',
  REJECTED: 'Rejected',
  DISABLED: 'Disabled',
}

export const STORE_STATUS_TONES: Record<StoreStatus, BadgeTone> = {
  PENDING_APPROVAL: 'new',
  ACTIVE: 'brand',
  REJECTED: 'hot',
  DISABLED: 'neutral',
}

/** The tabs of the store list: the approval queue first (it is the work), then the other statuses. */
export const STORE_TABS: { key: string; label: string; status?: StoreStatus; empty: string }[] = [
  { key: 'waiting', label: 'Waiting for approval', status: 'PENDING_APPROVAL', empty: 'No stores are waiting for approval.' },
  { key: 'active', label: 'Active', status: 'ACTIVE', empty: 'No active stores.' },
  { key: 'disabled', label: 'Disabled', status: 'DISABLED', empty: 'No disabled stores.' },
  { key: 'rejected', label: 'Rejected', status: 'REJECTED', empty: 'No rejected stores.' },
  { key: 'all', label: 'All', empty: 'No stores yet.' },
]

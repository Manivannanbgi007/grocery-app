import { isApiError } from '../../../api/client'
import type { usePartnerProfile } from '../usePartner'

/** Where the partner stands with the KYC; only 'ready' opens the panel (see the table in the frontend plan, section 9). */
export type PartnerGateState = 'loading' | 'error' | 'setting-up' | 'kyc' | 'pending' | 'rejected' | 'ready'

export function partnerGateState(query: ReturnType<typeof usePartnerProfile>): PartnerGateState {
  // A 404 means delivery-service has not made the profile from the registration yet (it is asked for again)
  if (query.isPending) return query.failureCount > 0 ? 'setting-up' : 'loading'
  if (query.isError) return isApiError(query.error, 404) ? 'setting-up' : 'error'
  switch (query.data.kycStatus) {
    case 'NOT_SUBMITTED':
      return 'kyc'
    case 'PENDING':
      return 'pending'
    case 'REJECTED':
      return 'rejected'
    case 'APPROVED':
      return 'ready'
  }
}

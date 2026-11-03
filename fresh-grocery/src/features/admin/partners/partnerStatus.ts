import type { KycStatus, VehicleType } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'

export const KYC_LABELS: Record<KycStatus, string> = {
  NOT_SUBMITTED: 'KYC not sent',
  PENDING: 'Waiting for review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}

export const KYC_TONES: Record<KycStatus, BadgeTone> = {
  NOT_SUBMITTED: 'neutral',
  PENDING: 'new',
  APPROVED: 'brand',
  REJECTED: 'hot',
}

export const VEHICLE_LABELS: Record<VehicleType, string> = {
  BICYCLE: 'Bicycle',
  SCOOTER: 'Scooter',
  MOTORBIKE: 'Motorbike',
}

/** The tabs of the partner list: the KYC queue first (it is the work), then the other KYC states. */
export const PARTNER_TABS: { key: string; label: string; kycStatus?: KycStatus; empty: string }[] = [
  { key: 'waiting', label: 'Waiting for review', kycStatus: 'PENDING', empty: 'No KYC is waiting for a review.' },
  { key: 'approved', label: 'Approved', kycStatus: 'APPROVED', empty: 'No approved partners yet.' },
  { key: 'rejected', label: 'Rejected', kycStatus: 'REJECTED', empty: 'No rejected partners.' },
  { key: 'not-sent', label: 'KYC not sent', kycStatus: 'NOT_SUBMITTED', empty: 'Every partner has sent their KYC.' },
  { key: 'all', label: 'All', empty: 'No delivery partners yet.' },
]

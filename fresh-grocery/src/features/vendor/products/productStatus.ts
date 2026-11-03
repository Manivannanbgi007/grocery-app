import type { ProductStatus } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'

/** A product's status from the vendor's side: INACTIVE is "hidden" (the vendor's own choice). */
export const PRODUCT_STATUS_LABELS: Record<ProductStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Hidden',
  BLOCKED: 'Blocked',
  DELETED: 'Deleted',
}

export const PRODUCT_STATUS_TONES: Record<ProductStatus, BadgeTone> = {
  ACTIVE: 'brand',
  INACTIVE: 'neutral',
  BLOCKED: 'hot',
  DELETED: 'neutral',
}

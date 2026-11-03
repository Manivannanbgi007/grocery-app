import { Ban, Banknote, CircleCheck, CircleX, Clock, RotateCcw, type LucideIcon } from 'lucide-react'
import type { PaymentStatus } from '../../../api/types'
import type { BadgeTone } from '../../../components/ui/Badge'

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Not settled yet',
  AWAITING_PAYMENT: 'Waiting for payment',
  PAID: 'Paid online',
  FAILED: 'Failed',
  CASH_ON_DELIVERY: 'Cash on delivery',
  COLLECTED: 'Cash collected',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
}

export const PAYMENT_STATUS_TONES: Record<PaymentStatus, BadgeTone> = {
  PENDING: 'neutral',
  AWAITING_PAYMENT: 'new',
  PAID: 'brand',
  FAILED: 'hot',
  CASH_ON_DELIVERY: 'sale',
  COLLECTED: 'brand',
  CANCELLED: 'neutral',
  REFUNDED: 'new',
}

/** An icon per status beside its colour and word: done (check), waiting (clock), failed (cross), money back (arrow). */
export const PAYMENT_STATUS_ICONS: Record<PaymentStatus, LucideIcon> = {
  PENDING: Clock,
  AWAITING_PAYMENT: Clock,
  PAID: CircleCheck,
  FAILED: CircleX,
  CASH_ON_DELIVERY: Banknote,
  COLLECTED: CircleCheck,
  CANCELLED: Ban,
  REFUNDED: RotateCcw,
}

/** The statuses offered in the list's filter, in the order money moves. */
export const PAYMENT_STATUS_FILTERS: PaymentStatus[] = ['AWAITING_PAYMENT', 'PAID', 'CASH_ON_DELIVERY', 'COLLECTED', 'REFUNDED', 'FAILED', 'CANCELLED', 'PENDING']

import {
  BadgeCheck,
  Bell,
  Bike,
  CircleCheck,
  CircleX,
  ClipboardCheck,
  CreditCard,
  IdCard,
  KeyRound,
  Package,
  PackageCheck,
  PartyPopper,
  RotateCcw,
  ShoppingBag,
  Store,
  TriangleAlert,
  Truck,
  type LucideIcon,
} from 'lucide-react'

type Tone = 'good' | 'info' | 'warn' | 'bad' | 'neutral'

/** The icon chip's colours per tone (the title and message always say the same in words). */
export const TONE_CLASSES: Record<Tone, string> = {
  good: 'bg-brand-light text-brand-text',
  info: 'bg-sale-light text-sale-text',
  warn: 'bg-warn-light text-warn-text',
  bad: 'bg-hot/10 text-hot',
  neutral: 'bg-surface text-heading',
}

/** The kinds of notification-service (NotificationType) with their icon; unknown kinds get the bell. */
const LOOKS: Record<string, { icon: LucideIcon; tone: Tone }> = {
  WELCOME: { icon: PartyPopper, tone: 'good' },
  PASSWORD_RESET: { icon: KeyRound, tone: 'neutral' },
  STORE_REGISTERED: { icon: Store, tone: 'info' },
  STORE_APPROVED: { icon: BadgeCheck, tone: 'good' },
  STORE_STATUS_CHANGED: { icon: Store, tone: 'warn' },
  ORDER_CONFIRMED: { icon: CircleCheck, tone: 'good' },
  NEW_ORDER: { icon: ShoppingBag, tone: 'good' },
  ORDER_ACCEPTED: { icon: ClipboardCheck, tone: 'good' },
  ORDER_REJECTED: { icon: CircleX, tone: 'bad' },
  ORDER_PACKED: { icon: Package, tone: 'info' },
  ORDER_OUT_FOR_DELIVERY: { icon: Truck, tone: 'info' },
  ORDER_DELIVERED: { icon: PackageCheck, tone: 'good' },
  ORDER_CANCELLED: { icon: CircleX, tone: 'bad' },
  ORDER_DELIVERY_FAILED: { icon: TriangleAlert, tone: 'bad' },
  DELIVERY_PARTNER_ASSIGNED: { icon: Bike, tone: 'info' },
  NEW_DELIVERY: { icon: Bike, tone: 'good' },
  PARTNER_KYC_REVIEWED: { icon: IdCard, tone: 'info' },
  PAYMENT_FAILED: { icon: CreditCard, tone: 'bad' },
  REFUND_COMPLETED: { icon: RotateCcw, tone: 'good' },
  LOW_STOCK: { icon: TriangleAlert, tone: 'warn' },
}

export function notificationLook(type: string): { icon: LucideIcon; tone: Tone } {
  return LOOKS[type] ?? { icon: Bell, tone: 'neutral' }
}

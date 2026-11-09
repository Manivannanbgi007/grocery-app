import { BadgePercent, Flame, Heart, House, ShoppingBasket, ShoppingCart, Store, type LucideIcon } from 'lucide-react'
import type { Tone } from '../ui/tones'

/** A main page: its name, where it goes and its icon (shown next to the name, hidden from screen readers). */
export interface MainLink {
  label: string
  to: string
  icon: LucideIcon
  /** the colour of the icon's tile in the phone menu (the meanings in components/ui/tones.ts) */
  tone: Tone
}

/** The main pages, shown in the nav bar (big screens) and in the mobile menu. */
export const MAIN_LINKS: readonly MainLink[] = [
  { label: 'Home', to: '/', icon: House, tone: 'teal' },
  { label: 'Shop', to: '/shop', icon: ShoppingBasket, tone: 'green' },
  { label: 'Stores', to: '/stores', icon: Store, tone: 'orange' },
  // "/#deals" and "/#offers" jump to those sections of the home page
  { label: 'Hot Deals', to: '/#deals', icon: Flame, tone: 'pink' },
  { label: 'Offers', to: '/#offers', icon: BadgePercent, tone: 'yellow' },
  { label: 'Wishlist', to: '/wishlist', icon: Heart, tone: 'pink' },
  { label: 'Cart', to: '/cart', icon: ShoppingCart, tone: 'green' },
]

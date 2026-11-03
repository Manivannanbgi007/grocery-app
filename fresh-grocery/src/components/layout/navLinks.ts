import { BadgePercent, Flame, Heart, House, ShoppingBasket, ShoppingCart, Store, type LucideIcon } from 'lucide-react'

/** A main page: its name, where it goes and its icon (shown next to the name, hidden from screen readers). */
export interface MainLink {
  label: string
  to: string
  icon: LucideIcon
}

/** The main pages, shown in the nav bar (big screens) and in the mobile menu. */
export const MAIN_LINKS: readonly MainLink[] = [
  { label: 'Home', to: '/', icon: House },
  { label: 'Shop', to: '/shop', icon: ShoppingBasket },
  { label: 'Stores', to: '/stores', icon: Store },
  // "/#deals" and "/#offers" jump to those sections of the home page
  { label: 'Hot Deals', to: '/#deals', icon: Flame },
  { label: 'Offers', to: '/#offers', icon: BadgePercent },
  { label: 'Wishlist', to: '/wishlist', icon: Heart },
  { label: 'Cart', to: '/cart', icon: ShoppingCart },
]

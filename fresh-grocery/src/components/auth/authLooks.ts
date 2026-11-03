import { Bike, ChartColumn, IndianRupee, Leaf, MapPinned, Navigation, Package, ShieldCheck, ShoppingBag, Wallet, type LucideIcon } from 'lucide-react'
import type { ComponentType } from 'react'
import { DashboardIllustration } from './illustrations/DashboardIllustration'
import { DeliveryRiderIllustration } from './illustrations/DeliveryRiderIllustration'
import { GroceryBasketIllustration } from './illustrations/GroceryBasketIllustration'
import { StorefrontIllustration } from './illustrations/StorefrontIllustration'

/** The look of a login or sign-up screen: the shop's, or one of the panels'. */
export type AuthLook = 'shop' | 'vendor' | 'delivery' | 'admin'

interface AuthLookContent {
  Illustration: ComponentType<{ className?: string }>
  headline: string
  text: string
  /** three short points about the panel, each with an icon */
  points: { icon: LucideIcon; text: string }[]
  /** true for a dark side panel (white text) */
  dark: boolean
  /** Tailwind classes, written out in full (Tailwind only finds complete class names) */
  classes: {
    /** the side panel on big screens */
    panel: string
    /** the small illustration above the form on phones */
    banner: string
    /** the soft blurred circles behind the illustration */
    glow: string
    /** the icon of a point */
    pointIcon: string
  }
}

export const AUTH_LOOKS: Record<AuthLook, AuthLookContent> = {
  shop: {
    Illustration: GroceryBasketIllustration,
    headline: 'Fresh groceries from stores near you',
    text: 'Fruit, vegetables and daily needs from approved local stores, brought to your door.',
    points: [
      { icon: Leaf, text: 'Fresh produce from local stores' },
      { icon: MapPinned, text: 'Follow your delivery live on the map' },
      { icon: Wallet, text: 'Pay online or cash on delivery' },
    ],
    dark: false,
    classes: { panel: 'bg-linear-to-br from-brand-light to-tile-1', banner: 'bg-brand-light', glow: 'bg-brand-soft', pointIcon: 'bg-card text-brand-text' },
  },
  vendor: {
    Illustration: StorefrontIllustration,
    headline: 'Grow your store with Fresh Grocery',
    text: 'Reach customers in your area and run your shop from one panel.',
    points: [
      { icon: ShoppingBag, text: 'Take orders from customers nearby' },
      { icon: Package, text: 'Manage products, stock and coupons' },
      { icon: ChartColumn, text: 'See your sales and best sellers' },
    ],
    dark: false,
    classes: { panel: 'bg-linear-to-br from-new-light to-tile-2', banner: 'bg-new-light', glow: 'bg-new/30', pointIcon: 'bg-card text-warn-text' },
  },
  delivery: {
    Illustration: DeliveryRiderIllustration,
    headline: 'Deliver groceries, earn per delivery',
    text: 'Go online when it suits you, take deliveries near you and get paid for each one.',
    points: [
      { icon: Bike, text: 'Deliveries in your own zone' },
      { icon: Navigation, text: 'The store and the customer on a live map' },
      { icon: IndianRupee, text: 'Your earnings, day by day' },
    ],
    dark: false,
    classes: { panel: 'bg-linear-to-br from-sale-light to-tile-3', banner: 'bg-sale-light', glow: 'bg-sale/30', pointIcon: 'bg-card text-sale-text' },
  },
  admin: {
    Illustration: DashboardIllustration,
    headline: 'Run the Fresh Grocery platform',
    text: 'Everything the platform team needs, in one place.',
    points: [
      { icon: ShieldCheck, text: 'Approve stores and delivery partners' },
      { icon: Package, text: 'Follow orders, deliveries and payments' },
      { icon: ChartColumn, text: 'Reports for the whole platform' },
    ],
    dark: true,
    classes: { panel: 'bg-linear-to-br from-ink to-ink-soft', banner: 'bg-ink', glow: 'bg-brand/25', pointIcon: 'bg-white/10 text-ink-brand' },
  },
}

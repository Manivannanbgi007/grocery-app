import { cn } from '../../utils/cn'

/**
 * The colour families of the app: the customer pages (the order page, the cart, the checkout, My orders …) and the
 * icons of every menu (MenuIcon). Each colour means the same everywhere, so people learn them without thinking:
 *
 *   green  = money, orders and the way to buy       blue   = time and delivery
 *   orange = products and stores                     violet = payment and the account
 *   pink   = savings, coupons and the wishlist       yellow = ratings and things to look at
 *   teal   = the platform itself: dashboards, zones, settings, system health
 *
 * Each family has a light background with a text colour readable on it (both have their own values in the dark theme),
 * and `tile`: a filled gradient with a white icon (a dark one on yellow) whose deep end stays the same in both themes,
 * so the icon is always readable.
 * Tailwind only knows class names written out in full, so every class is spelled out here; never build one from parts.
 */
export const TONES = {
  green: {
    soft: 'bg-brand-light text-brand-text',
    tint: 'from-brand-light',
    bar: 'bg-brand',
    ring: 'ring-brand/25',
    hover: 'hover:bg-brand hover:text-white',
    solid: 'bg-brand text-white shadow-brand/30',
    // A light green corner (ink-brand: the same in both themes), so the tile is as bright as the others on the dark
    // admin menu; the middle, behind the white icon, is the brand green (5.5:1)
    tile: 'bg-linear-to-br from-ink-brand via-brand to-brand-dark text-white shadow-brand/30',
  },
  blue: {
    soft: 'bg-sale-light text-sale-text',
    tint: 'from-sale-light',
    bar: 'bg-sale',
    ring: 'ring-sale/30',
    hover: 'hover:bg-sale-text hover:text-white',
    solid: 'bg-sale text-white shadow-sale/30',
    tile: 'bg-linear-to-br from-sale to-deep-blue text-white shadow-sale/30',
  },
  orange: {
    soft: 'bg-new-light text-warn-text',
    tint: 'from-new-light',
    bar: 'bg-new',
    ring: 'ring-new/30',
    hover: 'hover:bg-warn-text hover:text-white',
    solid: 'bg-new text-white shadow-new/30',
    tile: 'bg-linear-to-br from-new to-deep-orange text-white shadow-new/30',
  },
  violet: {
    soft: 'bg-violet-light text-violet-text',
    tint: 'from-violet-light',
    bar: 'bg-violet',
    ring: 'ring-violet/30',
    hover: 'hover:bg-violet hover:text-white',
    solid: 'bg-violet text-white shadow-violet/30',
    tile: 'bg-linear-to-br from-violet to-deep-violet text-white shadow-violet/30',
  },
  pink: {
    soft: 'bg-hot/10 text-hot-text',
    tint: 'from-hot/10',
    bar: 'bg-hot',
    ring: 'ring-hot/30',
    hover: 'hover:bg-hot hover:text-white',
    solid: 'bg-hot text-white shadow-hot/30',
    tile: 'bg-linear-to-br from-hot to-deep-pink text-white shadow-hot/30',
  },
  yellow: {
    soft: 'bg-warn-light text-warn-text',
    tint: 'from-warn-light',
    bar: 'bg-accent',
    ring: 'ring-accent/40',
    hover: 'hover:bg-accent hover:text-ink',
    // Yellow is light: a dark icon on it (ink stays dark in both themes)
    solid: 'bg-accent text-ink shadow-accent/30',
    tile: 'bg-linear-to-br from-accent to-warn text-ink shadow-accent/30',
  },
  teal: {
    soft: 'bg-teal-light text-teal-text',
    tint: 'from-teal-light',
    bar: 'bg-teal',
    ring: 'ring-teal/30',
    hover: 'hover:bg-teal-text hover:text-white',
    solid: 'bg-teal text-white shadow-teal/30',
    tile: 'bg-linear-to-br from-teal to-deep-teal text-white shadow-teal/30',
  },
}

export type Tone = keyof typeof TONES

/**
 * The classes of a coloured card: rounded, with the card's border and shadow, and a light wash of its colour in the
 * top-left corner. Put a <ToneBar> inside for the coloured line along the top (the card is relative for it).
 *
 * @example <section className={toneCard('blue', 'p-6')}><ToneBar tone="blue" />…</section>
 */
export function toneCard(tone: Tone, className?: string): string {
  return cn('relative overflow-hidden rounded-3xl border border-line bg-card bg-linear-to-br via-card to-card', TONES[tone].tint, className)
}

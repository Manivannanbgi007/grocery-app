import type { BadgeTone } from './Badge'

/**
 * The soft colours of a status per tone (the same tones as Badge, so the pages' tone maps stay): the pill's background,
 * text and ring, and the dot. Used by StatusBadge (the status in a list) and StatusTabs (the dot before a tab's name),
 * so a tab and the rows it shows have the same colour.
 */
export const STATUS_TONES: Record<BadgeTone, { pill: string; dot: string }> = {
  brand: { pill: 'bg-brand-light text-brand-text ring-brand/20', dot: 'bg-brand' },
  hot: { pill: 'bg-hot/10 text-hot-text ring-hot/20', dot: 'bg-hot' },
  sale: { pill: 'bg-sale-light text-sale-text ring-sale/25', dot: 'bg-sale' },
  new: { pill: 'bg-new-light text-warn-text ring-new/25', dot: 'bg-new' },
  neutral: { pill: 'bg-surface text-heading ring-line', dot: 'bg-muted' },
}

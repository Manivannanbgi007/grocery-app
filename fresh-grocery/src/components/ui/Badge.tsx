import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

export type BadgeTone = 'brand' | 'hot' | 'sale' | 'new' | 'neutral'

const TONE_CLASSES: Record<BadgeTone, string> = {
  brand: 'bg-brand text-white',
  hot: 'bg-hot text-white',
  sale: 'bg-sale text-white',
  new: 'bg-new text-white',
  neutral: 'bg-line text-heading',
}

interface BadgeProps {
  tone?: BadgeTone
  className?: string
  children: ReactNode
}

/** A small coloured label, e.g. "-10%", "Hot" or a count. */
export function Badge({ tone = 'brand', className, children }: BadgeProps) {
  return <span className={cn('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold whitespace-nowrap', TONE_CLASSES[tone], className)}>{children}</span>
}

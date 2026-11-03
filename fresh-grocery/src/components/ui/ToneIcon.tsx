import type { LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'
import { TONES, type Tone } from './tones'

interface ToneIconProps {
  icon: LucideIcon
  tone: Tone
  /** a full-colour tile with a white icon (default: a light tile with a coloured icon) */
  solid?: boolean
  className?: string
}

/** A rounded icon tile in one of the colours of tones.ts, e.g. at the start of a card's title. Decoration only. */
export function ToneIcon({ icon: Icon, tone, solid = false, className }: ToneIconProps) {
  return (
    <span aria-hidden="true" className={cn('grid size-9 shrink-0 place-items-center rounded-xl', solid ? cn('shadow-md', TONES[tone].solid) : TONES[tone].soft, className)}>
      <Icon className="size-5" />
    </span>
  )
}

/** The coloured line along the top of a card made with toneCard() (tones.ts). Decoration only. */
export function ToneBar({ tone }: { tone: Tone }) {
  return <span aria-hidden="true" className={cn('absolute inset-x-0 top-0 h-1', TONES[tone].bar)} />
}

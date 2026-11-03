import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'
import type { Tone } from '../ui/tones'

/**
 * Per colour: the icon tile's gradient, the card's light wash and its two drifting glows (Tailwind needs the whole
 * class names here). The colours mean the same as in components/ui/tones.ts.
 */
const LOOKS: Record<Tone, { icon: string; wash: string; glows: [string, string] }> = {
  green: { icon: 'from-brand to-deep-blue shadow-brand/30', wash: 'from-brand-light via-card to-sale-light', glows: ['bg-glow-1', 'bg-glow-2'] },
  blue: { icon: 'from-sale to-violet shadow-sale/30', wash: 'from-sale-light via-card to-violet-light', glows: ['bg-glow-2', 'bg-glow-4'] },
  orange: { icon: 'from-new to-hot shadow-hot/25', wash: 'from-new-light via-card to-sale-light', glows: ['bg-glow-3', 'bg-glow-2'] },
  violet: { icon: 'from-violet to-deep-blue shadow-violet/30', wash: 'from-violet-light via-card to-sale-light', glows: ['bg-glow-4', 'bg-glow-2'] },
  pink: { icon: 'from-hot to-violet shadow-hot/30', wash: 'from-hot/10 via-card to-violet-light', glows: ['bg-glow-4', 'bg-glow-3'] },
  yellow: { icon: 'from-accent to-new shadow-accent/30', wash: 'from-warn-light via-card to-new-light', glows: ['bg-glow-3', 'bg-glow-1'] },
}

interface PageHeroProps {
  /** the page's picture in the floating tile */
  icon: LucideIcon
  /** the page's <h1> */
  title: string
  /** the <h1>'s id, for a section named after it (aria-labelledby) */
  titleId?: string
  /** a line under the title */
  subtitle?: ReactNode
  /** the page's colour (tones.ts) */
  tone: Tone
  /** on the right (under the title on a phone): e.g. the steps to order, or a button */
  children?: ReactNode
}

/**
 * The colourful top of a customer page: a floating icon tile, the title and a line under it, on a light wash of the
 * page's colour with two soft glows that drift slowly behind it. The movement is motion-safe:animate-... (styles/
 * index.css), so "reduce motion" turns it off.
 */
export function PageHero({ icon: Icon, title, titleId, subtitle, tone, children }: PageHeroProps) {
  const look = LOOKS[tone]
  return (
    // isolate + overflow-hidden: the drifting glows stay behind the content and inside the card
    <div className={cn('relative isolate mb-6 flex flex-wrap items-center justify-between gap-5 overflow-hidden rounded-3xl border border-line bg-linear-to-br p-5 shadow-sm motion-safe:animate-rise sm:p-7', look.wash)}>
      <span aria-hidden="true" className={cn('absolute -top-20 -left-10 -z-10 size-60 rounded-full opacity-80 blur-3xl motion-safe:animate-blob', look.glows[0])} />
      <span
        aria-hidden="true"
        className={cn('absolute -right-10 -bottom-24 -z-10 size-64 rounded-full opacity-80 blur-3xl motion-safe:animate-blob', look.glows[1])}
        style={{ animationDelay: '-7s' }}
      />

      <div className="flex min-w-0 items-center gap-4">
        <span aria-hidden="true" className={cn('grid size-14 shrink-0 place-items-center rounded-2xl bg-linear-to-br text-white shadow-lg motion-safe:animate-float sm:size-16', look.icon)}>
          <Icon className="size-7 sm:size-8" />
        </span>
        <div className="min-w-0">
          <h1 id={titleId} className="text-3xl md:text-4xl">
            {title}
          </h1>
          {subtitle && <p className="mt-1 text-sm">{subtitle}</p>}
        </div>
      </div>
      {children}
    </div>
  )
}

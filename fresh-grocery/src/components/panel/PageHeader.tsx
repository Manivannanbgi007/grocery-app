import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

/** The page's colour: the card's tint, its drifting lights and the icon chip (decoration; the title says the page). */
const ACCENTS = {
  green: { panel: 'from-brand-light to-tile-1', glow: 'bg-brand/20', chip: 'from-brand to-brand-dark shadow-brand/30' },
  blue: { panel: 'from-sale-light to-tile-3', glow: 'bg-sale/25', chip: 'from-sale to-sale-text shadow-sale/30' },
  orange: { panel: 'from-new-light to-tile-2', glow: 'bg-new/25', chip: 'from-new to-hot shadow-new/30' },
  pink: { panel: 'from-tile-4 to-tile-6', glow: 'bg-hot/20', chip: 'from-hot to-hot-text shadow-hot/30' },
}

export type PageAccent = keyof typeof ACCENTS

interface PageHeaderProps {
  /** the page's <h1> */
  title: string
  /** one or two sentences under the title */
  description?: ReactNode
  icon: LucideIcon
  accent: PageAccent
  /** the page's drawing on the right (hidden on phones), e.g. <ZonesIllustration /> */
  illustration?: ReactNode
  /** buttons beside the text, e.g. "New zone" */
  actions?: ReactNode
}

/**
 * The top of a panel page: a tinted card with two soft lights that drift and a fine dot grid, an icon chip, the title,
 * a line of text, the page's actions and its illustration. All movement is motion-safe.
 */
export function PageHeader({ title, description, icon: Icon, accent, illustration, actions }: PageHeaderProps) {
  const colours = ACCENTS[accent]
  return (
    <header className={cn('relative mb-6 overflow-hidden rounded-3xl border border-line bg-linear-to-br p-5 sm:p-6', colours.panel)}>
      {/* Decoration: two lights that drift, and a fine grid of dots */}
      <span aria-hidden="true" className={cn('absolute -top-24 right-10 size-64 rounded-full blur-3xl motion-safe:animate-float', colours.glow)} />
      <span aria-hidden="true" className={cn('absolute -bottom-28 left-1/3 size-64 rounded-full blur-3xl motion-safe:animate-float', colours.glow)} style={{ animationDelay: '-3.5s' }} />
      <span aria-hidden="true" className="absolute inset-0 bg-[radial-gradient(#253d4e_1px,transparent_1px)] [background-size:22px_22px] opacity-[0.07]" />

      <div className="relative flex items-center gap-6">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-3">
            <span className={cn('grid size-12 shrink-0 place-items-center rounded-2xl bg-linear-to-br text-white shadow-lg', colours.chip)}>
              <Icon className="size-6" aria-hidden="true" />
            </span>
            <h1 className="text-3xl md:text-4xl">{title}</h1>
          </div>
          {description && <p className="mt-3 max-w-2xl text-sm sm:text-base">{description}</p>}
          {actions && <div className="mt-4 flex flex-wrap gap-3">{actions}</div>}
        </div>
        {illustration && <div className="hidden shrink-0 motion-safe:animate-float md:block [&>svg]:h-32 [&>svg]:w-auto lg:[&>svg]:h-36">{illustration}</div>}
      </div>
    </header>
  )
}

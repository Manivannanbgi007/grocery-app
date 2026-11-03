import { ArrowRight, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'
import { formatLongDay, indiaDay } from '../../utils/dates'
import { AUTH_LOOKS, type AuthLook } from '../auth/authLooks'

/** A quick link of the hero, shown as a button (the first one green). */
export interface HeroLink {
  to: string
  label: string
  icon: LucideIcon
}

interface PanelHeroProps {
  /** the panel's look, the same as its login screen: admin dark, vendor orange, delivery blue, shop green */
  look: AuthLook
  /** the page's title (its <h1>), e.g. "Hello, Ravi" */
  title: string
  /** true for a greeting: a hand waves after the title */
  wave?: boolean
  /** the small chip above the title; today's date with a live dot by default */
  eyebrow?: ReactNode
  subtitle?: ReactNode
  links?: HeroLink[]
  /** more under the subtitle, e.g. "You are online until 4 pm" */
  children?: ReactNode
  /** the drawing on the right; by default the panel's own, floating */
  illustration?: ReactNode
  /** smaller, for pages that are not a start page (the account pages) */
  compact?: boolean
}

/**
 * The top of a panel's start page (and, compact, of the account pages): a chip, the title, a line of text, quick links
 * and the panel's illustration, on the panel's own gradient with two soft lights that drift. The same design in every
 * panel; only the look (colours and drawing) changes. All movement is motion-safe.
 */
export function PanelHero({ look, title, wave = false, eyebrow, subtitle, links = [], children, illustration, compact = false }: PanelHeroProps) {
  const { Illustration, dark, classes } = AUTH_LOOKS[look]

  return (
    <section className={cn('relative overflow-hidden rounded-3xl', classes.panel, dark ? 'bg-linear-to-br from-ink via-ink-soft to-ink' : 'border border-line', compact ? 'p-5 sm:p-6' : 'p-6 sm:p-8 lg:p-10')}>
      {/* Decoration: two lights that drift, and a fine grid of dots */}
      <span aria-hidden="true" className={cn('absolute -top-28 right-6 size-80 rounded-full blur-3xl motion-safe:animate-float', classes.glow)} />
      <span aria-hidden="true" className={cn('absolute -bottom-36 left-1/4 size-80 rounded-full blur-3xl motion-safe:animate-float', dark ? 'bg-sale/15' : classes.glow)} style={{ animationDelay: '-3.5s' }} />
      <span
        aria-hidden="true"
        className={cn('absolute inset-0 [background-size:22px_22px]', dark ? 'bg-[radial-gradient(#ffffff_1px,transparent_1px)] opacity-[0.06]' : 'bg-[radial-gradient(#253d4e_1px,transparent_1px)] opacity-[0.07]')}
      />

      <div className="relative grid items-center gap-6 md:grid-cols-[minmax(0,1fr)_auto]">
        <div>
          <p className={cn('inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-bold', dark ? 'border-white/15 bg-white/10 text-ink-text' : 'border-line bg-card/70 text-heading')}>
            {eyebrow ?? (
              <>
                {/* A live dot: these pages keep themselves up to date */}
                <span aria-hidden="true" className="relative flex size-2">
                  <span className={cn('absolute inline-flex size-full rounded-full opacity-75 motion-safe:animate-ping', dark ? 'bg-ink-brand' : 'bg-brand')} />
                  <span className={cn('relative inline-flex size-2 rounded-full', dark ? 'bg-ink-brand' : 'bg-brand')} />
                </span>
                {formatLongDay(indiaDay())}
              </>
            )}
          </p>
          <h1 className={cn('mt-4', dark && 'text-white', compact ? 'text-3xl md:text-4xl' : 'text-3xl sm:text-4xl lg:text-5xl')}>
            {title}
            {wave && (
              <>
                {' '}
                <span aria-hidden="true" className="inline-block origin-[70%_70%] motion-safe:animate-wave">
                  👋
                </span>
              </>
            )}
          </h1>
          {subtitle && <p className={cn('mt-3 max-w-xl', dark ? 'text-ink-text' : 'text-body')}>{subtitle}</p>}
          {children}
          {links.length > 0 && (
            <nav aria-label="Quick links" className={cn('flex flex-wrap gap-3', compact ? 'mt-4' : 'mt-6')}>
              {links.map(({ to, label, icon: Icon }, index) => (
                <Link
                  key={to}
                  to={to}
                  className={cn(
                    'group inline-flex h-10 items-center gap-2 rounded-xl px-4 font-heading text-sm font-bold transition duration-300 hover:-translate-y-0.5',
                    index === 0 && 'bg-brand text-white shadow-lg shadow-brand/30 hover:bg-brand-dark',
                    index > 0 && (dark ? 'border border-white/15 bg-white/10 text-white backdrop-blur hover:bg-white/20' : 'border border-line bg-card/80 text-heading backdrop-blur hover:border-brand hover:text-brand-text'),
                  )}
                >
                  <Icon className="size-4" aria-hidden="true" />
                  {label}
                  <ArrowRight className="size-3.5 opacity-60 transition-transform group-hover:translate-x-0.5 group-hover:opacity-100" aria-hidden="true" />
                </Link>
              ))}
            </nav>
          )}
        </div>
        {illustration ?? (
          <div className="hidden motion-safe:animate-float md:block">
            <Illustration className={cn('w-auto', compact ? 'h-32' : 'h-52 lg:h-60')} />
          </div>
        )}
      </div>
    </section>
  )
}

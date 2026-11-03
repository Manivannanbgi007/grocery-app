import { ArrowUpRight, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'

/** Each tile's own colour: a gradient for the icon chip and a soft glow in the corner. Decoration: the label says what it is. */
const ACCENTS = {
  green: { chip: 'from-brand to-brand-dark shadow-brand/30', glow: 'bg-brand/20' },
  blue: { chip: 'from-sale to-sale-text shadow-sale/30', glow: 'bg-sale/25' },
  orange: { chip: 'from-new to-hot shadow-new/30', glow: 'bg-new/25' },
  pink: { chip: 'from-hot to-hot-text shadow-hot/30', glow: 'bg-hot/20' },
  /** work or money that waits, e.g. refunds */
  amber: { chip: 'from-warn to-warn-text shadow-warn/30', glow: 'bg-warn/25' },
}

export type KpiAccent = keyof typeof ACCENTS

interface KpiTileProps {
  label: string
  value: string
  /** a short explanation under the value, e.g. "orders delivered today" */
  hint?: string
  /** the icon in the coloured chip */
  icon?: LucideIcon
  /** the tile's colour (green by default) */
  accent?: KpiAccent
  /** 'warn' makes the tile amber, e.g. refunds that wait (the same as accent="amber") */
  tone?: 'normal' | 'warn'
  /** the page with the details: the whole tile is then a link that lifts on hover */
  to?: string
  /** under the value and the hint, e.g. a meter */
  children?: ReactNode
  /** e.g. "col-span-2" in a grid */
  className?: string
}

/**
 * One number of a dashboard row, e.g. "Sales ₹12,400": an icon in a coloured chip, the label, the value and a hint, on a
 * card with a soft glow in the tile's colour. The label comes right before the value (the tests read it that way).
 */
export function KpiTile({ label, value, hint, icon: Icon, accent = 'green', tone = 'normal', to, children, className }: KpiTileProps) {
  const colours = ACCENTS[tone === 'warn' ? 'amber' : accent]
  const content = (
    <>
      <span aria-hidden="true" className={cn('absolute -top-12 -right-12 size-32 rounded-full blur-2xl transition-transform duration-500 group-hover:scale-125', colours.glow)} />
      {Icon && (
        <span className={cn('relative mb-4 grid size-11 place-items-center rounded-xl bg-linear-to-br text-white shadow-lg transition-transform duration-300 group-hover:-rotate-6 group-hover:scale-110', colours.chip)}>
          <Icon className="size-5" aria-hidden="true" />
        </span>
      )}
      {to && <ArrowUpRight aria-hidden="true" className="absolute top-4 right-4 size-5 text-muted transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-brand-text" />}
      <p className="relative text-sm">{label}</p>
      <p className="relative font-heading text-2xl font-bold break-words text-heading tabular-nums sm:text-3xl">{value}</p>
      {hint && <p className="relative mt-0.5 text-xs">{hint}</p>}
      {children && <div className="relative">{children}</div>}
    </>
  )

  const tile = cn('group relative flex h-full flex-col overflow-hidden rounded-2xl border border-line bg-card p-4 sm:p-5', className)
  if (!to) return <div className={tile}>{content}</div>
  return (
    <Link to={to} className={cn(tile, 'transition duration-300 hover:-translate-y-1 hover:border-brand-soft hover:shadow-xl hover:shadow-heading/10')}>
      {content}
    </Link>
  )
}

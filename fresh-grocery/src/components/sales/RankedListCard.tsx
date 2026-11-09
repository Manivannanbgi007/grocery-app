import { ArrowRight, ChevronRight, type LucideIcon } from 'lucide-react'
import { useId } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'
import { formatPrice } from '../../utils/format'
import { ErrorState } from '../ui/ErrorState'
import { Skeleton } from '../ui/Skeleton'
import { ToneBar, ToneIcon } from '../ui/ToneIcon'
import { toneCard, TONES, type Tone } from '../ui/tones'

/** One line of a top list: rank, name (a link), a detail ("8 sold") and the sales. */
export interface RankedRow {
  id: number
  label: string
  to: string
  detail: string
  sales: number
  /** what the list is ranked by when it is not the sales (e.g. the units sold): the bars compare it */
  rankedBy?: number
}

interface RankedListCardProps {
  /** "Best sellers", "Top stores" */
  title: string
  /** a line under the title, e.g. what the list is ranked by: "By units sold" */
  subtitle?: string
  /** the picture in the title's tile, e.g. Store */
  icon: LucideIcon
  /** the card's colour (components/ui/tones.ts): the tile, the line along the top, the leader's wash and the bars */
  tone: Tone
  /** undefined while loading */
  rows: RankedRow[] | undefined
  error: Error | null
  onRetry: () => void
  /** a link in the top right corner, e.g. to the full report */
  more?: { to: string; label: string }
}

/**
 * The places 1, 2 and 3 as gold, silver and bronze medals; the others as a grey tile. The medals keep their colours in
 * the dark theme (accent and new do not change; the silver and bronze are written out for that reason).
 */
const MEDALS = [
  'bg-linear-to-br from-accent to-new text-ink shadow-md shadow-accent/40',
  'bg-linear-to-br from-[#eef2f6] to-[#a9b6c2] text-ink shadow-md shadow-heading/15',
  'bg-linear-to-br from-[#e6a874] to-[#a8622f] text-white shadow-md shadow-new/30',
]

/** The bars per colour, light to full (Tailwind needs the whole class names here). */
const BARS: Record<Tone, string> = {
  green: 'from-brand/50 to-brand',
  blue: 'from-sale/50 to-sale',
  orange: 'from-new/50 to-new',
  violet: 'from-violet/50 to-violet',
  pink: 'from-hot/50 to-hot',
  yellow: 'from-accent/50 to-accent',
  teal: 'from-teal/50 to-teal',
}

function RankBadge({ rank }: { rank: number }) {
  const medal = MEDALS[rank - 1]
  return (
    <span className={cn('grid size-9 shrink-0 place-items-center rounded-full font-heading text-sm font-bold', medal ?? 'bg-surface text-heading ring-1 ring-line')}>
      {rank}
    </span>
  )
}

/** Grey rows where the list will be, while it loads. */
function LoadingRows({ title }: { title: string }) {
  return (
    <div role="status" aria-label={`Loading the ${title.toLowerCase()}`} className="space-y-2">
      {[0, 1, 2].map((row) => (
        <div key={row} className="flex items-center gap-3 p-2.5">
          <Skeleton className="size-9 rounded-full" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-2 w-full rounded-full" />
          </div>
        </div>
      ))}
    </div>
  )
}

/**
 * A card with a top list, e.g. the best-selling products of a period: medals for the first three, the leader on a light
 * wash of the card's colour, and a bar under each line that compares it with the leader (the numbers are written out
 * too, so the bars need no legend). A whole line is the link; hovering it shows an arrow.
 */
export function RankedListCard({ title, subtitle, icon, tone, rows, error, onRetry, more }: RankedListCardProps) {
  const titleId = useId()
  let content
  if (error) content = <ErrorState compact title={`Could not load the ${title.toLowerCase()}`} error={error} onRetry={onRetry} />
  else if (!rows) content = <LoadingRows title={title} />
  else if (rows.length === 0) {
    content = (
      <div className="flex flex-1 flex-col items-center justify-center gap-3 py-8 text-center">
        <ToneIcon icon={icon} tone={tone} className="size-12 rounded-2xl" />
        <p className="text-sm">No sales in this period yet.</p>
      </div>
    )
  } else {
    const valueOf = (row: RankedRow) => row.rankedBy ?? row.sales
    const best = Math.max(...rows.map(valueOf))
    const total = rows.reduce((sum, row) => sum + row.sales, 0)
    content = (
      <>
        <ol aria-labelledby={titleId} className="mb-4 space-y-1.5 text-sm">
          {rows.map((row, index) => (
            <li
              key={row.id}
              // The lines come in one after the other; the leader sits on a light wash of the card's colour
              className={cn(
                'group relative rounded-xl p-2.5 transition-colors hover:bg-surface motion-safe:animate-rise',
                index === 0 && cn('bg-linear-to-r to-transparent', TONES[tone].tint),
              )}
              style={{ animationDelay: `${index * 70}ms` }}
            >
              <div className="flex items-center gap-3">
                <RankBadge rank={index + 1} />
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-3">
                    <div className="min-w-0 flex-1">
                      {/* after:inset-0 stretches the link over the whole line, so the line is one big target */}
                      <Link
                        to={row.to}
                        className="block truncate font-bold text-heading outline-none after:absolute after:inset-0 after:rounded-xl group-hover:text-brand-text focus-visible:after:ring-2 focus-visible:after:ring-brand"
                      >
                        {row.label}
                      </Link>
                      <span className="text-xs tabular-nums">{row.detail}</span>
                    </div>
                    <span className="shrink-0 font-heading font-bold text-heading tabular-nums">{formatPrice(row.sales)}</span>
                    <ChevronRight className="size-4 shrink-0 text-muted transition group-hover:translate-x-0.5 group-hover:text-brand-text" aria-hidden="true" />
                  </div>
                  <div aria-hidden="true" className="mt-2 h-2 rounded-full bg-line/50">
                    {/* animate-grow-x: the bar grows from the left when the list appears */}
                    <div
                      className={cn('h-full origin-left rounded-full bg-linear-to-r motion-safe:animate-grow-x', BARS[tone])}
                      style={{ width: `${best > 0 ? (valueOf(row) / best) * 100 : 0}%`, animationDelay: `${index * 70}ms` }}
                    />
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ol>
        {/* The list's total, at the bottom of the card (mt-auto) when the card is taller than its list */}
        <p className="mt-auto flex items-center justify-between gap-3 border-t border-line pt-3 text-xs">
          <span>{rows.length === 1 ? 'Sales' : `All ${rows.length} together`}</span>
          <strong className="font-heading text-sm text-heading tabular-nums">{formatPrice(total)}</strong>
        </p>
      </>
    )
  }

  return (
    // min-w-0: in a grid, a long product name (cut off with "…") must not make the card wider than the screen
    <section aria-labelledby={titleId} className={toneCard(tone, 'flex min-w-0 flex-col rounded-2xl p-4 shadow-sm shadow-heading/5 sm:p-6')}>
      <ToneBar tone={tone} />
      <div className="mb-4 flex items-center gap-3">
        <ToneIcon icon={icon} tone={tone} solid />
        <div className="min-w-0 flex-1">
          <h3 id={titleId} className="text-lg">
            {title}
          </h3>
          {subtitle && <p className="text-xs">{subtitle}</p>}
        </div>
        {more && (
          <Link to={more.to} className="flex shrink-0 items-center gap-1 rounded-full px-3 py-1.5 text-xs font-bold text-brand-text transition-colors hover:bg-brand-light">
            {more.label}
            <ArrowRight className="size-3.5" aria-hidden="true" />
          </Link>
        )}
      </div>
      {content}
    </section>
  )
}

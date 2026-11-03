import { useState } from 'react'
import { cn } from '../../utils/cn'

/** What a part means; the colours are the chart-* tokens of styles/index.css (validated as a set, both themes). */
export type DonutTone = 'good' | 'wait' | 'bad' | 'info'

export interface DonutPart {
  key: string
  /** e.g. "Cancelled or rejected" */
  label: string
  value: number
  tone: DonutTone
  /** a second number for the legend, e.g. the amount of these payments */
  detail?: string
}

interface DonutChartProps {
  /** the legend's name for screen readers, e.g. "Orders in this period by outcome" */
  label: string
  /** at most 4 parts (a fifth colour could not be told apart from the others) */
  parts: DonutPart[]
  /** what the number in the middle counts, one and many: ['order', 'orders'] */
  countWords: [string, string]
}

const STROKE_CLASSES: Record<DonutTone, string> = {
  good: 'stroke-chart-good',
  wait: 'stroke-chart-wait',
  bad: 'stroke-chart-bad',
  info: 'stroke-chart-info',
}
const SWATCH_CLASSES: Record<DonutTone, string> = {
  good: 'bg-chart-good',
  wait: 'bg-chart-wait',
  bad: 'bg-chart-bad',
  info: 'bg-chart-info',
}

/** The circle's radius makes its circumference exactly 100, so a part's length is simply its percentage. */
const RADIUS = 100 / (2 * Math.PI)
/** A 2px gap in the card colour between two parts (in the chart's 100-unit circumference, about 0.6 units). */
const GAP = 0.6

/**
 * Parts of a whole as a ring, for a quick look at shares (never for comparing close values: use bars then). The legend
 * beside it always names every part with its number and share, so no value depends on colour or hovering; hovering a
 * part or focusing its legend row shows that part in the middle and fades the others.
 */
export function DonutChart({ label, parts, countWords }: DonutChartProps) {
  const [activeKey, setActiveKey] = useState<string | null>(null)
  const total = parts.reduce((sum, part) => sum + part.value, 0)
  const active = parts.find((part) => part.key === activeKey && part.value > 0) ?? null
  const shareOf = (value: number) => (total === 0 ? 0 : Math.round((value / total) * 100))
  const counted = (count: number) => (count === 1 ? countWords[0] : countWords[1])

  // Each part starts where the parts before it end, from 12 o'clock (dashoffset 25 = a quarter turn back)
  const visible = parts.filter((part) => part.value > 0)
  const arcs = visible.map((part, index) => {
    const before = visible.slice(0, index).reduce((sum, other) => sum + other.value, 0)
    const length = (part.value / total) * 100
    // One part alone is a full ring; between several, a gap
    return { part, offset: 25 - (before / total) * 100, length: visible.length > 1 ? Math.max(length - GAP, 0.1) : length }
  })

  return (
    // The legend sits beside the ring only when the card itself is wide enough (a container query, not the screen width)
    <div className="@container">
      <div className="flex flex-col items-center gap-6 @md:flex-row">
        <div className="relative size-40 shrink-0">
          <svg aria-hidden="true" viewBox="0 0 42 42" className="size-full">
            <circle cx="21" cy="21" r={RADIUS} fill="none" strokeWidth="6" className="stroke-surface" />
            {arcs.map(({ part, offset, length }) => (
              <circle
                key={part.key}
                cx="21"
                cy="21"
                r={RADIUS}
                fill="none"
                strokeWidth={active?.key === part.key ? 7 : 6}
                strokeDasharray={`${length} ${100 - length}`}
                strokeDashoffset={offset}
                onPointerEnter={() => setActiveKey(part.key)}
                onPointerLeave={() => setActiveKey(null)}
                // animate-draw: each part sweeps from nothing to its length when the chart appears
                className={cn(STROKE_CLASSES[part.tone], 'cursor-default transition-all motion-safe:animate-draw', active && active.key !== part.key && 'opacity-30')}
              />
            ))}
          </svg>
          {/* The middle: the total, or the part that is hovered or focused */}
          <div aria-hidden="true" className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center">
            <span className="font-heading text-2xl font-bold text-heading">{active ? active.value : total}</span>
            <span className="max-w-24 text-xs leading-tight">{active ? `${shareOf(active.value)}% · ${active.label}` : counted(total)}</span>
          </div>
        </div>
        <ul aria-label={label} className="w-full min-w-0 flex-1 space-y-1 text-sm">
          {parts.map((part) => (
            <li
              key={part.key}
              tabIndex={0}
              onPointerEnter={() => setActiveKey(part.key)}
              onPointerLeave={() => setActiveKey(null)}
              onFocus={() => setActiveKey(part.key)}
              onBlur={() => setActiveKey(null)}
              className={cn('flex items-center gap-3 rounded-lg px-2 py-1.5 transition-colors', active?.key === part.key && 'bg-surface')}
            >
              <span aria-hidden="true" className={cn('size-3 shrink-0 rounded-sm', SWATCH_CLASSES[part.tone])} />
              <span className="min-w-0 flex-1 text-heading">{part.label}</span>
              <span className="text-right tabular-nums">
                <strong className="text-heading">{part.value}</strong>
                <span className="ml-1.5 inline-block w-10 text-xs">{shareOf(part.value)}%</span>
                {part.detail && <span className="block text-xs">{part.detail}</span>}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

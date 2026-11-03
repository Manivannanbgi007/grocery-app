import { useId, type ReactNode } from 'react'
import { cn } from '../../utils/cn'
import { formatShortDay, formatWeekdayDay } from '../../utils/dates'
import { formatCompactRupees, formatPrice } from '../../utils/format'
import { labelStep, niceMax, tooltipSide, type ChartDay } from './chartScale'

interface AreaChartProps {
  /** the chart's name for screen readers, e.g. "Sales per day" */
  label: string
  /** what the line shows, for the legend: "Sales" */
  valueName: string
  days: ChartDay[]
  /** what is counted, one and many: ['order', 'orders'] */
  countWords: [string, string]
}

const percentOf = (value: number, max: number) => (value / max) * 100

/** One entry of the legend: a small sample of the mark, and what it means. */
function LegendItem({ mark, text }: { mark: ReactNode; text: string }) {
  return (
    <span className="flex items-center gap-1.5">
      {mark}
      {text}
    </span>
  )
}

/**
 * Rupees over time as a line over a soft gradient. What it shows:
 * - a legend above it: the line, the dashed daily average (more than one day) and the gold best day;
 * - the best day's point in gold with its amount over it, the last day's point in green;
 * - every day is a full-height column: hovering or focusing it tints the column and shows its point and a tooltip with
 *   the day, the amount, the number of orders and the change from the day before.
 * A new period (another first day or length) draws the chart again from the left. The numbers should also be in a
 * table next to the chart.
 */
export function AreaChart({ label, valueName, days, countWords }: AreaChartProps) {
  const gradientId = useId()
  const max = niceMax(Math.max(0, ...days.map((day) => day.value)))
  const best = days.reduce<ChartDay | null>((top, day) => (day.value > (top?.value ?? 0) ? day : top), null)
  const average = days.length > 0 ? days.reduce((sum, day) => sum + day.value, 0) / days.length : 0
  const showAverage = days.length > 1 && average > 0
  const labelEvery = labelStep(days.length)
  const counted = (count: number) => `${count} ${count === 1 ? countWords[0] : countWords[1]}`

  // The SVG draws in a 100 × 100 box stretched over the plot; each point sits in the middle of its column
  const xOf = (index: number) => ((index + 0.5) / days.length) * 100
  const yOf = (value: number) => 100 - percentOf(value, max)
  // One day has no trend: its line runs flat across the whole plot (a single point would draw nothing)
  const points = days.length === 1 ? [[0, yOf(days[0].value)], [100, yOf(days[0].value)]] : days.map((day, index) => [xOf(index), yOf(day.value)])
  const linePoints = points.map(([x, y]) => `${x},${y}`).join(' ')
  const areaPath = points.length > 0 ? `M ${points[0][0]},100 L ${linePoints.replaceAll(' ', ' L ')} L ${points[points.length - 1][0]},100 Z` : ''
  // A new period gets a new key, so the plot starts again and its animations play once more
  const drawKey = `${days[0]?.date ?? ''}-${days.length}`

  return (
    <div>
      {/* The legend is for the eyes; screen readers get each day's numbers from the list below */}
      <div aria-hidden="true" className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
        <LegendItem mark={<span className="h-0.5 w-4 rounded-full bg-brand-dark" />} text={valueName} />
        {showAverage && <LegendItem mark={<span className="w-4 border-t-2 border-dashed border-sale" />} text="Daily average" />}
        {best && <LegendItem mark={<span className="size-2.5 rounded-full bg-accent ring-2 ring-accent/30" />} text="Best day" />}
      </div>
      <div className="flex gap-2">
        {/* The scale: ₹0, half and the top, on hairline gridlines */}
        <div aria-hidden="true" className="relative h-48 w-12 shrink-0 text-right text-xs tabular-nums sm:h-56">
          {[max, max / 2, 0].map((tick, index) => (
            <span key={tick} className="absolute right-0 -translate-y-1/2" style={{ top: `${index * 50}%` }}>
              {formatCompactRupees(tick)}
            </span>
          ))}
        </div>
        <div key={drawKey} className="relative h-48 flex-1 sm:h-56">
          {[0, 50, 100].map((top) => (
            <span key={top} aria-hidden="true" className="absolute inset-x-0 h-px bg-line" style={{ top: `${top}%` }} />
          ))}
          {/* animate-reveal: the line and area uncover from left to right when the chart appears */}
          <svg aria-hidden="true" viewBox="0 0 100 100" preserveAspectRatio="none" className="absolute inset-0 size-full overflow-visible motion-safe:animate-reveal">
            <defs>
              {/* The area: the brand colour at the line, fading out towards ₹0 */}
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" style={{ stopColor: 'var(--color-brand)', stopOpacity: 0.3 }} />
                <stop offset="100%" style={{ stopColor: 'var(--color-brand)', stopOpacity: 0 }} />
              </linearGradient>
            </defs>
            <path d={areaPath} fill={`url(#${gradientId})`} />
            <polyline points={linePoints} fill="none" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" className="stroke-brand-dark" />
          </svg>
          {showAverage && (
            // The daily average as a dashed line that grows from the left, with its amount on it
            <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 origin-left border-t-2 border-dashed border-sale/80 motion-safe:animate-grow-x" style={{ top: `${yOf(average)}%` }}>
              <span className="absolute bottom-1 left-1 rounded-full bg-sale-light px-2 py-0.5 text-[11px] font-bold whitespace-nowrap text-sale-text">Avg {formatCompactRupees(average)}</span>
            </div>
          )}
          <ul aria-label={label} className="absolute inset-0 flex">
            {days.map((day, index) => {
              const height = percentOf(day.value, max)
              const isBest = day === best
              const last = index === days.length - 1
              const change = index > 0 ? Math.round((day.value - days[index - 1].value) * 100) / 100 : null
              return (
                // The whole column is the hover and focus target: the reader aims at a day, not at a 2px line
                <li
                  key={day.date}
                  tabIndex={0}
                  aria-label={`${formatShortDay(day.date)}: ${formatPrice(day.value)} from ${counted(day.count)}`}
                  className="group relative h-full flex-1 cursor-default outline-offset-[-2px] transition-colors hover:bg-brand/5 focus-visible:bg-brand/5"
                >
                  <span aria-hidden="true" className="absolute inset-y-0 left-1/2 hidden w-px bg-muted group-hover:block group-focus-visible:block" />
                  {/* The point: gold for the best day (it pops in after the line), green for the last day, else on hover */}
                  <span
                    aria-hidden="true"
                    className={cn(
                      'absolute left-1/2 -translate-x-1/2 translate-y-1/2 rounded-full ring-2 ring-card',
                      isBest ? 'size-3 bg-accent shadow-md shadow-accent/50 motion-safe:animate-pop' : 'size-2.5 bg-brand-dark',
                      isBest || last ? 'block' : 'hidden group-hover:block group-focus-visible:block',
                    )}
                    style={{ bottom: `${height}%`, animationDelay: isBest ? '0.8s' : undefined }}
                  />
                  {isBest && (
                    <span
                      aria-hidden="true"
                      className="absolute left-1/2 -translate-x-1/2 rounded-full bg-card/90 px-1.5 text-xs font-bold whitespace-nowrap text-heading shadow-sm group-hover:hidden"
                      style={{ bottom: `calc(${height}% + 10px)` }}
                    >
                      {formatCompactRupees(day.value)}
                    </span>
                  )}
                  <span
                    aria-hidden="true"
                    className={cn(
                      'pointer-events-none absolute top-0 z-10 hidden w-max rounded-xl border border-line bg-card px-3 py-2 text-xs shadow-xl shadow-heading/10 group-hover:block group-focus-visible:block',
                      tooltipSide(index, days.length),
                    )}
                  >
                    <span className="block font-bold text-heading">{formatWeekdayDay(day.date)}</span>
                    <strong className="block font-heading text-base text-heading">{formatPrice(day.value)}</strong>
                    {counted(day.count)}
                    {change !== null && change !== 0 && (
                      <span className={cn('block font-bold', change > 0 ? 'text-brand-text' : 'text-hot-text')}>
                        {change > 0 ? '▲' : '▼'} {formatPrice(Math.abs(change))} vs the day before
                      </span>
                    )}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      </div>
      {/* A label is centred under its point and may be wider than its column; phones show every second one */}
      <div aria-hidden="true" className="mt-2 flex h-4 pl-14 text-xs">
        {days.map((day, index) => (
          <span key={day.date} className="relative min-w-0 flex-1">
            {index % labelEvery === 0 && (
              <span className={cn('absolute left-1/2 -translate-x-1/2 whitespace-nowrap', index % (labelEvery * 2) !== 0 && 'hidden sm:block')}>
                {formatShortDay(day.date)}
              </span>
            )}
          </span>
        ))}
      </div>
    </div>
  )
}

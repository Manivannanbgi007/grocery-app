import { cn } from '../../utils/cn'
import { formatShortDay } from '../../utils/dates'
import { formatCompactRupees, formatPrice } from '../../utils/format'
import { labelStep, niceMax, tooltipSide, type ChartDay } from '../charts/chartScale'

interface DailyBarChartProps {
  /** the chart's name for screen readers, e.g. "Sales per day" */
  label: string
  days: ChartDay[]
  /** what is counted, one and many: ['order', 'orders'] */
  countWords: [string, string]
  /** the axis and tooltip text of a column: "26 Oct" by default */
  formatLabel?: (date: string) => string
}

const percentOf = (value: number, max: number) => (value / max) * 100

/**
 * Rupees per day (or month) as columns: one series, so no legend (the title names it). Columns are at most 24px wide,
 * grow from the baseline and round only at the top; only the best column carries its value, every column shows it on
 * hover and keyboard focus, and the numbers should also be in a table next to the chart.
 */
export function DailyBarChart({ label, days, countWords, formatLabel = formatShortDay }: DailyBarChartProps) {
  const max = niceMax(Math.max(0, ...days.map((day) => day.value)))
  const best = days.reduce<ChartDay | null>((top, day) => (day.value > (top?.value ?? 0) ? day : top), null)
  const labelEvery = labelStep(days.length)
  const counted = (count: number) => `${count} ${count === 1 ? countWords[0] : countWords[1]}`

  return (
    <div>
      <div className="flex gap-2">
        {/* The scale: ₹0, half and the top, on hairline gridlines */}
        <div aria-hidden="true" className="relative h-56 w-12 shrink-0 text-right text-xs tabular-nums">
          {[max, max / 2, 0].map((tick, index) => (
            <span key={tick} className="absolute right-0 -translate-y-1/2" style={{ top: `${index * 50}%` }}>
              {formatCompactRupees(tick)}
            </span>
          ))}
        </div>
        <div className="relative h-56 flex-1">
          {[0, 50, 100].map((top) => (
            <span key={top} aria-hidden="true" className="absolute inset-x-0 h-px bg-line" style={{ top: `${top}%` }} />
          ))}
          <ul aria-label={label} className="absolute inset-0 flex items-end gap-1">
            {days.map((day, index) => {
              const height = percentOf(day.value, max)
              return (
                // The whole column is the hover and focus target, not only the painted bar
                <li key={day.date} tabIndex={0} aria-label={`${formatLabel(day.date)}: ${formatPrice(day.value)} from ${counted(day.count)}`} className="group relative flex h-full flex-1 cursor-default items-end justify-center">
                  {day === best && (
                    <span aria-hidden="true" className="absolute text-xs font-bold text-heading" style={{ bottom: `calc(${height}% + 4px)` }}>
                      {formatCompactRupees(day.value)}
                    </span>
                  )}
                  <span aria-hidden="true" className="w-full max-w-6 rounded-t bg-brand-dark transition-colors group-hover:bg-brand-text group-focus-visible:bg-brand-text" style={{ height: `${height}%` }} />
                  <span
                    aria-hidden="true"
                    className={cn(
                      'pointer-events-none absolute bottom-full z-10 mb-1 hidden w-max rounded-lg border border-line bg-card px-3 py-2 text-xs shadow-lg group-hover:block group-focus-visible:block',
                      tooltipSide(index, days.length),
                    )}
                  >
                    <strong className="block font-heading text-sm text-heading">{formatPrice(day.value)}</strong>
                    {formatLabel(day.date)} · {counted(day.count)}
                  </span>
                </li>
              )
            })}
          </ul>
        </div>
      </div>
      {/* A label is centred under its column and may be wider than it (so it never widens the page); phones show every second one */}
      <div aria-hidden="true" className="mt-2 flex h-4 gap-1 pl-14 text-xs">
        {days.map((day, index) => (
          <span key={day.date} className="relative min-w-0 flex-1">
            {index % labelEvery === 0 && (
              <span className={cn('absolute left-1/2 -translate-x-1/2 whitespace-nowrap', index % (labelEvery * 2) !== 0 && 'hidden sm:block')}>
                {formatLabel(day.date)}
              </span>
            )}
          </span>
        ))}
      </div>
    </div>
  )
}

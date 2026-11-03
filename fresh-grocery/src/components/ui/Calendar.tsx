import { ChevronLeft, ChevronRight, type LucideIcon } from 'lucide-react'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { addDays, addMonths, clampDay, dayOf, formatFullDay, MONTH_NAMES, monthWeeks, partsOf, WEEKDAY_NAMES, weekdayOf } from '../../utils/calendar'
import { cn } from '../../utils/cn'
import { Select } from './Select'

interface CalendarProps {
  /** the chosen day (YYYY-MM-DD), or '' */
  value: string
  onChoose: (day: string) => void
  /** today (India time): ringed, and where the calendar opens when no day is chosen */
  today: string
  min?: string
  max?: string
  /** a chosen period (a range picker): its first and last day are filled, the days between them tinted */
  range?: { from: string; to: string }
}

/** Without a min or max, the year drop-down offers this many years before and after today. */
const YEARS_BACK = 100
const YEARS_AHEAD = 10

function MonthButton({ label, icon: Icon, disabled, onClick }: { label: string; icon: LucideIcon; disabled: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-8 shrink-0 place-items-center rounded-full text-heading transition-colors hover:bg-surface disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:bg-transparent"
    >
      <Icon className="size-5" aria-hidden="true" />
    </button>
  )
}

/**
 * A month to choose a day from (the inside of DateField): month and year drop-downs and arrows to move, the days from
 * Sunday to Saturday, today ringed, the chosen day filled, days outside min/max greyed out.
 * Keyboard (the grid has one tab stop, the day with the focus): ← → a day, ↑ ↓ a week, Home/End the start or end of the
 * week, PageUp/PageDown a month (with Shift a year), Enter or Space chooses.
 */
export function Calendar({ value, onChoose, today, min, max, range }: CalendarProps) {
  // The day that has (or gets) the keyboard focus; its month is the one shown
  const [focused, setFocused] = useState(() => clampDay(value || today, min, max))
  const { year, month } = partsOf(focused)
  const gridRef = useRef<HTMLTableElement>(null)
  // true when the focus should follow `focused` into the grid: when the calendar opens, and after a key moved it
  const moveFocus = useRef(true)

  useEffect(() => {
    if (!moveFocus.current) return
    moveFocus.current = false
    gridRef.current?.querySelector<HTMLButtonElement>(`[data-day="${focused}"]`)?.focus()
  }, [focused])

  const allowed = (day: string) => clampDay(day, min, max) === day
  /** Shows the month of `day` (kept within min and max), without moving the focus. */
  const showMonthOf = (day: string) => setFocused(clampDay(day, min, max))

  function handleKeyDown(event: KeyboardEvent<HTMLTableElement>) {
    const moves: Record<string, (day: string) => string> = {
      ArrowLeft: (day) => addDays(day, -1),
      ArrowRight: (day) => addDays(day, 1),
      ArrowUp: (day) => addDays(day, -7),
      ArrowDown: (day) => addDays(day, 7),
      Home: (day) => addDays(day, -weekdayOf(day)),
      End: (day) => addDays(day, 6 - weekdayOf(day)),
      PageUp: (day) => addMonths(day, event.shiftKey ? -12 : -1),
      PageDown: (day) => addMonths(day, event.shiftKey ? 12 : 1),
    }
    const move = moves[event.key]
    if (!move) return
    event.preventDefault()
    const next = clampDay(move(focused), min, max)
    if (next === focused) return
    moveFocus.current = true
    setFocused(next)
  }

  const firstYear = Math.min(min ? partsOf(min).year : partsOf(today).year - YEARS_BACK, year)
  const lastYear = Math.max(max ? partsOf(max).year : partsOf(today).year + YEARS_AHEAD, year)
  const years = Array.from({ length: lastYear - firstYear + 1 }, (_, index) => String(firstYear + index))

  return (
    <div>
      <div className="mb-3 flex items-center gap-1.5">
        <MonthButton label="Previous month" icon={ChevronLeft} disabled={min !== undefined && dayOf(year, month, 0) < min} onClick={() => showMonthOf(addMonths(focused, -1))} />
        <Select
          size="sm"
          aria-label="Month"
          value={String(month)}
          onChange={(chosen) => showMonthOf(addMonths(focused, Number(chosen) - month))}
          options={MONTH_NAMES.map((name, index) => ({ value: String(index), label: name }))}
          className="min-w-0 flex-1"
        />
        <Select
          size="sm"
          aria-label="Year"
          value={String(year)}
          onChange={(chosen) => showMonthOf(addMonths(focused, (Number(chosen) - year) * 12))}
          options={years.map((option) => ({ value: option, label: option }))}
          // Narrow, so the longest month name ("September") still fits beside it on a phone
          className="w-21 shrink-0"
        />
        <MonthButton label="Next month" icon={ChevronRight} disabled={max !== undefined && dayOf(year, month + 1, 1) > max} onClick={() => showMonthOf(addMonths(focused, 1))} />
      </div>
      <table ref={gridRef} role="grid" aria-label={`${MONTH_NAMES[month]} ${year}`} onKeyDown={handleKeyDown} className="w-full table-fixed border-collapse text-center text-sm">
        <thead>
          <tr>
            {WEEKDAY_NAMES.map((name) => (
              <th key={name} scope="col" className="pb-1 text-xs font-bold text-body">
                <span aria-hidden="true">{name.slice(0, 2)}</span>
                <span className="sr-only">{name}</span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {monthWeeks(year, month).map((week) => (
            <tr key={week[0]}>
              {week.map((day) => {
                const chosen = range ? day === range.from || day === range.to : day === value
                const between = range !== undefined && day > range.from && range.to !== '' && day < range.to
                const otherMonth = partsOf(day).month !== month
                return (
                  <td key={day} aria-selected={chosen || between} className="p-0.5">
                    <button
                      type="button"
                      data-day={day}
                      tabIndex={day === focused ? 0 : -1}
                      aria-label={formatFullDay(day)}
                      aria-current={day === today ? 'date' : undefined}
                      disabled={!allowed(day)}
                      onClick={() => onChoose(day)}
                      className={cn(
                        'mx-auto grid size-9 place-items-center rounded-full transition-colors outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2 focus-visible:ring-offset-card',
                        'disabled:cursor-not-allowed disabled:opacity-35',
                        chosen ? 'bg-brand font-bold text-white' : 'hover:bg-brand-light disabled:hover:bg-transparent',
                        between && 'bg-brand-light font-bold text-brand-text',
                        !chosen && !between && day === today && 'font-bold text-brand-text ring-1 ring-brand',
                        !chosen && !between && day !== today && (otherMonth ? 'text-muted' : 'text-heading'),
                      )}
                    >
                      {partsOf(day).day}
                    </button>
                  </td>
                )
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

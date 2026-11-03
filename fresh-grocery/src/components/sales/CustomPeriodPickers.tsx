import { CalendarDays, CalendarRange, type LucideIcon } from 'lucide-react'
import { useId, useRef, useState, type FocusEvent, type ReactNode } from 'react'
import { useDismiss } from '../../hooks/useDismiss'
import { usePopoverPlacement } from '../../hooks/usePopoverPlacement'
import { addDays, dayOf, formatDayLabel, partsOf } from '../../utils/calendar'
import { cn } from '../../utils/cn'
import { daysBetween, indiaDay } from '../../utils/dates'
import { Button } from '../ui/Button'
import { Calendar } from '../ui/Calendar'
import { periodProblem } from './reportPeriod'
import { periodLabel, periodTabClass, type ChosenPeriod } from './salesPeriod'

/*
 * The two calendar buttons at the end of the period row (PeriodPicker): "Pick a day" and "Custom range". Both give
 * onChoose the first and the last day (the same day twice for one day); the page puts them into its URL.
 */

interface CustomPeriodProps {
  /** the period shown now: the button of its kind is filled and shows its dates */
  period: ChosenPeriod
  onChoose: (from: string, to: string) => void
}

const SMALL_BUTTON = 'rounded-full px-3 py-1.5 font-heading text-sm font-bold text-brand-text transition-colors hover:bg-brand-light'

interface PeriodPopoverProps {
  icon: LucideIcon
  /** the button's text */
  text: ReactNode
  /** this kind of period is shown now: the button is filled like a chosen tab */
  chosen: boolean
  /** the card's name for screen readers */
  dialogLabel: string
  /** what the card shows while it is open; call close() after a choice (the focus goes back to the button) */
  children: (close: () => void) => ReactNode
}

/** A button of the period row that opens a card under it (or above it, when there is no room), like DateField. */
function PeriodPopover({ icon: Icon, text, chosen, dialogLabel, children }: PeriodPopoverProps) {
  const rootRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const buttonId = useId()
  const [open, setOpen] = useState(false)
  const placement = usePopoverPlacement(rootRef, panelRef, open)

  // Closes the card and puts the focus back on the button (the card's day buttons disappear with it)
  function close() {
    setOpen(false)
    document.getElementById(buttonId)?.focus()
  }
  // Escape puts the focus back on the button; a click elsewhere leaves it where the user clicked
  useDismiss(rootRef, open, (reason) => (reason === 'escape' ? close() : setOpen(false)))

  // Tabbing out of the card closes it (a click elsewhere is handled by useDismiss)
  function handleBlur(event: FocusEvent<HTMLDivElement>) {
    if (event.relatedTarget && !rootRef.current?.contains(event.relatedTarget)) setOpen(false)
  }

  return (
    <div ref={rootRef} onBlur={handleBlur} className="relative">
      <button
        id={buttonId}
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-current={chosen ? 'true' : undefined}
        onClick={() => setOpen(!open)}
        className={cn(periodTabClass(chosen), 'flex items-center gap-1.5 whitespace-nowrap')}
      >
        <Icon className="size-4 shrink-0" aria-hidden="true" />
        {text}
      </button>
      {open && (
        <div
          ref={panelRef}
          role="dialog"
          aria-label={dialogLabel}
          className={cn(
            'absolute z-50 w-[19.5rem] max-w-[calc(100vw-2rem)] rounded-2xl border border-line bg-card p-3 shadow-xl shadow-heading/10',
            'transition-[opacity,scale] duration-150 starting:scale-95 starting:opacity-0 motion-reduce:transition-none',
            placement.up ? 'bottom-full mb-1.5 origin-bottom' : 'top-full mt-1.5 origin-top',
            placement.alignRight ? 'right-0' : 'left-0',
          )}
        >
          {children(close)}
        </div>
      )}
    </div>
  )
}

/** "Pick a day": one day's sales, shown as soon as the day is clicked. */
export function DayPicker({ period, onChoose }: CustomPeriodProps) {
  const chosen = period.kind === 'day' && period.problem === null
  const text = chosen ? (
    <>
      {/* The space is outside the hidden span: a trailing space inside it gets lost in the button's name */}
      <span className="sr-only">Day:</span> {periodLabel(period.from, period.to)}
    </>
  ) : (
    'Pick a day'
  )
  return (
    <PeriodPopover icon={CalendarDays} text={text} chosen={chosen} dialogLabel="Pick a day: choose a date">
      {(close) => (
        <DayCard
          day={chosen ? period.from : ''}
          onChoose={(day) => {
            onChoose(day, day)
            close()
          }}
        />
      )}
    </PeriodPopover>
  )
}

/** The inside of "Pick a day": a month to choose from (no day after today), and quick buttons for today and yesterday. */
function DayCard({ day, onChoose }: { day: string; onChoose: (day: string) => void }) {
  const today = indiaDay()
  return (
    <>
      <Calendar value={day} onChoose={onChoose} today={today} max={today} />
      <div className="mt-2 flex gap-1 border-t border-line pt-2">
        <button type="button" onClick={() => onChoose(today)} className={SMALL_BUTTON}>
          Today
        </button>
        <button type="button" onClick={() => onChoose(addDays(today, -1))} className={SMALL_BUTTON}>
          Yesterday
        </button>
      </div>
    </>
  )
}

/** "Custom range": the sales of any days in a row (up to 366), from a first to a last day. */
export function RangePicker({ period, onChoose }: CustomPeriodProps) {
  const chosen = period.kind === 'range' && period.problem === null
  const text = chosen ? (
    <>
      {/* The space is outside the hidden span: a trailing space inside it gets lost in the button's name */}
      <span className="sr-only">Custom range:</span> {periodLabel(period.from, period.to)}
    </>
  ) : (
    'Custom range'
  )
  return (
    <PeriodPopover icon={CalendarRange} text={text} chosen={chosen} dialogLabel="Custom range: choose the first and the last day">
      {(close) => (
        <RangeCard
          first={chosen ? period.from : ''}
          last={chosen ? period.to : ''}
          onApply={(from, to) => {
            onChoose(from, to)
            close()
          }}
        />
      )}
    </PeriodPopover>
  )
}

/** "First day · 12 Oct 2026": one end of the range, ringed while the next click picks it. */
function RangeEnd({ label, day, next }: { label: string; day: string; next: boolean }) {
  return (
    <div className={cn('rounded-lg border px-3 py-1.5', next ? 'border-brand ring-2 ring-brand/15' : 'border-line')}>
      <span className="block text-xs">{label}</span>
      <span className={cn('block truncate text-sm font-bold', day ? 'text-heading' : 'text-muted')}>{day ? formatDayLabel(day) : 'Choose'}</span>
    </div>
  )
}

/**
 * The inside of "Custom range". The first click in the month picks the first day, the next click the last day (a day
 * before the first one becomes the new first day instead). "Show sales" shows the period; "This month" and "Last
 * month" show theirs at once.
 */
function RangeCard({ first, last, onApply }: { first: string; last: string; onApply: (from: string, to: string) => void }) {
  const today = indiaDay()
  const { year, month } = partsOf(today)
  const [from, setFrom] = useState(first)
  const [to, setTo] = useState(last)
  // true while the next click picks the last day
  const choosingLast = from !== '' && to === ''
  const dayCount = from && to ? daysBetween(from, to).length : 0
  // The calendar allows no day after today and no last day before the first one; only "more than 366 days" is left
  const problem = dayCount > 0 ? periodProblem(from, to) : null

  function choose(day: string) {
    if (choosingLast && day >= from) {
      setTo(day)
    } else {
      setFrom(day)
      setTo('')
    }
  }

  return (
    <>
      <div aria-live="polite" className="mb-3 grid grid-cols-2 gap-2">
        <RangeEnd label="First day" day={from} next={from === ''} />
        <RangeEnd label="Last day" day={to} next={choosingLast} />
      </div>
      <Calendar value={to || from} range={{ from, to }} onChoose={choose} today={today} max={today} />
      <div className="mt-2 flex gap-1 border-t border-line pt-2">
        <button type="button" onClick={() => onApply(dayOf(year, month, 1), today)} className={SMALL_BUTTON}>
          This month
        </button>
        <button type="button" onClick={() => onApply(dayOf(year, month - 1, 1), dayOf(year, month, 0))} className={SMALL_BUTTON}>
          Last month
        </button>
      </div>
      {problem && (
        <p role="alert" className="mt-2 text-sm text-hot">
          {problem}
        </p>
      )}
      <div className="mt-2 flex items-center justify-between gap-2">
        <span className="text-xs">{dayCount > 0 ? `${dayCount} ${dayCount === 1 ? 'day' : 'days'}` : 'Choose the first and the last day'}</span>
        <Button size="sm" disabled={dayCount === 0 || problem !== null} onClick={() => onApply(from, to)}>
          Show sales
        </Button>
      </div>
    </>
  )
}

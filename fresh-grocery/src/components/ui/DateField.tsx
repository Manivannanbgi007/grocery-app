import { CalendarDays } from 'lucide-react'
import { useId, useRef, useState, type FocusEvent, type ReactNode } from 'react'
import { useDismiss } from '../../hooks/useDismiss'
import { usePopoverPlacement } from '../../hooks/usePopoverPlacement'
import { clampDay, formatDayLabel } from '../../utils/calendar'
import { cn } from '../../utils/cn'
import { indiaDay } from '../../utils/dates'
import { Calendar } from './Calendar'

interface DateFieldProps {
  label: string
  /** the chosen day as YYYY-MM-DD (the forms' and the API's format), or '' */
  value: string
  onChange: (day: string) => void
  min?: string
  max?: string
  /** shown while no day is chosen */
  placeholder?: string
  /** an optional field: the calendar offers "Clear" */
  clearable?: boolean
  /** shown in red under the field; also marks it invalid for screen readers */
  error?: string
  hint?: ReactNode
  /** md: forms (48 px, like TextField); sm: filter bars (40 px) */
  size?: 'sm' | 'md'
  /** the label only for screen readers, e.g. in a filter bar */
  hideLabel?: boolean
  disabled?: boolean
  className?: string
}

const FOOTER_BUTTON = 'rounded-full px-3 py-1.5 font-heading text-sm font-bold text-brand-text transition-colors hover:bg-brand-light disabled:cursor-not-allowed disabled:opacity-40'

/**
 * Our date picker, instead of the browser's: a field with the date written out ("17 May 1994") that opens a calendar
 * card under it (or above it). The focus goes into the calendar; choosing a day, or Escape, brings it back to the field.
 */
export function DateField({
  label,
  value,
  onChange,
  min,
  max,
  placeholder = 'Choose a date',
  clearable = false,
  error,
  hint,
  size = 'md',
  hideLabel = false,
  disabled = false,
  className,
}: DateFieldProps) {
  const id = useId()
  const labelId = `${id}-label`
  const buttonId = `${id}-button`
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined
  const rootRef = useRef<HTMLDivElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(false)
  const placement = usePopoverPlacement(rootRef, panelRef, open)
  const today = indiaDay()

  function close(focusField: boolean) {
    setOpen(false)
    if (focusField) buttonRef.current?.focus()
  }
  useDismiss(rootRef, open, (reason) => close(reason === 'escape'))

  function choose(day: string) {
    if (day !== value) onChange(day)
    close(true)
  }

  // Tabbing out of the calendar closes it (a click elsewhere is handled by useDismiss)
  function handleBlur(event: FocusEvent<HTMLDivElement>) {
    if (event.relatedTarget && !rootRef.current?.contains(event.relatedTarget)) setOpen(false)
  }

  return (
    <div className={className}>
      <span id={labelId} className={hideLabel ? 'sr-only' : 'mb-1.5 block font-heading text-sm font-bold text-heading'}>
        {label}
      </span>
      <div ref={rootRef} onBlur={handleBlur} className="relative">
        <button
          ref={buttonRef}
          id={buttonId}
          type="button"
          aria-haspopup="dialog"
          aria-expanded={open}
          // The name is the label and the date: "Date of birth (optional) 17 May 1994"
          aria-labelledby={`${labelId} ${buttonId}`}
          aria-describedby={describedBy}
          aria-invalid={error ? true : undefined}
          disabled={disabled}
          onClick={() => setOpen(!open)}
          className={cn(
            'flex w-full items-center gap-2 rounded-lg border bg-card text-left text-heading outline-none transition-[border-color,box-shadow] hover:border-brand-soft focus-visible:border-brand focus-visible:ring-4 focus-visible:ring-brand/15 disabled:cursor-not-allowed disabled:opacity-60',
            size === 'md' ? 'h-12 px-3.5' : 'h-10 px-3 text-sm',
            error ? 'border-hot' : open ? 'border-brand ring-4 ring-brand/15' : 'border-line',
          )}
        >
          <CalendarDays className="size-4 shrink-0 text-body" aria-hidden="true" />
          <span className={cn('min-w-0 flex-1 truncate', !value && 'text-muted')}>{value ? formatDayLabel(value) : placeholder}</span>
        </button>
        {open && (
          <div
            ref={panelRef}
            role="dialog"
            aria-label={`${label}: choose a date`}
            className={cn(
              'absolute z-50 w-[19.5rem] max-w-[calc(100vw-2rem)] rounded-2xl border border-line bg-card p-3 shadow-xl shadow-heading/10',
              'transition-[opacity,scale] duration-150 starting:scale-95 starting:opacity-0 motion-reduce:transition-none',
              placement.up ? 'bottom-full mb-1.5 origin-bottom' : 'top-full mt-1.5 origin-top',
              placement.alignRight ? 'right-0' : 'left-0',
            )}
          >
            <Calendar value={value} onChoose={choose} today={today} min={min} max={max} />
            <div className="mt-2 flex items-center justify-between border-t border-line pt-2">
              <button type="button" onClick={() => choose(today)} disabled={clampDay(today, min, max) !== today} className={FOOTER_BUTTON}>
                Today
              </button>
              {clearable && value && (
                <button type="button" onClick={() => choose('')} className={FOOTER_BUTTON}>
                  Clear
                </button>
              )}
            </div>
          </div>
        )}
      </div>
      {hint && (
        <div id={hintId} className="mt-1 text-xs">
          {hint}
        </div>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-sm text-hot">
          {error}
        </p>
      )}
    </div>
  )
}

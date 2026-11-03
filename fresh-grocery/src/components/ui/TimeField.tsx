import { Clock } from 'lucide-react'
import { useId } from 'react'
import { formatClockTime, timesOfDay } from '../../utils/calendar'
import { Select } from './Select'

interface TimeFieldProps {
  label: string
  /** HH:MM (24 hours, as the API uses it), or '' */
  value: string
  onChange: (time: string) => void
  /** minutes between the times offered */
  step?: number
  placeholder?: string
  /** marks the field red, e.g. when its row shows an error */
  invalid?: boolean
  /** md: forms (48 px, like TextField); sm: rows and filter bars (40 px) */
  size?: 'sm' | 'md'
  /** the label only for screen readers, e.g. "Monday opens at" in a row that says it already */
  hideLabel?: boolean
  disabled?: boolean
  className?: string
}

/**
 * Our time picker, instead of the browser's: a drop-down (Select) with a clock and the times of the day, written
 * "7:00 am". Typing "7" jumps to 7:00 am. A saved time between the steps (e.g. 7:10) stays in the list.
 */
export function TimeField({ label, value, onChange, step = 15, placeholder = 'Choose a time', invalid = false, size = 'md', hideLabel = false, disabled = false, className }: TimeFieldProps) {
  const labelId = `${useId()}-label`
  const options = timesOfDay(step, value).map((time) => ({ value: time, label: formatClockTime(time) }))

  return (
    <div className={className}>
      <span id={labelId} className={hideLabel ? 'sr-only' : 'mb-1.5 block font-heading text-sm font-bold text-heading'}>
        {label}
      </span>
      <Select icon={Clock} labelledBy={labelId} value={value} onChange={onChange} options={options} placeholder={placeholder} invalid={invalid} size={size} disabled={disabled} />
    </div>
  )
}

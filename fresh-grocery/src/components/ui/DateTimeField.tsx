import { useId } from 'react'
import { DateField } from './DateField'
import { TimeField } from './TimeField'

interface DateTimeFieldProps {
  label: string
  /** YYYY-MM-DDTHH:MM (as the old datetime-local field gave it), or '' */
  value: string
  onChange: (value: string) => void
  /** shown in red under the field */
  error?: string
  className?: string
}

/** The time a day starts with, until another one is chosen. */
const START_OF_DAY = '00:00'

/**
 * A day and a time side by side, e.g. when a banner is shown from. The time can be chosen once there is a day; clearing
 * the day empties both.
 */
export function DateTimeField({ label, value, onChange, error, className }: DateTimeFieldProps) {
  const id = useId()
  const [day = '', time = ''] = value ? value.split('T') : []

  return (
    <div role="group" aria-labelledby={`${id}-label`} className={className}>
      <span id={`${id}-label`} className="mb-1.5 block font-heading text-sm font-bold text-heading">
        {label}
      </span>
      <div className="flex gap-2">
        <DateField label={`${label}, day`} hideLabel value={day} onChange={(chosen) => onChange(chosen ? `${chosen}T${time || START_OF_DAY}` : '')} clearable className="min-w-0 flex-1" />
        <TimeField label={`${label}, time`} hideLabel value={time} onChange={(chosen) => onChange(`${day}T${chosen}`)} disabled={!day} placeholder="Time" className="w-36 shrink-0" />
      </div>
      {error && <p className="mt-1 text-sm text-hot">{error}</p>}
    </div>
  )
}

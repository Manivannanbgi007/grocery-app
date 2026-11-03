import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { DayOfWeek, OpeningHours } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { TimeField } from '../../../components/ui/TimeField'
import { DAY_NAMES, hhmm } from '../../../utils/openingHours'
import { useStoreChanges } from '../useVendorStore'

interface DayValues {
  dayOfWeek: DayOfWeek
  open: boolean
  /** "07:00" */
  opensAt: string
  closesAt: string
}

function daysOf(openingHours: OpeningHours[]): DayValues[] {
  return openingHours.map((day) => ({ dayOfWeek: day.dayOfWeek, open: !day.closed, opensAt: hhmm(day.opensAt) || '08:00', closesAt: hhmm(day.closesAt) || '21:00' }))
}

/** The same rules as store-service's OpeningHoursValidator, as one message per day (or none). */
function dayError(day: DayValues): string | undefined {
  if (!day.open) return undefined
  if (!day.opensAt || !day.closesAt) return 'Enter both times, or mark the day as closed.'
  if (day.opensAt >= day.closesAt) return 'The store must open before it closes.'
  return undefined
}

/** The weekly opening hours: open or closed per day, and from when to when. Saving replaces the whole week. */
export function OpeningHoursForm({ openingHours }: { openingHours: OpeningHours[] }) {
  const { openingHours: save } = useStoreChanges()
  const [days, setDays] = useState(() => daysOf(openingHours))
  const [showErrors, setShowErrors] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  function change(dayOfWeek: DayOfWeek, values: Partial<DayValues>) {
    setDays((current) => current.map((day) => (day.dayOfWeek === dayOfWeek ? { ...day, ...values } : day)))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setShowErrors(true)
    setFormError(null)
    if (days.some(dayError)) return
    save.mutate(
      days.map((day) => ({ dayOfWeek: day.dayOfWeek, opensAt: day.open ? day.opensAt : null, closesAt: day.open ? day.closesAt : null, closed: !day.open })),
      {
        onSuccess: () => toast.success('Opening hours saved'),
        onError: (error) => setFormError(isApiError(error, 401) ? null : error.message),
      },
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <ul className="divide-y divide-line">
        {days.map((day) => {
          const error = showErrors ? dayError(day) : undefined
          const name = DAY_NAMES[day.dayOfWeek]
          return (
            <li key={day.dayOfWeek} className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3">
              <label className="flex w-36 items-center gap-2 font-heading font-bold text-heading">
                <input type="checkbox" checked={day.open} onChange={(event) => change(day.dayOfWeek, { open: event.target.checked })} className="size-4 accent-brand" aria-label={`Open on ${name}`} />
                {name}
              </label>
              {day.open ? (
                <span className="flex items-center gap-2 text-sm">
                  <TimeField label={`${name} opens at`} hideLabel size="sm" value={day.opensAt} onChange={(opensAt) => change(day.dayOfWeek, { opensAt })} invalid={Boolean(error)} className="w-36" />
                  to
                  <TimeField label={`${name} closes at`} hideLabel size="sm" value={day.closesAt} onChange={(closesAt) => change(day.dayOfWeek, { closesAt })} invalid={Boolean(error)} className="w-36" />
                </span>
              ) : (
                <span className="text-sm">Closed all day</span>
              )}
              {error && <p className="w-full text-sm text-hot">{error}</p>}
            </li>
          )
        })}
      </ul>
      <Button type="submit" loading={save.isPending}>
        Save opening hours
      </Button>
    </form>
  )
}

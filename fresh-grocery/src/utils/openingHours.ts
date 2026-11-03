import type { DayOfWeek, OpeningHours } from '../api/types'

export const DAY_NAMES: Record<DayOfWeek, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
}

/** "07:00:00" -> "07:00" (what a time box shows) */
export const hhmm = (time: string | null) => (time ? time.slice(0, 5) : '')

const WEEKDAY = new Intl.DateTimeFormat('en-US', { weekday: 'long', timeZone: 'Asia/Kolkata' })

/** Today in India, e.g. "MONDAY": the stores' opening hours are India times. */
export function indiaWeekday(moment: number | Date = Date.now()): DayOfWeek {
  return WEEKDAY.format(moment).toUpperCase() as DayOfWeek
}

/** "07:00–22:00", or "Closed" */
export function formatHours(day: OpeningHours): string {
  if (day.closed || !day.opensAt || !day.closesAt) return 'Closed'
  return `${hhmm(day.opensAt)}–${hhmm(day.closesAt)}`
}

/*
 * Days and times for the date and time pickers. A day is a 'YYYY-MM-DD' string and a time 'HH:MM', as the forms and the
 * API use them. Days are counted in UTC, so no time zone can shift them by one.
 */

export const MONTH_NAMES = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

/** Sunday first, as in Indian calendars. */
export const WEEKDAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

const DAY_MS = 24 * 60 * 60 * 1000

/** The day `day` of `month` (0 = January) in `year`; numbers past the end roll over (32 January = 1 February). */
export function dayOf(year: number, month: number, day: number): string {
  return new Date(Date.UTC(year, month, day)).toISOString().slice(0, 10)
}

export function partsOf(day: string): { year: number; month: number; day: number } {
  const [year, month, date] = day.split('-').map(Number)
  return { year, month: month - 1, day: date }
}

export const isDay = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value)

export function addDays(day: string, days: number): string {
  return new Date(Date.parse(day) + days * DAY_MS).toISOString().slice(0, 10)
}

/** The same day `months` later; 31 January + 1 month = 28 (or 29) February. */
export function addMonths(day: string, months: number): string {
  const { year, month, day: date } = partsOf(day)
  const lastDay = new Date(Date.UTC(year, month + months + 1, 0)).getUTCDate()
  return dayOf(year, month + months, Math.min(date, lastDay))
}

/** 0 = Sunday ... 6 = Saturday */
export const weekdayOf = (day: string) => new Date(Date.parse(day)).getUTCDay()

/** `day`, but not before `min` and not after `max` (when given). */
export function clampDay(day: string, min?: string, max?: string): string {
  if (min && day < min) return min
  if (max && day > max) return max
  return day
}

/** The weeks shown for a month: Sunday to Saturday, with the days of the months before and after to fill them. */
export function monthWeeks(year: number, month: number): string[][] {
  const first = dayOf(year, month, 1)
  const last = dayOf(year, month + 1, 0)
  const weeks: string[][] = []
  // Days compare as text: '2026-10-31' < '2026-11-01'
  for (let sunday = addDays(first, -weekdayOf(first)); sunday <= last; sunday = addDays(sunday, 7)) {
    weeks.push(Array.from({ length: 7 }, (_, index) => addDays(sunday, index)))
  }
  return weeks
}

/** "17 May 1994" */
export function formatDayLabel(day: string): string {
  const { year, month, day: date } = partsOf(day)
  return `${date} ${MONTH_NAMES[month].slice(0, 3)} ${year}`
}

/** "Tuesday, 17 May 1994": a day button's name for screen readers. */
export function formatFullDay(day: string): string {
  const { year, month, day: date } = partsOf(day)
  return `${WEEKDAY_NAMES[weekdayOf(day)]}, ${date} ${MONTH_NAMES[month]} ${year}`
}

/** "07:00" -> "7:00 am", "00:30" -> "12:30 am", "20:30" -> "8:30 pm" */
export function formatClockTime(time: string): string {
  const [hours, minutes] = time.split(':').map(Number)
  return `${hours % 12 || 12}:${String(minutes).padStart(2, '0')} ${hours < 12 ? 'am' : 'pm'}`
}

export const isTime = (value: string) => /^([01]\d|2[0-3]):[0-5]\d$/.test(value)

/** The times of a day every `stepMinutes` ("00:00", "00:15" ...), with `keep` added in its place when it is between them. */
export function timesOfDay(stepMinutes: number, keep?: string): string[] {
  const times = Array.from({ length: (24 * 60) / stepMinutes }, (_, index) => {
    const minutes = index * stepMinutes
    return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
  })
  if (keep && isTime(keep) && !times.includes(keep)) times.push(keep)
  return times.sort()
}

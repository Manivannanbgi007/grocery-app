/*
 * Calendar days in India time, the shop's time zone (the backend's reports and the coupon dates use it too).
 * A day is written "2026-10-26" (ISO date), the format of <input type="date"> and of the report API.
 */

const INDIA = 'Asia/Kolkata'
const isoDay = new Intl.DateTimeFormat('en-CA', { timeZone: INDIA, year: 'numeric', month: '2-digit', day: '2-digit' })
const DAY_MS = 24 * 60 * 60 * 1000

/** The day (India time) of a moment: "2026-10-26T20:00:00Z" -> "2026-10-27" (01:30 in India). */
export function indiaDay(moment: string | number | Date = Date.now()): string {
  return isoDay.format(new Date(moment))
}

/** The day `days` days before (or after, when negative) another one: ("2026-10-26", 13) -> "2026-10-13". */
export function daysBefore(day: string, days: number): string {
  return new Date(Date.parse(`${day}T00:00:00Z`) - days * DAY_MS).toISOString().slice(0, 10)
}

/** Every day from `from` to `to`, both included. */
export function daysBetween(from: string, to: string): string[] {
  const days: string[] = []
  for (let day = from; day <= to; day = daysBefore(day, -1)) days.push(day)
  return days
}

/** The first moment of a day in India: "2026-10-26" -> "2026-10-25T18:30:00.000Z". */
export function startOfIndiaDay(day: string): string {
  return new Date(`${day}T00:00:00+05:30`).toISOString()
}

/** The last second of a day in India: "2026-10-26" -> "2026-10-26T18:29:59.000Z". */
export function endOfIndiaDay(day: string): string {
  return new Date(`${day}T23:59:59+05:30`).toISOString()
}

/** India is 5 h 30 min ahead of UTC all year (no summer time). */
const INDIA_OFFSET_MS = (5 * 60 + 30) * 60 * 1000

/** A time in India from an <input type="datetime-local">: "2026-11-01T09:00" -> "2026-11-01T03:30:00.000Z". */
export function indiaDateTimeToIso(value: string): string {
  return new Date(`${value}:00+05:30`).toISOString()
}

/** The other way, for the box: "2026-11-01T03:30:00Z" -> "2026-11-01T09:00". */
export function isoToIndiaDateTime(moment: string): string {
  return new Date(Date.parse(moment) + INDIA_OFFSET_MS).toISOString().slice(0, 16)
}

const longDay = new Intl.DateTimeFormat('en-IN', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })

/** "2026-10-27" -> "Tuesday, 27 October" (e.g. today on the admin dashboard). */
export function formatLongDay(day: string): string {
  return longDay.format(new Date(`${day}T00:00:00Z`))
}

const shortDay = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', timeZone: 'UTC' })

/** "2026-10-26" -> "26 Oct" (for chart axes). */
export function formatShortDay(day: string): string {
  return shortDay.format(new Date(`${day}T00:00:00Z`))
}

const weekdayDay = new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' })

/** "2026-10-30" -> "Fri, 30 Oct" (a chart's tooltip, a table of days). */
export function formatWeekdayDay(day: string): string {
  return weekdayDay.format(new Date(`${day}T00:00:00Z`))
}

const shortMonth = new Intl.DateTimeFormat('en-IN', { month: 'short', year: 'numeric', timeZone: 'UTC' })

/** "2026-10" -> "Oct 2026" (for monthly charts). */
export function formatMonth(month: string): string {
  return shortMonth.format(new Date(`${month}-01T00:00:00Z`))
}

/** The last `count` months up to the month of `day`, oldest first: ("2026-10-27", 3) -> ["2026-08", "2026-09", "2026-10"]. */
export function lastMonths(day: string, count: number): string[] {
  const [year, month] = day.split('-').map(Number)
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(Date.UTC(year, month - 1 - (count - 1 - index), 1))
    return date.toISOString().slice(0, 7)
  })
}

const clockTime = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: INDIA })
const MINUTE_MS = 60 * 1000

/**
 * How long ago something happened, in a few words (India time), for the notifications:
 *   under a minute "Just now", "5 min ago", "3 h ago" (today), "Yesterday, 3:45 pm", then "26 Oct, 3:45 pm".
 * The exact date and time belongs next to it in a title or <time> attribute.
 */
export function formatRelative(moment: string, now: number = Date.now()): string {
  const ago = now - Date.parse(moment)
  if (ago < MINUTE_MS) return 'Just now'
  if (ago < 60 * MINUTE_MS) return `${Math.floor(ago / MINUTE_MS)} min ago`
  const day = indiaDay(moment)
  const today = indiaDay(now)
  if (day === today) return `${Math.floor(ago / (60 * MINUTE_MS))} h ago`
  const time = clockTime.format(new Date(moment))
  if (day === daysBefore(today, 1)) return `Yesterday, ${time}`
  return `${formatShortDay(day)}, ${time}`
}

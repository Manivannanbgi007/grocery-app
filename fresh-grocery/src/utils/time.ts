/** What is left until a moment, split into parts for a countdown. */
export interface TimeLeft {
  days: number
  hours: number
  minutes: number
  seconds: number
  /** true once the moment has passed (all parts are then 0) */
  ended: boolean
}

const SECOND = 1000
const MINUTE = 60 * SECOND
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** timeLeft("2026-10-27T00:00:00Z", now) -> { days: 0, hours: 5, minutes: 12, seconds: 9, ended: false } */
export function timeLeft(until: string, now: number): TimeLeft {
  const remaining = new Date(until).getTime() - now
  if (!(remaining > 0)) return { days: 0, hours: 0, minutes: 0, seconds: 0, ended: true }
  return {
    days: Math.floor(remaining / DAY),
    hours: Math.floor((remaining % DAY) / HOUR),
    minutes: Math.floor((remaining % HOUR) / MINUTE),
    seconds: Math.floor((remaining % MINUTE) / SECOND),
    ended: false,
  }
}

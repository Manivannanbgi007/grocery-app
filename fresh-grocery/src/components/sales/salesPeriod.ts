import { formatDayLabel } from '../../utils/calendar'
import { cn } from '../../utils/cn'
import { daysBefore, formatShortDay, indiaDay } from '../../utils/dates'
import { periodProblem } from './reportPeriod'

/** The periods a sales section offers (days, ending today). */
export const SALES_PERIODS = [7, 14, 30]
export const DEFAULT_SALES_PERIOD = 14

/** What "sales" means in every report (the report-service rule), shown under the numbers. */
export const SALES_DEFINITION =
  'Sales are the orders that were confirmed and not cancelled, rejected or undelivered, by the day they were placed (India time). New orders show up a moment later.'

/** The period in the URL (?days=30), or the default one; with the first and last day ("2026-10-26", India dates). */
export function salesPeriodOf(searchParams: URLSearchParams): { days: number; from: string; to: string } {
  const requested = Number(searchParams.get('days'))
  const days = SALES_PERIODS.includes(requested) ? requested : DEFAULT_SALES_PERIOD
  const to = indiaDay()
  return { days, from: daysBefore(to, days - 1), to }
}

/** A period chosen with the tabs (the last 7, 14 or 30 days) or with a calendar (one day, or any days in a row). */
export interface ChosenPeriod {
  /** 'last': one of the tabs; 'day': one day; 'range': a first and a last day */
  kind: 'last' | 'day' | 'range'
  /** the tab's number of days (only for 'last') */
  days: number | null
  from: string
  to: string
  /** why the dates in the URL cannot be shown (e.g. the first day is after the last), or null */
  problem: string | null
}

/**
 * The period in the URL: ?from=2026-10-01&to=2026-10-20 when it was chosen in a calendar (one day when both are the
 * same), otherwise ?days=30 or the default tab.
 */
export function chosenPeriodOf(searchParams: URLSearchParams): ChosenPeriod {
  const from = searchParams.get('from')
  const to = searchParams.get('to')
  if (from === null && to === null) return { kind: 'last', ...salesPeriodOf(searchParams), problem: null }
  return { kind: from === to ? 'day' : 'range', days: null, from: from ?? '', to: to ?? '', problem: periodProblem(from ?? '', to ?? '') }
}

/** A period for people: "20 Oct 2026", "12 Oct – 18 Oct 2026" or "28 Dec 2025 – 3 Jan 2026". */
export function periodLabel(from: string, to: string): string {
  if (from === to) return formatDayLabel(from)
  const sameYear = from.slice(0, 4) === to.slice(0, 4)
  return `${sameYear ? formatShortDay(from) : formatDayLabel(from)} – ${formatDayLabel(to)}`
}

/** The look of one choice in the period row (a tab or a calendar button): the chosen one is filled green, so it stands out. */
export function periodTabClass(chosen: boolean): string {
  return cn(
    'rounded-lg px-3 py-1.5 text-sm font-bold transition-colors',
    chosen ? 'bg-brand text-white shadow-sm shadow-brand/30' : 'text-heading hover:bg-brand-light hover:text-brand-text',
  )
}

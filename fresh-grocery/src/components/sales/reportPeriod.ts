import { daysBefore, daysBetween, indiaDay } from '../../utils/dates'

/*
 * A report period of any length chosen with two dates (?from=2026-10-01&to=2026-10-26), for the admin's reports and
 * the super admin's commission report. report-service answers for at most 366 days.
 */

export const MAX_REPORT_DAYS = 366
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/

/** Why a period cannot be shown, or null (the report-service rules: from before to, at most 366 days, not in the future). */
export function periodProblem(from: string, to: string): string | null {
  if (!ISO_DAY.test(from) || !ISO_DAY.test(to)) return 'Choose both days.'
  if (from > to) return 'The first day must not be after the last day.'
  if (to > indiaDay()) return 'The last day cannot be in the future.'
  if (daysBetween(from, to).length > MAX_REPORT_DAYS) return `A report covers at most ${MAX_REPORT_DAYS} days.`
  return null
}

/** The period in the address (the last `defaultDays` days up to today when it has none), and why it cannot be shown. */
export function reportPeriodOf(fromParam: string, toParam: string, defaultDays: number): { from: string; to: string; problem: string | null } {
  const today = indiaDay()
  const from = fromParam || daysBefore(today, defaultDays - 1)
  const to = toParam || today
  return { from, to, problem: periodProblem(from, to) }
}

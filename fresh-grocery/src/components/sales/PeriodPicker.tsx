import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { DEFAULT_SALES_PERIOD, periodTabClass, SALES_PERIODS } from './salesPeriod'

interface PeriodPickerProps {
  /** the page the links lead to, e.g. "/vendor" (the period is added as ?days=) */
  path: string
  /** the period shown now; null when no tab is chosen (e.g. a day picked in a calendar) */
  days: number | null
  /** builds a link yourself, e.g. to keep a list's filters (useUrlFilters().linkWith) */
  linkFor?: (days: number) => string
  /** more choices at the end of the row, e.g. the calendars of CustomPeriodPickers */
  children?: ReactNode
}

/** "7 days · 14 days · 30 days": one row above everything the period changes. */
export function PeriodPicker({ path, days, linkFor, children }: PeriodPickerProps) {
  const link = (period: number) => (linkFor ? linkFor(period) : period === DEFAULT_SALES_PERIOD ? path : `${path}?days=${period}`)
  return (
    <nav aria-label="Period" className="flex flex-wrap gap-1 rounded-xl border border-line bg-card p-1 shadow-sm shadow-heading/5">
      {SALES_PERIODS.map((period) => (
        <Link key={period} to={link(period)} aria-current={period === days ? 'page' : undefined} preventScrollReset className={periodTabClass(period === days)}>
          {period} days
        </Link>
      ))}
      {children}
    </nav>
  )
}

import type { LucideIcon } from 'lucide-react'
import { KpiTile, type KpiAccent } from './KpiTile'
import { useCountUp } from '../../hooks/useCountUp'

interface StatCardProps {
  label: string
  /** the number; it counts up when the card appears (null: not known yet, shown as "–") */
  value: number | null
  /** how the number is written, e.g. formatPrice (whole numbers with Indian grouping by default) */
  format?: (value: number) => string
  /** a short explanation under the number */
  hint?: string
  icon: LucideIcon
  accent: KpiAccent
  /** the page with the details: the whole card is then a link */
  to?: string
  /** e.g. "col-span-2" in a grid */
  className?: string
}

const wholeNumber = (value: number) => value.toLocaleString('en-IN')

/** A KpiTile whose number counts up when it appears, e.g. "Sales today ₹12,400" on the admin dashboard. */
export function StatCard({ label, value, format = wholeNumber, ...tile }: StatCardProps) {
  const shown = useCountUp(value ?? 0)
  // While counting: whole numbers only (no flickering paise); the exact value at the end
  const text = value === null ? '–' : format(shown === value ? value : Math.round(shown))
  return <KpiTile label={label} value={text} {...tile} />
}

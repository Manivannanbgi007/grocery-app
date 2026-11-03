/** One point of a chart over time: a day (or a month), its amount in rupees, and how many orders or deliveries made it. */
export interface ChartDay {
  /** "2026-10-26" (or "2026-10" for a month, with a formatLabel) */
  date: string
  value: number
  count: number
}

/** The top of a value scale: the biggest value rounded up to 1, 2 or 5 × 10ⁿ (₹860 -> ₹1,000), so the ticks are clean numbers. */
export function niceMax(value: number): number {
  if (value <= 0) return 100
  const step = 10 ** Math.floor(Math.log10(value))
  const nice = [1, 2, 5, 10].find((factor) => factor * step >= value)!
  return nice * step
}

/**
 * Every how many points an x-axis label fits: every one up to 10 points, then every second (up to 16) or fourth (up
 * to 31); a longer period (a custom range of up to a year) gets about 8 labels.
 */
export function labelStep(count: number): number {
  if (count > 31) return Math.ceil(count / 8)
  return count > 16 ? 4 : count > 10 ? 2 : 1
}

/** Where a tooltip sits over a point, so the first and last ones do not run off the chart. */
export function tooltipSide(index: number, count: number): string {
  if (index < 2) return 'left-0'
  if (index > count - 3) return 'right-0'
  return 'left-1/2 -translate-x-1/2'
}

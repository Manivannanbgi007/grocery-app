import type { SalesDay } from '../../api/types'

/** Rounds to paise, so 0.1 + 0.2 shows as ₹0.30 and not ₹0.30000000000000004. */
const roundMoney = (amount: number) => Math.round(amount * 100) / 100

/** Quick facts about a period's sales per day, shown above the chart ("Sales per day"). */
export interface SalesInsights {
  /** the sales per day on average, over every day of the period (also the days without sales) */
  dailyAverage: number
  /** the day with the most sales (the first one when several tie); null when no day had any */
  bestDay: SalesDay | null
  /** how many days had at least one sale */
  daysWithSales: number
  /** the last day of the period, and how much more (+) or less (-) it sold than the day before (null for one day) */
  lastDay: { day: SalesDay; change: number | null } | null
}

/** How much more (+) or less (-) the day at `index` sold than the day before it; null for the first day. */
export function changeFromDayBefore(days: SalesDay[], index: number): number | null {
  return index > 0 ? roundMoney(days[index].sales - days[index - 1].sales) : null
}

export function salesInsightsOf(days: SalesDay[]): SalesInsights {
  const total = days.reduce((sum, day) => sum + day.sales, 0)
  const bestDay = days.reduce<SalesDay | null>((best, day) => (day.sales > (best?.sales ?? 0) ? day : best), null)
  const last = days.length - 1
  return {
    dailyAverage: days.length > 0 ? roundMoney(total / days.length) : 0,
    bestDay,
    daysWithSales: days.filter((day) => day.salesOrders > 0).length,
    lastDay: last >= 0 ? { day: days[last], change: changeFromDayBefore(days, last) } : null,
  }
}

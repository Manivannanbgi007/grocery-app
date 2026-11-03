import { describe, expect, it } from 'vitest'
import { salesDay } from '../../test/sales'
import { changeFromDayBefore, salesInsightsOf } from './salesInsights'

const DAYS = [salesDay('2026-10-26', 0), salesDay('2026-10-27', 300, 2), salesDay('2026-10-28', 500, 3), salesDay('2026-10-29', 120)]

describe('salesInsightsOf', () => {
  it('finds the daily average, the best day, the days with sales and the last day with its change', () => {
    const insights = salesInsightsOf(DAYS)
    expect(insights.dailyAverage).toBe(230) // ₹920 over 4 days, the day without sales too
    expect(insights.bestDay?.date).toBe('2026-10-28')
    expect(insights.daysWithSales).toBe(3)
    expect(insights.lastDay).toEqual({ day: DAYS[3], change: -380 })
  })

  it('has no best day and no change when nothing was sold on a single day', () => {
    const insights = salesInsightsOf([salesDay('2026-10-26', 0)])
    expect(insights.bestDay).toBeNull()
    expect(insights.dailyAverage).toBe(0)
    expect(insights.lastDay?.change).toBeNull()
  })
})

describe('changeFromDayBefore', () => {
  it('is the difference to the day before, rounded to paise, and null for the first day', () => {
    expect(changeFromDayBefore(DAYS, 0)).toBeNull()
    expect(changeFromDayBefore(DAYS, 2)).toBe(200)
    // 0.3 - 0.1 is 0.19999999999999998 in JavaScript
    expect(changeFromDayBefore([salesDay('2026-10-26', 0.1), salesDay('2026-10-27', 0.3)], 1)).toBe(0.2)
  })
})

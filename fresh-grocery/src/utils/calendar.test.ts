import { describe, expect, it } from 'vitest'
import { addMonths, clampDay, formatClockTime, formatDayLabel, formatFullDay, monthWeeks, timesOfDay } from './calendar'

describe('calendar helpers', () => {
  it('lays a month out in weeks from Sunday, with the days around it', () => {
    const october = monthWeeks(2026, 9) // 1 October 2026 is a Thursday
    expect(october).toHaveLength(5)
    expect(october[0]).toEqual(['2026-09-27', '2026-09-28', '2026-09-29', '2026-09-30', '2026-10-01', '2026-10-02', '2026-10-03'])
    expect(october[4].at(-1)).toBe('2026-10-31')
    // February 2026 starts on a Sunday and fills exactly four weeks
    expect(monthWeeks(2026, 1)).toHaveLength(4)
  })

  it('moves by months without leaving the month, and keeps days within min and max', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28')
    expect(addMonths('2024-01-31', 1)).toBe('2024-02-29')
    expect(addMonths('2026-03-15', -12)).toBe('2025-03-15')
    expect(clampDay('2026-11-02', undefined, '2026-10-27')).toBe('2026-10-27')
    expect(clampDay('2026-10-01', '2026-10-05')).toBe('2026-10-05')
  })

  it('writes days and times for people', () => {
    expect(formatDayLabel('1994-05-17')).toBe('17 May 1994')
    expect(formatFullDay('2026-10-27')).toBe('Tuesday, 27 October 2026')
    expect(['00:00', '00:30', '07:05', '12:00', '20:30'].map(formatClockTime)).toEqual(['12:00 am', '12:30 am', '7:05 am', '12:00 pm', '8:30 pm'])
    expect(timesOfDay(30)).toHaveLength(48)
    expect(timesOfDay(30, '07:10').slice(14, 17)).toEqual(['07:00', '07:10', '07:30'])
  })
})

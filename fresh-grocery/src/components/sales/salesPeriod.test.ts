import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { chosenPeriodOf, periodLabel } from './salesPeriod'

describe('chosenPeriodOf', () => {
  // Today is 27 Oct 2026 (India) in these tests
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-27T06:30:00Z'))
  })
  afterEach(() => vi.useRealTimers())

  const periodOf = (query: string) => chosenPeriodOf(new URLSearchParams(query))

  it('reads a tab, one day or a range from the URL', () => {
    expect(periodOf('')).toEqual({ kind: 'last', days: 14, from: '2026-10-14', to: '2026-10-27', problem: null })
    expect(periodOf('days=7')).toEqual({ kind: 'last', days: 7, from: '2026-10-21', to: '2026-10-27', problem: null })
    expect(periodOf('from=2026-10-20&to=2026-10-20')).toEqual({ kind: 'day', days: null, from: '2026-10-20', to: '2026-10-20', problem: null })
    // Dates win over a tab
    expect(periodOf('days=7&from=2026-10-01&to=2026-10-20')).toEqual({ kind: 'range', days: null, from: '2026-10-01', to: '2026-10-20', problem: null })
  })

  it('says why dates cannot be shown', () => {
    expect(periodOf('from=2026-10-20&to=2026-10-01').problem).toBe('The first day must not be after the last day.')
    expect(periodOf('from=2026-10-20&to=2026-10-30').problem).toBe('The last day cannot be in the future.')
    expect(periodOf('from=2025-01-01&to=2026-10-20').problem).toBe('A report covers at most 366 days.')
    expect(periodOf('from=2026-10-20').problem).toBe('Choose both days.')
    expect(periodOf('from=yesterday&to=today').problem).toBe('Choose both days.')
  })
})

describe('periodLabel', () => {
  it('writes one day, days in one year and days over New Year', () => {
    expect(periodLabel('2026-10-20', '2026-10-20')).toBe('20 Oct 2026')
    expect(periodLabel('2026-10-12', '2026-10-18')).toBe('12 Oct – 18 Oct 2026')
    expect(periodLabel('2025-12-28', '2026-01-03')).toBe('28 Dec 2025 – 3 Jan 2026')
  })
})

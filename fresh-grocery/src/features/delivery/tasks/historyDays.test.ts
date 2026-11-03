import { describe, expect, it } from 'vitest'
import type { Delivery } from '../../../api/types'
import { dayTitle, groupByDay, historyDayOf, roadTime } from './historyDays'

/** A finished delivery with only the fields the history's days use. */
function delivery(id: number, deliveredAt: string | null, createdAt = '2026-10-28T05:00:00Z'): Delivery {
  return { id, deliveredAt, createdAt } as Delivery
}

describe('History days', () => {
  it('puts a delivery on the day it was delivered in India, else on the day it was created', () => {
    // 20:00 UTC is 01:30 the next morning in India
    expect(historyDayOf(delivery(1, '2026-10-29T20:00:00Z'))).toBe('2026-10-30')
    expect(historyDayOf(delivery(2, null, '2026-10-28T05:00:00Z'))).toBe('2026-10-28')
  })

  it('groups the deliveries by day, newest day first, keeping their order', () => {
    const days = groupByDay([delivery(3, '2026-10-30T10:00:00Z'), delivery(2, '2026-10-30T08:00:00Z'), delivery(1, '2026-10-28T09:00:00Z')])
    expect(days.map((day) => [day.day, day.deliveries.map((item) => item.id)])).toEqual([
      ['2026-10-30', [3, 2]],
      ['2026-10-28', [1]],
    ])
    expect(groupByDay([])).toEqual([])
  })

  it('calls the days Today, Yesterday, or by their date', () => {
    expect(dayTitle('2026-10-31', '2026-10-31')).toBe('Today')
    expect(dayTitle('2026-10-30', '2026-10-31')).toBe('Yesterday')
    expect(dayTitle('2026-10-27', '2026-10-31')).toBe('Tuesday, 27 October')
  })

  it('tells how long the order was on the road', () => {
    expect(roadTime({ pickedUpAt: '2026-10-30T10:00:00Z', deliveredAt: '2026-10-30T10:12:20Z' })).toBe('12 min')
    expect(roadTime({ pickedUpAt: '2026-10-30T10:00:00Z', deliveredAt: '2026-10-30T10:00:10Z' })).toBe('1 min')
    expect(roadTime({ pickedUpAt: '2026-10-30T10:00:00Z', deliveredAt: '2026-10-30T11:05:00Z' })).toBe('1 h 5 min')
    expect(roadTime({ pickedUpAt: '2026-10-30T10:00:00Z', deliveredAt: '2026-10-30T12:00:00Z' })).toBe('2 h')
    expect(roadTime({ pickedUpAt: null, deliveredAt: '2026-10-30T12:00:00Z' })).toBeNull()
  })
})

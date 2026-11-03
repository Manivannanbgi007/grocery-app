import { describe, expect, it } from 'vitest'
import { monthlySpending } from '../features/orders/spending'
import type { OrderSummary } from '../api/types'
import { formatMonth, formatRelative, lastMonths } from './dates'
import { initialsOf } from './format'
import { rowRange } from './pagination'

/** 27 Oct 2026, 10:00 in India. */
const NOW = Date.parse('2026-10-27T04:30:00Z')

describe('formatRelative', () => {
  it('says how long ago, in India time', () => {
    expect(formatRelative('2026-10-27T04:29:40Z', NOW)).toBe('Just now')
    expect(formatRelative('2026-10-27T04:25:00Z', NOW)).toBe('5 min ago')
    expect(formatRelative('2026-10-27T01:30:00Z', NOW)).toBe('3 h ago')
    // 26 Oct, 3:45 pm in India
    expect(formatRelative('2026-10-26T10:15:00Z', NOW)).toBe('Yesterday, 3:45 pm')
    expect(formatRelative('2026-10-24T10:15:00Z', NOW)).toBe('24 Oct, 3:45 pm')
  })
})

describe('months', () => {
  it('lists the last months, also across a new year', () => {
    expect(lastMonths('2026-10-27', 3)).toEqual(['2026-08', '2026-09', '2026-10'])
    expect(lastMonths('2027-01-05', 3)).toEqual(['2026-11', '2026-12', '2027-01'])
    expect(formatMonth('2026-10')).toBe('Oct 2026')
  })

  it('adds up what a customer spent per month, without cancelled or undelivered orders', () => {
    const order = (status: OrderSummary['status'], totalAmount: number, placedAt: string): OrderSummary => ({
      id: 1, customerUserId: 4, storeId: 1, storeName: 'Fresh Mart', status, paymentMethod: 'COD', paymentStatus: 'CASH_ON_DELIVERY',
      itemCount: 1, totalAmount, placedAt,
    })
    const months = monthlySpending(
      [
        order('DELIVERED', 177, '2026-10-24T05:35:00Z'),
        order('CONFIRMED', 50.5, '2026-10-26T05:35:00Z'),
        order('CANCELLED', 192, '2026-10-25T05:35:00Z'),
        // 1 Sep, 01:00 in India: still September there, although it is 31 Aug in UTC
        order('DELIVERED', 80, '2026-08-31T19:30:00Z'),
      ],
      3,
      '2026-10-27',
    )
    expect(months).toEqual([
      { date: '2026-08', value: 0, count: 0 },
      { date: '2026-09', value: 80, count: 1 },
      { date: '2026-10', value: 227.5, count: 2 },
    ])
  })
})

describe('small helpers', () => {
  it('works out the rows of a page and the letters of an avatar', () => {
    expect(rowRange(3, 20, 45)).toEqual({ first: 41, last: 45 })
    expect(initialsOf('Priya Sharma')).toBe('PS')
    expect(initialsOf('Platform Admin Team')).toBe('PA')
  })
})

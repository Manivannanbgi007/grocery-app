import { describe, expect, it } from 'vitest'
import type { TrackingRoute } from '../../api/types'
import { arrivalText, orderStepOf } from './trackingTexts'

describe('orderStepOf', () => {
  it('moves the order on as soon as the delivery does', () => {
    expect(orderStepOf('READY_FOR_PICKUP', 'ACCEPTED')).toBe('READY_FOR_PICKUP')
    expect(orderStepOf('READY_FOR_PICKUP', 'PICKED_UP')).toBe('OUT_FOR_DELIVERY')
    expect(orderStepOf('READY_FOR_PICKUP', 'OUT_FOR_DELIVERY')).toBe('OUT_FOR_DELIVERY')
    expect(orderStepOf('OUT_FOR_DELIVERY', 'DELIVERED')).toBe('DELIVERED')
  })

  it('keeps an order that has moved on or stopped as it is', () => {
    expect(orderStepOf('DELIVERED', 'DELIVERED')).toBe('DELIVERED')
    expect(orderStepOf('DELIVERY_FAILED', 'FAILED')).toBe('DELIVERY_FAILED')
    expect(orderStepOf('CANCELLED', 'CANCELLED')).toBe('CANCELLED')
  })
})

describe('arrivalText', () => {
  // 10:30 in India (UTC+5:30)
  const now = new Date('2026-10-27T05:00:00Z').getTime()
  const route = (target: TrackingRoute['target'], estimatedArrivalAt: string): TrackingRoute => ({ target, distanceMeters: 2100, durationSeconds: 420, estimatedArrivalAt, points: [] })

  it('gives the arrival time from the routing service, in India time', () => {
    expect(arrivalText(route('DESTINATION', '2026-10-27T05:07:00Z'), 'Fresh Mart', now)).toBe('Arrives around 10:37 am (in about 7 min)')
    expect(arrivalText(route('PICKUP', '2026-10-27T05:04:00Z'), 'Fresh Mart', now)).toBe('At Fresh Mart around 10:34 am')
  })

  it('says at least 1 min, and nothing without a route (no guessing)', () => {
    expect(arrivalText(route('DESTINATION', '2026-10-27T04:59:00Z'), 'Fresh Mart', now)).toBe('Arrives around 10:29 am (in about 1 min)')
    expect(arrivalText(null, 'Fresh Mart', now)).toBeNull()
  })
})

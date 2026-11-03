import { describe, expect, it } from 'vitest'
import type { Order } from '../../api/types'
import { hasInvoice, paymentSummary, pollInterval, statusMessage } from './orderStatus'

describe('pollInterval', () => {
  const poll = (status: Order['status'], paymentStatus: Order['paymentStatus'] = 'CASH_ON_DELIVERY') => pollInterval({ status, paymentStatus })

  it('asks every 3 s while pending, every 30 s while the order moves, never once it is finished', () => {
    expect(poll('PENDING', 'PENDING')).toBe(3000)
    expect(poll('CONFIRMED')).toBe(30_000)
    expect(poll('OUT_FOR_DELIVERY')).toBe(30_000)
    expect(poll('DELIVERED', 'PAID')).toBe(false)
    expect(poll('CANCELLED', 'CANCELLED')).toBe(false)
    expect(poll('REJECTED', 'REFUNDED')).toBe(false)
    expect(poll('DELIVERY_FAILED', 'CANCELLED')).toBe(false)
    expect(pollInterval(undefined)).toBe(false)
  })

  it('keeps asking while the refund of a cancelled paid order is on its way', () => {
    expect(poll('CANCELLED', 'PAID')).toBe(3000)
  })
})

describe('hasInvoice', () => {
  it('has an invoice once the store accepted the order', () => {
    expect(hasInvoice('CONFIRMED')).toBe(false)
    expect(hasInvoice('ACCEPTED')).toBe(true)
    expect(hasInvoice('DELIVERED')).toBe(true)
    expect(hasInvoice('CANCELLED')).toBe(false)
  })
})

describe('statusMessage', () => {
  const order = { status: 'CONFIRMED', storeName: 'Fresh Mart', statusReason: null, deliveryPartnerName: null, deliveredAt: null } as const

  it('speaks of the store and the delivery partner by name', () => {
    expect(statusMessage(order).text).toBe('Fresh Mart will accept it shortly.')
    expect(statusMessage({ ...order, status: 'READY_FOR_PICKUP' }).text).toBe('We are finding a delivery partner for your order.')
    expect(statusMessage({ ...order, status: 'OUT_FOR_DELIVERY', deliveryPartnerName: 'Arjun Singh' }).text).toBe('Arjun Singh is bringing your order.')
  })

  it('gives the reason of a cancelled or rejected order', () => {
    expect(statusMessage({ ...order, status: 'CANCELLED', statusReason: 'Ordered by mistake' })).toEqual({ title: 'Order cancelled', text: 'Ordered by mistake' })
    expect(statusMessage({ ...order, status: 'REJECTED', statusReason: 'Out of milk today' }).title).toBe('The store could not take your order')
  })
})

describe('paymentSummary', () => {
  const amounts = { itemsTotal: 201, couponCode: null, couponDiscount: 0, deliveryFee: 30, taxIncluded: 2.3, totalAmount: 231 }
  const summary = (paymentMethod: Order['paymentMethod'], paymentStatus: Order['paymentStatus'], status: Order['status'] = 'CONFIRMED') =>
    paymentSummary({ status, paymentMethod, paymentStatus, amounts })

  it('says how and whether the order is paid', () => {
    expect(summary('COD', 'CASH_ON_DELIVERY')).toBe('Cash on delivery: pay ₹231 at the door')
    expect(summary('COD', 'PAID', 'DELIVERED')).toBe('Paid ₹231 in cash')
    expect(summary('ONLINE', 'PAID')).toBe('Paid ₹231 online')
    expect(summary('ONLINE', 'PAID', 'CANCELLED')).toBe('Paid ₹231 online. The refund is on its way.')
    expect(summary('ONLINE', 'REFUNDED', 'CANCELLED')).toBe('₹231 refunded')
    expect(summary('COD', 'CANCELLED', 'CANCELLED')).toBe('Nothing to pay')
  })
})

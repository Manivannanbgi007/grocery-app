import { describe, expect, it } from 'vitest'
import { MockApiError } from './mockApiError'
import { cancelMyOrder, getOrder, getStoreOrder, listStoreOrders, packOrder, placeOrder, rejectOrder, acceptOrder, markReadyForPickup } from './mockOrders'
import { listNotifications } from './mockNotifications'
import { publicStores, registerStore, setAcceptingOrders, storeOfOwner, updateDeliveryAreas, updateOpeningHours } from './mockStores'

/* The mock store and the store's order steps must follow store-service and order-service. */

const RAVI = 3 // vendor1, owner of Fresh Mart (store 1)
const MEENA = 6 // vendor2, owner of Green Basket (store 2)
const PRIYA = 4 // customer1

function errorOf(action: () => unknown): { status: number; message: string; details: string[] } {
  try {
    action()
  } catch (error) {
    if (error instanceof MockApiError) return { status: error.status, message: error.message, details: error.details.map((detail) => detail.message) }
    throw error
  }
  throw new Error('no error was thrown')
}

const DETAILS = { name: 'Sunil Stores', description: null, phone: '9123456780', email: null, addressLine1: '5, MG Road', addressLine2: null, city: 'Bengaluru', state: 'Karnataka', pincode: '560001' }

describe('mock stores', () => {
  it('registers one store per vendor, waiting for approval and hidden from customers', () => {
    expect(errorOf(() => storeOfOwner(2000))).toMatchObject({ status: 404, message: 'You have not registered a store yet' })
    const store = registerStore(2000, DETAILS)
    expect(store).toMatchObject({ status: 'PENDING_APPROVAL', deliveryPincodes: ['560001'] })
    expect(publicStores().map((candidate) => candidate.name)).toEqual(['Fresh Mart', 'Green Basket'])
    expect(errorOf(() => registerStore(2000, DETAILS))).toMatchObject({ status: 409 })
  })

  it('checks the opening hours like OpeningHoursValidator; left-out days are closed', () => {
    expect(errorOf(() => updateOpeningHours(RAVI, [{ dayOfWeek: 'MONDAY', opensAt: '22:00', closesAt: '07:00', closed: false }])).message).toBe('MONDAY: opensAt must be before closesAt')
    expect(errorOf(() => updateOpeningHours(RAVI, [{ dayOfWeek: 'MONDAY', opensAt: null, closesAt: null, closed: false }])).message).toBe(
      'MONDAY: opensAt and closesAt are required when the store is open',
    )
    const store = updateOpeningHours(RAVI, [{ dayOfWeek: 'MONDAY', opensAt: '09:00', closesAt: '18:30', closed: false }])
    expect(store.openingHours[0]).toEqual({ dayOfWeek: 'MONDAY', opensAt: '09:00:00', closesAt: '18:30:00', closed: false })
    expect(store.openingHours[6]).toMatchObject({ dayOfWeek: 'SUNDAY', closed: true })
  })

  it('changes the delivery PIN codes and the order intake, which the shop then follows', () => {
    expect(errorOf(() => updateDeliveryAreas(MEENA, ['56003'])).details).toEqual(['must be a valid 6-digit PIN code'])
    expect(updateDeliveryAreas(RAVI, ['560071', '560038', '560038']).deliveryPincodes).toEqual(['560038', '560071'])

    setAcceptingOrders(RAVI, false)
    expect(errorOf(() => placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null })).message).toBe('Fresh Mart is not taking orders right now')
  })
})

describe("the store's order steps", () => {
  it("lists only the store's orders and tells the store about a new one", () => {
    placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null })
    getOrder(PRIYA, 1001)
    expect(listStoreOrders(1, 'CONFIRMED').map((order) => order.id)).toEqual([1001, 903])
    expect(listStoreOrders(2, null)).toEqual([])
    expect(listNotifications(RAVI, true)[0]).toMatchObject({ type: 'NEW_ORDER', title: 'New order #1001', referenceId: 1001 })
    expect(errorOf(() => getStoreOrder(2, 1001))).toMatchObject({ status: 404, message: 'Order not found with id: 1001' })
  })

  it('accepts, packs and hands over in that order only, and tells the customer', () => {
    expect(errorOf(() => packOrder(1, 903)).message).toBe('Order 903 is CONFIRMED, so it cannot become PACKED')
    expect(acceptOrder(1, 903)).toMatchObject({ status: 'ACCEPTED', cancellable: true })
    expect(packOrder(1, 903).status).toBe('PACKED')
    expect(markReadyForPickup(1, 903)).toMatchObject({ status: 'READY_FOR_PICKUP', cancellable: true })
    expect(getStoreOrder(1, 903).timeline.map((entry) => [entry.status, entry.changedBy])).toEqual([
      ['PENDING', 'CUSTOMER'],
      ['CONFIRMED', 'SYSTEM'],
      ['ACCEPTED', 'VENDOR'],
      ['PACKED', 'VENDOR'],
      ['READY_FOR_PICKUP', 'VENDOR'],
    ])
    expect(listNotifications(9, false).map((notification) => notification.type)).toEqual(['ORDER_PACKED', 'ORDER_ACCEPTED'])
  })

  it('rejects with a required reason, which the customer gets', () => {
    expect(errorOf(() => rejectOrder(1, 903, ' ')).details).toEqual(['must not be blank'])
    expect(rejectOrder(1, 903, 'Out of eggs')).toMatchObject({ status: 'REJECTED', statusReason: 'Out of eggs', paymentStatus: 'CANCELLED', cancellable: false })
    expect(listNotifications(9, true)[0]).toMatchObject({ type: 'ORDER_REJECTED', message: 'The store rejected your order. Reason: Out of eggs. If you paid online, the money will be refunded.' })
  })

  it("tells the store when the customer cancels", () => {
    placeOrder(PRIYA, { addressId: 1, paymentMethod: 'COD', note: null })
    cancelMyOrder(PRIYA, 1001, 'Changed my mind')
    expect(listNotifications(RAVI, true)[0]).toMatchObject({
      type: 'ORDER_CANCELLED',
      title: 'Order #1001 was cancelled',
      message: 'Cancelled by the customer. Reason: Changed my mind. Do not pack it.',
    })
  })
})

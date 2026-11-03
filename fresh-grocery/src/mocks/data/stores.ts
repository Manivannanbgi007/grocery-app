import type { DayOfWeek, OpeningHours, Store } from '../../api/types'

/* The two approved stores of the backend's local seed (store-service), copied as the API returns them. */

/**
 * The PIN codes each store delivers to (store_delivery_pincodes). Not part of the public store API:
 * order-service checks them when an order is placed.
 */
export const demoDeliveryPincodes: Record<number, string[]> = {
  1: ['560038', '560008', '560071'],
  2: ['560034', '560095', '560047'],
}

const WEEK: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

function sameHoursEveryDay(opensAt: string, closesAt: string): OpeningHours[] {
  return WEEK.map((dayOfWeek) => ({ dayOfWeek, opensAt, closesAt, closed: false }))
}

export const demoStores: Store[] = [
  {
    id: 1,
    name: 'Fresh Mart',
    description: 'Fresh vegetables, fruits, dairy and daily essentials',
    phone: '9000000003',
    addressLine1: '45, 100 Feet Road, Indiranagar',
    addressLine2: 'Opposite CMH Hospital',
    city: 'Bengaluru',
    state: 'Karnataka',
    pincode: '560038',
    latitude: 12.9784,
    longitude: 77.6408,
    acceptingOrders: true,
    // The real API works this out from the opening hours and the current time
    openNow: true,
    openingHours: sameHoursEveryDay('07:00:00', '22:00:00'),
  },
  {
    id: 2,
    name: 'Green Basket',
    description: 'Organic produce and household items',
    phone: '9000000006',
    addressLine1: '12, 80 Feet Road, Koramangala 4th Block',
    addressLine2: null,
    city: 'Bengaluru',
    state: 'Karnataka',
    pincode: '560034',
    latitude: 12.9336,
    longitude: 77.6265,
    acceptingOrders: true,
    openNow: true,
    openingHours: sameHoursEveryDay('08:00:00', '21:00:00').map((hours) =>
      hours.dayOfWeek === 'SUNDAY' ? { ...hours, opensAt: '09:00:00', closesAt: '14:00:00' } : hours,
    ),
  },
]

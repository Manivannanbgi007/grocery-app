import { act, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Delivery } from '../../../api/types'
import { acceptDelivery, deliveryOfOrder, goOnline } from '../../../mocks/mockDelivery'
import { acceptOrder, markReadyForPickup, packOrder, placeOrder } from '../../../mocks/mockOrders'
import { snapshotOf } from '../../../mocks/mockTracking'
import { logInAs, renderApp } from '../../../test/render'

/*
 * The partner's live location on the mock API. Priya (user 4) orders from Fresh Mart (order 1001, ₹231 cash);
 * delivery1 (Arjun, user 5) has accepted it. jsdom has no GPS: each test puts a fake one into `navigator`.
 */

/** A fake navigator.geolocation: the test decides where the phone is, or that the partner refuses. */
function fakeGps() {
  let report: PositionCallback = () => {}
  let fail: PositionErrorCallback = () => {}
  const geolocation = {
    watchPosition: vi.fn((success: PositionCallback, error?: PositionErrorCallback | null) => {
      report = success
      fail = error ?? (() => {})
      return 7
    }),
    clearWatch: vi.fn(),
    getCurrentPosition: vi.fn(),
  }
  Object.defineProperty(navigator, 'geolocation', { value: geolocation, configurable: true })
  return {
    geolocation,
    /** The phone measures a position (accuracy in metres). */
    at(latitude: number, longitude: number, accuracy = 8) {
      act(() => report({ coords: { latitude, longitude, accuracy }, timestamp: Date.now() } as GeolocationPosition))
    },
    /** The partner answers "Block" to the browser's question. */
    refuse() {
      act(() => fail({ code: 1, PERMISSION_DENIED: 1, POSITION_UNAVAILABLE: 2, TIMEOUT: 3, message: 'User denied Geolocation' } as GeolocationPositionError))
    },
  }
}

/** Location access was allowed on an earlier visit (the Permissions API says "granted"). */
function alreadyAllowed() {
  Object.defineProperty(navigator, 'permissions', { value: { query: async () => ({ state: 'granted' }) }, configurable: true })
}

afterEach(() => {
  Reflect.deleteProperty(navigator, 'geolocation')
  Reflect.deleteProperty(navigator, 'permissions')
})

/** Priya's order is ready at Fresh Mart and Arjun has accepted the delivery. */
function arjunAcceptedPriyasOrder(): Delivery {
  const order = placeOrder(4, { addressId: 1, paymentMethod: 'COD', note: null })
  goOnline(5)
  acceptOrder(1, order.id)
  packOrder(1, order.id)
  markReadyForPickup(1, order.id)
  const delivery = deliveryOfOrder(order.id)!
  acceptDelivery(5, delivery.id)
  return delivery
}

/** The position the customer sees (the mock server's last stored one). */
const sharedPosition = (delivery: Delivery) => snapshotOf(delivery, undefined).location

async function openLiveLocation(delivery: Delivery) {
  logInAs('delivery1@grocery.com')
  const rendered = renderApp(`/delivery/deliveries/${delivery.id}`)
  const panel = await screen.findByRole('region', { name: 'Live location' }, { timeout: 10_000 })
  return { ...rendered, panel }
}

describe('Delivery panel: live location', () => {
  it('shares the position after the partner allows it, and stops when the order is delivered', async () => {
    const gps = fakeGps()
    const delivery = arjunAcceptedPriyasOrder()
    const { user, panel } = await openLiveLocation(delivery)

    expect(within(panel).getByText('Not sharing')).toBeInTheDocument()
    expect(within(panel).getByText(/The customer follows your trip on a map/)).toBeInTheDocument()
    await user.click(within(panel).getByRole('button', { name: 'Share my location' }))
    expect(gps.geolocation.watchPosition).toHaveBeenCalledWith(expect.any(Function), expect.any(Function), expect.objectContaining({ enableHighAccuracy: true }))
    expect(within(panel).getByText('Finding your position…')).toBeInTheDocument()

    // The first position is sent at once: the customer now sees it
    gps.at(12.975, 77.6405)
    expect(await within(panel).findByText('The customer sees where you are. Last sent just now.')).toBeInTheDocument()
    expect(within(panel).getByText('Sharing')).toBeInTheDocument()
    expect(within(panel).getByText(/Keep this screen open while you deliver/)).toBeInTheDocument()
    expect(sharedPosition(delivery)).toMatchObject({ latitude: 12.975, longitude: 77.6405, accuracyMeters: 8 })
    // The map shows the store, the customer and the partner
    expect(await within(panel).findByTitle('You')).toBeInTheDocument()
    expect(within(panel).getByTitle('Fresh Mart (pick up)')).toBeInTheDocument()
    expect(within(panel).getByTitle('Priya Sharma (deliver)')).toBeInTheDocument()

    // Picking up and starting the trip keep sharing; delivering stops it (the GPS watch is cleared)
    await user.click(screen.getByRole('button', { name: 'I picked up the order' }))
    await user.click(await screen.findByRole('button', { name: 'I am on my way' }))
    expect(await screen.findByRole('heading', { name: 'Bring it to the customer' })).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Live location' })).getByText('Sharing')).toBeInTheDocument()
    expect(gps.geolocation.clearWatch).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Delivered' }))
    await user.click(within(screen.getByRole('alertdialog', { name: 'Collected ₹231?' })).getByRole('button', { name: 'Yes, delivered' }))
    expect(await screen.findByText('Delivered. Well done!')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Live location' })).not.toBeInTheDocument())
    expect(gps.geolocation.clearWatch).toHaveBeenCalledWith(7)
  })

  it('does not send a position that is too imprecise', async () => {
    const gps = fakeGps()
    const delivery = arjunAcceptedPriyasOrder()
    const { user, panel } = await openLiveLocation(delivery)
    await user.click(within(panel).getByRole('button', { name: 'Share my location' }))

    gps.at(12.975, 77.6405, 2500)
    expect(await within(panel).findByRole('alert')).toHaveTextContent('Your position is not precise enough (about 2500 m). Turn on GPS / precise location.')
    expect(within(panel).getByText('Sending your first position…')).toBeInTheDocument()
    expect(sharedPosition(delivery)).toBeNull()
  })

  it('explains how to allow location when the partner blocked it', async () => {
    const gps = fakeGps()
    const delivery = arjunAcceptedPriyasOrder()
    const { user, panel } = await openLiveLocation(delivery)
    await user.click(within(panel).getByRole('button', { name: 'Share my location' }))

    gps.refuse()
    expect(await within(panel).findByText(/Location access is blocked for this site/)).toBeInTheDocument()
    expect(within(panel).getByText('Not sharing')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Share my location' })).toBeInTheDocument()
    expect(gps.geolocation.clearWatch).toHaveBeenCalledWith(7)
  })

  it('starts by itself when location was allowed before', async () => {
    const gps = fakeGps()
    alreadyAllowed()
    const delivery = arjunAcceptedPriyasOrder()
    const { panel } = await openLiveLocation(delivery)

    await waitFor(() => expect(gps.geolocation.watchPosition).toHaveBeenCalled())
    expect(within(panel).queryByRole('button', { name: 'Share my location' })).not.toBeInTheDocument()
  })

  it('says so when the browser has no location', async () => {
    const delivery = arjunAcceptedPriyasOrder()
    const { user, panel } = await openLiveLocation(delivery)

    await user.click(within(panel).getByRole('button', { name: 'Share my location' }))
    expect(within(panel).getByText(/This browser cannot share a location/)).toBeInTheDocument()
  })
})

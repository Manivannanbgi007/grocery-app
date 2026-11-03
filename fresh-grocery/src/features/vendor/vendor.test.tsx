import { act, screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { loadSession } from '../../api/session'
import type { DemoUser } from '../../mocks/data/users'
import { fail } from '../../mocks/handlers/respond'
import { addRegisteredUser, linkStoreToVendor } from '../../mocks/mockAuth'
import { getOrder, placeOrder } from '../../mocks/mockOrders'
import { registerStore, storeOfOwner } from '../../mocks/mockStores'
import { server } from '../../mocks/server'
import { appQueryClient, logInAs, renderApp } from '../../test/render'
import { QUEUE_POLL_MS, SAFETY_NET_POLL_MS } from './orders/useVendorOrders'

/*
 * The vendor panel on the mock API. vendor1 = Ravi Kumar (user 3) owns Fresh Mart (store 1); order 903 of customer3
 * (Onion ×2, Farm Eggs ×2, ₹196, cash on delivery) is waiting for the store.
 */

/** A vendor registered a moment ago, without a store (and so without a store id in the login). */
function newVendor(): DemoUser {
  const vendor: DemoUser = {
    id: 2000,
    fullName: 'Sunil Rao',
    email: 'sunil@example.com',
    phone: '9123456780',
    role: 'VENDOR',
    status: 'ACTIVE',
    storeId: null,
    lastLoginAt: null,
    createdAt: '2026-10-26T00:00:00Z',
    password: 'Store123',
  }
  addRegisteredUser(vendor)
  return vendor
}

async function fillLogin(user: ReturnType<typeof renderApp>['user'], email: string) {
  await user.type(await screen.findByLabelText('Email'), email)
  await user.type(screen.getByLabelText('Password'), 'Password@123')
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

describe('Vendor login', () => {
  it('sends a guest to the vendor login, and a vendor to the dashboard afterwards', async () => {
    const { user, router } = renderApp('/vendor/orders')
    expect(await screen.findByRole('heading', { name: 'Vendor login' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?next=%2Fvendor%2Forders')

    await fillLogin(user, 'vendor1@grocery.com')
    expect(await screen.findByRole('heading', { level: 1, name: 'Orders' })).toBeInTheDocument()
    expect(loadSession()?.user.storeId).toBe(1)
  })

  it('sends a customer account to the shop', async () => {
    const { user } = renderApp('/vendor/login')
    await fillLogin(user, 'customer1@grocery.com')
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('This is a customer account. Customers log in on the shop.')
    expect(within(alert).getByRole('link', { name: "Go to the shop's login" })).toHaveAttribute('href', '/login')
  })

  it('tells a logged-in customer that the panel is for vendors', async () => {
    logInAs()
    renderApp('/vendor')
    expect(await screen.findByRole('heading', { name: 'This panel is for vendors' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument()
  })

  it("logs a customer out of the panel's notice to the plain vendor login", async () => {
    logInAs()
    const { user, router } = renderApp('/vendor/orders?tab=all')
    await user.click(await screen.findByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Vendor login' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/vendor/login')
    expect(router.state.location.search).toBe('')
    expect(loadSession()).toBeNull()
  })
})

describe('Store gate', () => {
  it('lets a new vendor register the store, which then waits for approval', async () => {
    const vendor = newVendor()
    logInAs(vendor.email)
    const { user } = renderApp('/vendor')

    expect(await screen.findByRole('heading', { name: 'Register your store' })).toBeInTheDocument()
    expect(screen.getByLabelText('Store phone')).toHaveValue('9123456780')
    await user.click(screen.getByRole('button', { name: 'Send for approval' }))
    expect(screen.getByText("Please enter the store's name.")).toBeInTheDocument()

    await user.type(screen.getByLabelText('Store name'), 'Sunil Stores')
    await user.type(screen.getByLabelText('Street address'), '5, MG Road')
    await user.type(screen.getByLabelText('City'), 'Bengaluru')
    await user.type(screen.getByLabelText('State'), 'Karnataka')
    await user.type(screen.getByLabelText('PIN code'), '560001')
    await user.click(screen.getByRole('button', { name: 'Send for approval' }))

    expect(await screen.findByRole('heading', { name: 'Your store is waiting for approval' })).toBeInTheDocument()
    expect(screen.getByText('Sunil Stores')).toBeInTheDocument()
    // No menu until the store is approved
    expect(screen.queryByRole('navigation', { name: 'Vendor panel' })).not.toBeInTheDocument()
  })

  /** Sunil's store, approved by an admin after his login (so his login's token has no store id). */
  function approvedStoreOfLoggedInVendor(linked: boolean) {
    const vendor = newVendor()
    const store = registerStore(vendor.id, { name: 'Sunil Stores', description: null, phone: '9123456780', email: null, addressLine1: '5, MG Road', addressLine2: null, city: 'Bengaluru', state: 'Karnataka', pincode: '560001' })
    logInAs(vendor.email)
    storeOfOwner(vendor.id).status = 'ACTIVE'
    // StoreApproved: auth-service links the store to the account a moment after the approval
    if (linked) linkStoreToVendor(vendor.id, store.id)
    return { vendor, store }
  }

  it('opens the panel by itself once the store is approved: a new token brings the store id', async () => {
    const { store } = approvedStoreOfLoggedInVendor(true)
    renderApp('/vendor')

    expect(await screen.findByRole('heading', { name: 'Hello, Sunil' })).toBeInTheDocument()
    expect(loadSession()?.user.storeId).toBe(store.id)
  })

  it('offers to try again while the account is not linked to the store yet', async () => {
    const { vendor, store } = approvedStoreOfLoggedInVendor(false)
    const { user } = renderApp('/vendor')

    expect(await screen.findByRole('heading', { name: 'Sunil Stores is approved!' })).toBeInTheDocument()
    expect(await screen.findByText(/being linked to the store/)).toBeInTheDocument()
    linkStoreToVendor(vendor.id, store.id)
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { name: 'Hello, Sunil' })).toBeInTheDocument()
  })

  it('still lets the vendor log in again instead', async () => {
    approvedStoreOfLoggedInVendor(false)
    const { user, router } = renderApp('/vendor')

    await user.click(await screen.findByRole('button', { name: 'Log in again' }))
    expect(await screen.findByRole('heading', { name: 'Vendor login' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/vendor/login')
    expect(router.state.location.search).toBe('')
    expect(loadSession()).toBeNull()
  })
})

describe('Vendor orders', () => {
  it('shows what needs doing on the dashboard', async () => {
    logInAs('vendor1@grocery.com')
    renderApp('/vendor')
    expect(await screen.findByRole('heading', { name: 'Hello, Ravi' })).toBeInTheDocument()
    const newOrders = await screen.findByRole('link', { name: /New orders/ })
    await waitFor(() => expect(newOrders).toHaveTextContent('1'))
    expect(await screen.findByRole('link', { name: 'Orders, 1 new' })).toBeInTheDocument()
  })

  it('accepts a new order from the queue', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/orders')

    const queue = await screen.findByRole('list', { name: /New orders/ })
    expect(within(queue).getByRole('link', { name: 'Order #903' })).toBeInTheDocument()
    await user.click(within(queue).getByRole('button', { name: 'Accept order #903' }))

    expect(await screen.findByText('Order #903 accepted')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'No new orders right now. New orders show up here by themselves.' })).toBeInTheDocument()
    // The order flow moves on with it: nothing left to accept, one to pack
    const flow = screen.getByRole('list', { name: 'Your orders, step by step' })
    await waitFor(() => expect(within(flow).getByRole('link', { name: /To pack/ })).toHaveTextContent(/To pack\s*1/))
    expect(within(flow).getByRole('link', { name: /To accept/ })).toHaveTextContent(/To accept\s*0/)
    // The accepted order now counts on its tab
    await user.click(await screen.findByRole('link', { name: 'Accepted, 1 waiting' }))
    expect(await screen.findByRole('button', { name: 'Mark order #903 as packed' })).toBeInTheDocument()
  })

  it('takes an order from new to ready for pickup on its page', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/orders/903')

    expect(await screen.findByRole('heading', { level: 1, name: 'Order #903' })).toBeInTheDocument()
    const pickList = screen.getByRole('list', { name: 'Products to pack' })
    expect(within(pickList).getAllByRole('listitem').map((item) => item.textContent)).toEqual([expect.stringContaining('2×Onion'), expect.stringContaining('2×Farm Eggs')])
    expect(screen.getByRole('link', { name: 'Call Anita: 9000000009' })).toHaveAttribute('href', 'tel:9000000009')
    expect(screen.getByText('Note: “Please call before coming”')).toBeInTheDocument()
    expect(screen.getByText('Cash on delivery: the delivery partner collects ₹196')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Invoice' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Accept order #903' }))
    expect(await screen.findByRole('heading', { name: 'Pick and pack the items' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Invoice' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Mark order #903 as packed' }))
    await user.click(await screen.findByRole('button', { name: 'Mark order #903 ready for pickup' }))
    expect(await screen.findByRole('heading', { name: 'Waiting for pickup' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /order #903/ })).not.toBeInTheDocument()

    // The history speaks to the store: "by you", and no customer notes like "The store accepted your order"
    const history = screen.getByRole('heading', { name: 'History' }).parentElement!
    expect(within(history).getByText('Accepted').parentElement).toHaveTextContent('by you')
    expect(within(history).queryByText('The store accepted your order')).not.toBeInTheDocument()
    expect(within(history).getByText('Waiting for a delivery partner')).toBeInTheDocument()
  })

  it('rejects an order only with a reason', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/orders/903')

    await user.click(await screen.findByRole('button', { name: 'Reject' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Reject order #903?' })
    await user.click(within(dialog).getByRole('button', { name: 'Reject order' }))
    expect(within(dialog).getByText('Please tell the customer why.')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Some items are out of stock' }))
    await user.click(within(dialog).getByRole('button', { name: 'Reject order' }))
    expect(await screen.findByRole('heading', { name: 'You rejected this order' })).toBeInTheDocument()
    expect(screen.getByText('Some items are out of stock', { selector: 'section > p' })).toBeInTheDocument()
  })

  it("receives a customer's new order", async () => {
    placeOrder(4, { addressId: 1, paymentMethod: 'COD', note: null })
    getOrder(4, 1001) // the stock is reserved and cash on delivery accepted: the order is now CONFIRMED
    logInAs('vendor1@grocery.com')
    renderApp('/vendor/orders')
    const queue = await screen.findByRole('list', { name: /New orders/ })
    expect(within(queue).getByRole('link', { name: 'Order #1001' })).toBeInTheDocument()
  })

  // With the app's own React Query settings (appQueryClient): the test client's defaults would refresh anyway
  describe('The queue keeps itself up to date', () => {
    /** The way a browser reports switching to another tab and back (React Query listens on window, so it bubbles). */
    function setTabShown(shown: boolean) {
      act(() => {
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => (shown ? 'visible' : 'hidden') })
        document.dispatchEvent(new Event('visibilitychange', { bubbles: true }))
      })
    }

    /** customer1 orders her cart from Fresh Mart; reading it once moves the mock order on to CONFIRMED (order 1001). */
    function aCustomerOrders() {
      placeOrder(4, { addressId: 1, paymentMethod: 'COD', note: null })
      getOrder(4, 1001)
    }

    /** The live stream cannot be opened (e.g. order-service is restarting), until the test's handlers are reset. */
    function liveUpdatesDown(options?: { once: boolean }) {
      server.use(http.get('*/api/v1/vendor/orders/stream', ({ request }) => fail(503, 'Live updates are busy', request), options))
    }

    afterEach(() => {
      // Back to jsdom's own visibilityState (always "visible")
      delete (document as { visibilityState?: unknown }).visibilityState
      vi.useRealTimers()
    })

    it('is live: a new order shows up at once, with no "Check now"', async () => {
      logInAs('vendor1@grocery.com')
      renderApp('/vendor/orders', appQueryClient())
      const queue = await screen.findByRole('list', { name: /New orders/ })
      expect(await screen.findByText('Live')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Check now' })).not.toBeInTheDocument()

      // The stream reports it (the mock compares the store's orders twice a second): no 15 s wait
      aCustomerOrders()
      expect(await within(queue).findByRole('link', { name: 'Order #1001' }, { timeout: 2000 })).toBeInTheDocument()
      expect(await screen.findByRole('link', { name: 'New, 2 waiting' })).toBeInTheDocument()
      expect(await screen.findByRole('link', { name: 'Orders, 2 new' })).toBeInTheDocument() // the menu's counter too
    })

    it('asks only once a minute while live, not every 15 s', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true })
      // Counts the queue's requests (lists and counters); a handler that returns nothing lets the normal mock answer
      const queueRequests = vi.fn()
      server.use(
        http.get('*/api/v1/vendor/orders', () => {
          queueRequests()
        }),
      )
      logInAs('vendor1@grocery.com')
      renderApp('/vendor/orders', appQueryClient())
      await screen.findByRole('list', { name: /New orders/ })
      await screen.findByText('Live')
      await act(() => vi.advanceTimersByTimeAsync(1_000)) // the lists are loaded once more after "ready"
      queueRequests.mockClear()

      await act(() => vi.advanceTimersByTimeAsync(3 * QUEUE_POLL_MS))
      expect(queueRequests).not.toHaveBeenCalled()
      await act(() => vi.advanceTimersByTimeAsync(SAFETY_NET_POLL_MS - 3 * QUEUE_POLL_MS))
      expect(queueRequests).toHaveBeenCalled() // the safety net
    })

    it('reconnects after the live updates broke, and is live again', async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true })
      liveUpdatesDown({ once: true }) // the first attempt fails, the next one (2 s later) works
      logInAs('vendor1@grocery.com')
      renderApp('/vendor/orders', appQueryClient())
      await screen.findByRole('list', { name: /New orders/ })
      expect(screen.getByRole('button', { name: 'Check now' })).toBeInTheDocument()

      await act(() => vi.advanceTimersByTimeAsync(2_000))
      expect(await screen.findByText('Live')).toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Check now' })).not.toBeInTheDocument()
    })

    describe('while the live updates are down', () => {
      beforeEach(() => liveUpdatesDown())

      it('checks every 15 s by itself', async () => {
        vi.useFakeTimers({ shouldAdvanceTime: true })
        logInAs('vendor1@grocery.com')
        renderApp('/vendor/orders', appQueryClient())
        const queue = await screen.findByRole('list', { name: /New orders/ })
        expect(screen.getByText(/^Updated .* · checks every 15 s$/)).toBeInTheDocument()
        expect(within(queue).queryByRole('link', { name: 'Order #1001' })).not.toBeInTheDocument()

        aCustomerOrders()
        await act(() => vi.advanceTimersByTimeAsync(QUEUE_POLL_MS))
        expect(await within(queue).findByRole('link', { name: 'Order #1001' })).toBeInTheDocument()
        expect(await screen.findByRole('link', { name: 'New, 2 waiting' })).toBeInTheDocument()
      })

      it('asks at once when the vendor comes back to the browser tab', async () => {
        logInAs('vendor1@grocery.com')
        renderApp('/vendor/orders', appQueryClient())
        const queue = await screen.findByRole('list', { name: /New orders/ })

        // A customer orders while the vendor works in another tab
        setTabShown(false)
        aCustomerOrders()
        setTabShown(true)
        expect(await within(queue).findByRole('link', { name: 'Order #1001' })).toBeInTheDocument()
      })

      it('asks again for a list that is shown again (another tab of the queue and back)', async () => {
        logInAs('vendor1@grocery.com')
        const { user } = renderApp('/vendor/orders', appQueryClient())
        await screen.findByRole('list', { name: /New orders/ })
        await user.click(screen.getByRole('link', { name: 'All' }))
        await screen.findByRole('list', { name: /All orders/ })

        aCustomerOrders()
        await user.click(screen.getByRole('link', { name: /^New/ }))
        expect(await screen.findByRole('link', { name: 'Order #1001' })).toBeInTheDocument()
      })

      it('keeps the list when a refresh fails, says so, and recovers', async () => {
        logInAs('vendor1@grocery.com')
        const { user } = renderApp('/vendor/orders', appQueryClient())
        await screen.findByRole('list', { name: /New orders/ })
        expect(screen.getByText(/^Updated .* · checks every 15 s$/)).toBeInTheDocument()

        // order-service restarting: the refresh (and its one retry) fails
        server.use(http.get('*/api/v1/vendor/orders', ({ request }) => fail(503, 'Order service is restarting', request)))
        await user.click(screen.getByRole('button', { name: 'Check now' }))
        expect(await screen.findByText(/^Could not refresh the orders \(Order service is restarting\)/, {}, { timeout: 5000 })).toBeInTheDocument()
        // The last list stays on screen
        expect(screen.getByRole('list', { name: /New orders/ })).toBeInTheDocument()
        expect(screen.getByRole('link', { name: 'Order #903' })).toBeInTheDocument()

        server.resetHandlers()
        await user.click(screen.getByRole('button', { name: 'Try now' }))
        await waitFor(() => expect(screen.queryByText(/Could not refresh the orders/)).not.toBeInTheDocument())
      })
    })
  })

  it('pauses new orders from the top bar', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor')
    const intake = await screen.findByRole('switch', { name: 'Taking new orders' })
    expect(intake).toBeChecked()
    await user.click(intake)
    expect(await screen.findByRole('switch', { name: 'New orders paused' })).not.toBeChecked()
    expect(screen.getByText('New orders are paused')).toBeInTheDocument()
  })
})

describe('A vendor in the shop', () => {
  it('cannot shop, and finds the way back to the panel', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/products/1')

    await user.click(await screen.findByRole('button', { name: 'Add Tomato to cart' }))
    expect(await screen.findByText(/Shopping needs a customer account/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Account of Ravi Kumar' }))
    expect(screen.getByRole('link', { name: 'Vendor panel' })).toHaveAttribute('href', '/vendor')
    expect(screen.queryByRole('link', { name: 'My orders' })).not.toBeInTheDocument()
  })

  it('is told that the cart is for customers', async () => {
    logInAs('vendor1@grocery.com')
    renderApp('/cart')
    expect(await screen.findByRole('heading', { name: 'This page is for customers' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the vendor panel' })).toHaveAttribute('href', '/vendor')
  })
})

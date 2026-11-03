// Runs before every test file (see vite.config.ts "test.setupFiles").
import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { toast } from 'sonner'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { clearSession } from '../api/session'
import { closeWishlistPopup } from '../features/wishlist/wishlistPopupState'
import { resetLocalStorageLists } from '../hooks/useLocalStorageList'
import { resetMockUsers } from '../mocks/mockAuth'
import { resetMockBanners } from '../mocks/mockBanners'
import { resetMockCarts } from '../mocks/mockCarts'
import { resetMockCatalog } from '../mocks/mockCatalog'
import { resetMockChat } from '../mocks/mockChat'
import { resetMockCoupons } from '../mocks/mockCoupons'
import { resetMockCustomers } from '../mocks/mockCustomers'
import { resetMockDelivery } from '../mocks/mockDelivery'
import { resetMockNotifications } from '../mocks/mockNotifications'
import { resetMockOrders } from '../mocks/mockOrders'
import { resetMockOrderStreams } from '../mocks/mockOrderStream'
import { resetMockPlatform } from '../mocks/mockPlatform'
import { resetMockRatings } from '../mocks/mockRatings'
import { resetMockSocial } from '../mocks/mockSocial'
import { resetMockStores } from '../mocks/mockStores'
import { resetMockTracking } from '../mocks/mockTracking'
import { server } from '../mocks/server'

// jsdom (the fake browser of the tests) has no scrolling; the router's ScrollRestoration calls it, and the product
// page's stars scroll to the reviews
window.scrollTo = () => {}
Element.prototype.scrollIntoView = () => {}

// findBy... waits up to 3 s (default 1 s): pages are loaded on first use (lazy), and when all test files run
// in parallel, opening a page that redirects to another one (cart -> login) can take longer than a second
configure({ asyncUtilTimeout: 3000 })

// The tests use the same mock API as `npm run dev`. A request without a mock fails the test.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

afterEach(() => {
  cleanup() // remove what the test rendered
  toast.dismiss() // sonner shows the toasts still open to the next Toaster: the next test starts without them
  server.resetHandlers() // undo server.use(...) overrides of a test
  clearSession() // the login lives in memory (api/session.ts): the next test starts as a guest
  localStorage.clear()
  sessionStorage.clear()
  resetLocalStorageLists()
  closeWishlistPopup(false) // the pop-up's open state lives outside React
  resetMockCatalog() // every test starts with the demo catalog, the seed carts, addresses, orders and stores
  resetMockBanners()
  resetMockCarts()
  resetMockChat()
  resetMockCoupons()
  resetMockCustomers()
  resetMockDelivery()
  resetMockOrderStreams() // the vendor panel's live streams, before their orders are reset
  resetMockOrders()
  resetMockPlatform()
  resetMockRatings()
  resetMockNotifications()
  resetMockStores()
  resetMockTracking()
  resetMockUsers()
  resetMockSocial()
})

afterAll(() => server.close())

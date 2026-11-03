import { Outlet, ScrollRestoration, useLocation } from 'react-router'
import { AssistantButton } from '../../features/assistant/AssistantButton'
import { CUSTOMER_HOME } from '../../features/auth/authContext'
import { CompareBar } from '../../features/compare/CompareBar'
import { WishlistPopup } from '../../features/wishlist/WishlistPopup'
import { BackToTopButton } from './BackToTopButton'
import { FeatureStrip } from './FeatureStrip'
import { Footer } from './Footer'
import { Header } from './Header'
import { NavBar } from './NavBar'
import { NewsletterBanner } from './NewsletterBanner'
import { PageEnter } from './PageEnter'
import { SaleToast } from './SaleToast'

/**
 * Pages where the customer is about to pay: no "Start shopping" banner and "Why shop with us" cards there, so nothing
 * leads away from the checkout and the page stays short.
 */
const CHECKOUT_PAGES = ['/cart', '/checkout']

/** The home page, also at a logged-in customer's own address (CUSTOMER_HOME): the same page, the same look. */
const HOME_PAGES = ['/', CUSTOMER_HOME]

/** The frame around every page: header and menus on top, the page itself in <Outlet />, banner and footer below. */
export function MainLayout() {
  const { pathname } = useLocation()
  const showShopPromotion = !CHECKOUT_PAGES.includes(pathname)

  return (
    <div className="flex min-h-screen flex-col">
      {/* z-60: above the sticky header (z-50) */}
      <a href="#main" className="sr-only rounded-md bg-brand px-4 py-2 font-bold text-white focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-60">
        Skip to content
      </a>
      <Header />
      <NavBar />

      <main id="main" className="flex-1">
        <PageEnter>
          <Outlet />
        </PageEnter>
      </main>

      {showShopPromotion && (
        <>
          <NewsletterBanner variant={HOME_PAGES.includes(pathname) ? 'light' : 'green'} />
          <FeatureStrip />
        </>
      )}
      <Footer />

      <SaleToast />
      <CompareBar />
      <WishlistPopup />
      <BackToTopButton />
      <AssistantButton />
      {/* Scrolls to the top on every new page, and back to where you were on "Back" */}
      <ScrollRestoration />
    </div>
  )
}

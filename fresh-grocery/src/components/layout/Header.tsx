import { ArrowLeftRight, ArrowRight, Heart, Menu, ShoppingCart } from 'lucide-react'
import { useRef, useState } from 'react'
import { useLocation } from 'react-router'
import { useCart } from '../../features/cart/useCart'
import { useCompare } from '../../features/compare/useCompare'
import { useWishlist } from '../../features/wishlist/useWishlist'
import { useScrolledPast } from '../../hooks/useScrolledPast'
import { cn } from '../../utils/cn'
import { ButtonLink } from '../ui/Button'
import { AccountMenu } from './AccountMenu'
import { HeaderIconLink } from './HeaderIconLink'
import { Logo } from './Logo'
import { MobileMenu } from './MobileMenu'
import { NotificationBell } from './NotificationBell'
import { SearchBar } from './SearchBar'

/**
 * Logo, search, "Become Vendor" and the Compare / Wishlist / Cart / Account links. On phones also the menu button.
 * It stays at the top while the page scrolls (sticky, so it keeps its place and covers nothing at the top of the page).
 * Its height per screen size is --header-height in index.css: change that too when the header gets taller or smaller.
 */
export function Header() {
  const location = useLocation()
  const { count: wishlistCount } = useWishlist()
  const { count: compareCount } = useCompare()
  const { data: cart } = useCart()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  // A shadow once the page moves beneath the header
  const scrolled = useScrolledPast(0)

  // A new key re-creates the search bar, so it shows the shop's current filters (and is empty on other pages)
  const searchKey = location.pathname === '/shop' ? location.search : 'other-page'

  function closeMenu() {
    setMenuOpen(false)
    menuButtonRef.current?.focus()
  }

  return (
    // z-50: above the round buttons of the page (z-40), also the phone menu inside it; dialogs come later and cover it.
    // No backdrop-blur here: it would make the header the frame of the (fixed) phone menu and squeeze the menu into it.
    <header id="shop-header" className={cn('glass-bar sticky top-0 z-50 border-b border-line transition-shadow', scrolled && 'shadow-md shadow-heading/10')}>
      {/* Phones: tighter, so the icons stay on the logo's row and the header is two rows (icons, search), not three */}
      <div className="page-container flex flex-wrap items-center gap-x-3 gap-y-2 py-3 sm:gap-x-4 md:flex-nowrap md:py-4 lg:gap-8 lg:py-6">
        <button
          ref={menuButtonRef}
          type="button"
          onClick={() => setMenuOpen(true)}
          className="-ml-1 rounded-md p-1 text-heading hover:text-brand-text lg:hidden"
          aria-label="Open menu"
          aria-expanded={menuOpen}
        >
          <Menu className="size-7" aria-hidden="true" />
        </button>

        {/* Below 360 px (the narrowest phones) only the leaf mark: the words would push the icons onto a third row */}
        <Logo wordmarkClassName="max-[22.5rem]:hidden" />

        {/* Phones: the search box moves to its own row below (order-last + full width).
            min-w-0: the search box gets narrower when the row is full, instead of pushing the icons off the screen */}
        <div className="order-last w-full md:order-none md:max-w-2xl md:min-w-0 md:flex-1">
          <SearchBar key={searchKey} />
        </div>

        <ButtonLink to="/register?role=VENDOR" variant="outline" className="hidden xl:inline-flex">
          Become Vendor
          <ArrowRight className="size-4" aria-hidden="true" />
        </ButtonLink>

        <nav aria-label="Your account" className="ml-auto flex items-center gap-4 sm:gap-5 lg:gap-7">
          {/* Below lg there is no room for it next to the search (the compare bar at the bottom leads there), and its
              label waits for 2xl, so the search box keeps its width */}
          <HeaderIconLink to="/compare" icon={ArrowLeftRight} label="Compare" count={compareCount} className="hidden lg:flex" labelClassName="xl:hidden 2xl:inline" />
          <HeaderIconLink to="/wishlist" icon={Heart} label="Wishlist" count={wishlistCount} />
          {/* Only a logged-in customer has a cart, so a guest sees the icon without a counter */}
          <HeaderIconLink to="/cart" icon={ShoppingCart} label="Cart" count={cart?.itemCount} />
          <NotificationBell />
          <AccountMenu />
        </nav>
      </div>

      <MobileMenu open={menuOpen} onClose={closeMenu} />
    </header>
  )
}

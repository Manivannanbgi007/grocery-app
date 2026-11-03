import { X } from 'lucide-react'
import { useEffect, useId, useRef, type KeyboardEvent, type MouseEvent } from 'react'
import { createPortal } from 'react-dom'
import { Link, useLocation } from 'react-router'
import { toast } from 'sonner'
import { useProductsByIds } from '../../hooks/useProductsByIds'
import { keepFocusInside } from '../../utils/focusTrap'
import { useWishlist, type SavedProduct } from './useWishlist'
import { closeWishlistPopup, useWishlistPopupOpen } from './wishlistPopupState'
import { WishlistItem } from './WishlistItem'

/**
 * The wishlist pop-up that opens when a heart saves a product (MainLayout shows it): every saved product with its stock
 * and Add to cart, a link to the wishlist page and "Continue shopping".
 * Escape, a click on the dark background and both buttons close it; Tab stays inside it while it is open.
 */
export function WishlistPopup() {
  const open = useWishlistPopupOpen()
  const { pathname } = useLocation()

  // A link inside (a product, the wishlist page, the login for a guest's Add to cart) closes it
  useEffect(() => {
    closeWishlistPopup(false)
  }, [pathname])

  if (!open) return null
  // A portal renders it at the end of <body>, above everything else on the page
  return createPortal(<WishlistPanel />, document.body)
}

function WishlistPanel() {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const wishlist = useWishlist()
  const { products, missing } = useProductsByIds(wishlist.products.map((product) => product.id))

  // The focus moves into the pop-up when it opens
  useEffect(() => {
    closeRef.current?.focus()
  }, [])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    // Only keys pressed in the pop-up itself: the "Start a new cart?" dialog of Add to cart handles its own Escape
    if (!panelRef.current?.contains(event.target as Node)) return
    if (event.key === 'Escape') closeWishlistPopup()
    keepFocusInside(event, panelRef.current)
  }

  function handleBackgroundClick(event: MouseEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget) closeWishlistPopup()
  }

  function remove(saved: SavedProduct) {
    wishlist.remove(saved.id)
    toast.success(`${saved.name} removed from your wishlist`)
    // The button is gone with its row: the focus stays in the pop-up
    closeRef.current?.focus()
  }

  return (
    <div onClick={handleBackgroundClick} className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-ink/50 p-4 sm:pt-24">
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={handleKeyDown}
        className="flex max-h-[calc(100vh-2rem)] w-full max-w-3xl flex-col overflow-hidden rounded-2xl bg-card shadow-2xl motion-safe:animate-slide-up sm:max-h-[calc(100vh-8rem)]"
      >
        <header className="flex items-center justify-between gap-3 bg-brand px-5 py-3.5 text-white">
          <h2 id={titleId} className="font-heading text-lg font-bold text-white">
            Wishlist ({wishlist.count})
          </h2>
          <button ref={closeRef} type="button" onClick={() => closeWishlistPopup()} aria-label="Close the wishlist" className="rounded-md p-1 hover:bg-white/15">
            <X className="size-5" aria-hidden="true" />
          </button>
        </header>

        {wishlist.count === 0 ? (
          <p className="px-5 py-10 text-center">Your wishlist is empty. Tap the heart on any product to save it here.</p>
        ) : (
          <ul aria-label="Products in your wishlist" className="@container flex-1 divide-y divide-dashed divide-line overflow-y-auto">
            {wishlist.products.map((saved) => (
              <WishlistItem
                key={saved.id}
                saved={saved}
                product={products.get(saved.id)}
                missing={missing.has(saved.id)}
                onRemove={() => remove(saved)}
                // Also when it is the page that is open already (then the address does not change)
                onOpen={() => closeWishlistPopup(false)}
              />
            ))}
          </ul>
        )}

        <footer className="flex flex-wrap items-center justify-between gap-3 bg-ink px-5 py-3.5 text-sm font-bold">
          <Link to="/wishlist" onClick={() => closeWishlistPopup(false)} className="text-ink-brand underline-offset-4 hover:underline">
            Open wishlist page
          </Link>
          <button type="button" onClick={() => closeWishlistPopup()} className="text-white underline-offset-4 hover:underline">
            Continue shopping
          </button>
        </footer>
      </div>
    </div>
  )
}

import { ArrowLeft, ArrowRight, MessageCircle, ShieldCheck, ShoppingBasket, ShoppingCart, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageHero } from '../../components/layout/PageHero'
import { PageTitle } from '../../components/layout/PageTitle'
import { Appear } from '../../components/ui/Appear'
import { Button, ButtonLink } from '../../components/ui/Button'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { useProductsByIds } from '../../hooks/useProductsByIds'
import { useStoreName } from '../../hooks/useStores'
import { RequireCustomer } from '../auth/RequireCustomer'
import { CART_COLUMNS, CartItemRow } from './CartItemRow'
import { CheckoutSteps } from './CheckoutSteps'
import { CartTotals } from './CartTotals'
import { CouponSection } from './CouponSection'
import { useCart, useCartChanges } from './useCart'
import { whatsappShareLink } from './whatsapp'

/*
 * The look of the cart (only the look; every button works as before). Its colours mean the same as on the order page:
 *   orange = the products, pink = savings and coupons, blue = delivery, green = the money and the way to the checkout.
 * The movement is written as motion-safe:animate-... ("reduce motion" turns it off; see styles/index.css): the blocks
 * rise in one after the other (<Appear>), the products' rows rise in one by one, and the top card (PageHero) has a
 * floating cart icon and soft coloured circles that drift behind it.
 */

/** The cart needs a logged-in customer: guests are sent to the login page first. */
export function CartPage() {
  return (
    <RequireCustomer>
      <CartContent />
    </RequireCustomer>
  )
}

/** The two columns of the page, like the checkout: the products on the left, the summary on the right (from 1024 px). */
const COLUMNS = 'grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]'
const CARD = 'rounded-3xl border border-line bg-card'

function CartContent() {
  const { data: cart, isPending, isError, error, refetch } = useCart()
  const changes = useCartChanges()
  const { products } = useProductsByIds(cart?.items.map((item) => item.productId) ?? [])
  const storeName = useStoreName(cart?.storeId)
  const [confirmEmpty, setConfirmEmpty] = useState(false)
  const { updateQuantity, removeItem, emptyCart } = changes

  /** The row whose change is being saved gets its buttons disabled until the backend answered. */
  function isSaving(productId: number): boolean {
    return (updateQuantity.isPending && updateQuantity.variables?.productId === productId) || (removeItem.isPending && removeItem.variables === productId)
  }

  let content
  if (isPending) {
    content = (
      <div className={COLUMNS}>
        <Skeleton className="h-80 rounded-3xl" />
        <Skeleton className="h-96 rounded-3xl" />
      </div>
    )
  } else if (isError) {
    content = <ErrorState title="Could not load your cart" error={error} onRetry={() => void refetch()} />
  } else if (cart.items.length === 0) {
    content = (
      // A light orange-to-blue card with two drifting circles around the "empty" message
      <Appear>
        <div className="relative isolate overflow-hidden rounded-3xl border border-line bg-linear-to-br from-new-light via-card to-sale-light">
          <span aria-hidden="true" className="absolute -top-16 right-1/4 -z-10 size-56 rounded-full bg-glow-1 opacity-80 blur-3xl motion-safe:animate-blob" />
          <span
            aria-hidden="true"
            className="absolute -bottom-20 left-1/4 -z-10 size-56 rounded-full bg-glow-4 opacity-80 blur-3xl motion-safe:animate-blob"
            style={{ animationDelay: '-6s' }}
          />
          <EmptyState
            icon="🛒"
            title="Your cart is empty"
            message="Fill it with fresh fruits, vegetables and daily essentials."
            action={
              <ButtonLink to="/shop" className="group shadow-lg shadow-brand/30">
                Continue shopping
                <ArrowRight className="size-4 transition-transform duration-200 group-hover:translate-x-1" aria-hidden="true" />
              </ButtonLink>
            }
          />
        </div>
      </Appear>
    )
  } else {
    content = (
      <div className={COLUMNS}>
        <div className="space-y-4">
          {/* @container: the rows switch to the table layout by the width of this card (see CART_COLUMNS) */}
          <Appear>
            <section aria-label="Your products" className={`@container relative overflow-hidden ${CARD}`}>
              {/* An orange bar along the top: orange is the colour of the products */}
              <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-linear-to-r from-new to-hot" />
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-linear-to-r from-new-light to-transparent px-4 py-4 sm:px-5">
                <div className="flex min-w-0 items-center gap-3">
                  <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-xl bg-new text-white shadow-md shadow-new/30">
                    <ShoppingBasket className="size-5" />
                  </span>
                  <p className="text-sm">
                    There {cart.itemCount === 1 ? 'is' : 'are'} <strong className="text-warn-text">{cart.itemCount}</strong> {cart.itemCount === 1 ? 'product' : 'products'} in your cart
                    {storeName && (
                      <>
                        {' '}
                        from <strong className="text-heading">{storeName}</strong>
                      </>
                    )}
                    .
                  </p>
                </div>
                <Button variant="danger-outline" size="sm" onClick={() => setConfirmEmpty(true)}>
                  <Trash2 className="size-4" aria-hidden="true" />
                  Empty cart
                </Button>
              </div>

              {/* Column titles: only when the rows are laid out as a table (screen readers get labels in the rows) */}
              <div
                aria-hidden="true"
                className={`hidden gap-x-4 bg-surface px-5 py-2.5 font-heading text-xs font-bold tracking-wide text-body uppercase @3xl:grid ${CART_COLUMNS}`}
              >
                <span className="col-span-2">Product</span>
                <span>Price</span>
                <span>Quantity</span>
                <span className="text-right">Subtotal</span>
                <span />
              </div>

              <ul aria-label="Products in your cart" className="divide-y divide-line">
                {cart.items.map((item, index) => (
                  <CartItemRow
                    key={item.productId}
                    item={item}
                    product={products.get(item.productId)}
                    saving={isSaving(item.productId)}
                    onQuantityChange={(quantity) => updateQuantity.mutate({ productId: item.productId, quantity })}
                    onRemove={() => removeItem.mutate(item.productId)}
                    order={index}
                  />
                ))}
              </ul>
            </section>
          </Appear>

          {/* A pill; its arrow steps to the left under the mouse */}
          <Link
            to="/shop"
            className="group inline-flex items-center gap-1.5 rounded-full bg-brand-light px-4 py-2 text-sm font-bold text-brand-text transition-colors hover:bg-brand hover:text-white"
          >
            <ArrowLeft className="size-4 transition-transform duration-200 group-hover:-translate-x-1" aria-hidden="true" />
            Continue shopping
          </Link>

          {/* Under the products (not in the summary): the summary stays short, so its button is always in view */}
          <Appear order={2}>
            <CouponSection cart={cart} changes={changes} />
          </Appear>
        </div>

        <aside className="lg:sticky lg:top-below-header">
          <Appear order={1}>
            <CartTotals cart={cart}>
              {/* The checkout page checks the cart against today's prices and stock first */}
              <ButtonLink to="/checkout" size="lg" className="group w-full shadow-lg shadow-brand/30">
                Proceed to checkout
                <ArrowRight className="size-5 transition-transform duration-200 group-hover:translate-x-1" aria-hidden="true" />
              </ButtonLink>
              <a href={whatsappShareLink(cart, storeName)} target="_blank" rel="noreferrer" className={buttonClasses('outline', 'md', 'w-full')}>
                <MessageCircle className="size-4" aria-hidden="true" />
                Share on WhatsApp
              </a>
              <p className="flex items-center justify-center gap-1.5 pt-1 text-xs">
                <ShieldCheck className="size-4 text-brand-text" aria-hidden="true" />
                Safe checkout: cash on delivery, UPI or card
              </p>
            </CartTotals>
          </Appear>
        </aside>

        <ConfirmDialog
          open={confirmEmpty}
          title="Empty your cart?"
          message={`All ${cart.itemCount} products will be removed from your cart.`}
          confirmLabel="Empty cart"
          loading={emptyCart.isPending}
          onConfirm={() => emptyCart.mutate(undefined, { onSettled: () => setConfirmEmpty(false) })}
          onCancel={() => setConfirmEmpty(false)}
        />
      </div>
    )
  }

  return (
    <>
      <PageTitle title="Cart" />
      <BreadcrumbBar crumbs={[{ label: 'Shop', to: '/shop' }, { label: 'Cart' }]} />
      <div className="page-container mt-8">
        <PageHero icon={ShoppingCart} tone="orange" title="Your cart" subtitle="Check your products, then go to the checkout.">
          <CheckoutSteps current={0} />
        </PageHero>
        {content}
      </div>
    </>
  )
}

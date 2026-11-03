import { Copy, Gift, Heart, Share2 } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageHero } from '../../components/layout/PageHero'
import { PageTitle } from '../../components/layout/PageTitle'
import { Button, ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { LoadingState } from '../../components/ui/LoadingState'
import { ToneBar, ToneIcon } from '../../components/ui/ToneIcon'
import { toneCard } from '../../components/ui/tones'
import { useProductsByIds } from '../../hooks/useProductsByIds'
import { RecentlyViewed } from './RecentlyViewed'
import { toSavedProduct, useWishlist, type SavedProduct } from './useWishlist'
import { WishlistItem } from './WishlistItem'

/** A shared link holds at most this many product numbers (the wishlist's own limit). */
const MAX_SHARED = 50

/*
 * The look (only the look): pink, the colour of the wishlist. A pink top card (PageHero) with a floating heart, the
 * products in a pink card whose rows rise in one after the other, and the share link in a violet card. The movement is
 * motion-safe:animate-..., so "reduce motion" turns it off.
 */

/** The list of products: plain lines between the rows, like the cart table (grey stripes would hide the grey behind the product pictures) */
const LIST_CLASSES = '@container divide-y divide-line'

/** A light pink-to-violet card around an "empty" message */
const EMPTY_CARD = 'rounded-3xl border border-line bg-linear-to-br from-hot/10 via-card to-violet-light motion-safe:animate-rise'

/** "1,5,9" -> [1, 5, 9]: the product numbers of a shared link, without anything that is not a number. */
function parseShared(value: string): number[] {
  const ids = value
    .split(',')
    .map(Number)
    .filter((id) => Number.isInteger(id) && id > 0)
  return [...new Set(ids)].slice(0, MAX_SHARED)
}

/**
 * The wishlist: the products saved with the heart buttons (kept in this browser), with today's price and stock, and a
 * link to share them. Opened with ?shared=1,5,9 it shows someone else's shared wishlist instead.
 */
export function WishlistPage() {
  const [searchParams] = useSearchParams()
  const shared = searchParams.get('shared')

  return (
    <>
      <PageTitle title="Wishlist" />
      <BreadcrumbBar crumbs={[{ label: 'Wishlist' }]} />
      <div className="page-container mt-8 space-y-12">
        {shared === null ? <MyWishlist /> : <SharedWishlist productIds={parseShared(shared)} />}
        <RecentlyViewed />
      </div>
    </>
  )
}

function MyWishlist() {
  const wishlist = useWishlist()
  const { products, missing } = useProductsByIds(wishlist.products.map((product) => product.id))
  const shareLink = `${window.location.origin}/wishlist?shared=${wishlist.products.map((product) => product.id).join(',')}`

  function remove(saved: SavedProduct) {
    wishlist.remove(saved.id)
    toast.success(`${saved.name} removed from your wishlist`)
  }

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(shareLink)
      toast.success('Wishlist link copied')
    } catch {
      toast.error('Could not copy the link. Select it and copy it yourself.')
    }
  }

  return (
    <section aria-labelledby="wishlist-title">
      <PageHero
        icon={Heart}
        tone="pink"
        title="Wishlist"
        titleId="wishlist-title"
        subtitle={
          wishlist.count === 0 ? (
            'Save products with the heart button to buy them later.'
          ) : (
            <>
              There {wishlist.count === 1 ? 'is' : 'are'} <strong className="text-hot-text">{wishlist.count}</strong> {wishlist.count === 1 ? 'product' : 'products'} in
              your wishlist.
            </>
          )
        }
      />
      {wishlist.count === 0 ? (
        <div className={EMPTY_CARD}>
          <EmptyState
            icon="💚"
            title="There are no products on the Wishlist!"
            message="Tap the heart on any product to save it here for later."
            action={
              <ButtonLink to="/shop" className="shadow-lg shadow-brand/30">
                Browse products
              </ButtonLink>
            }
          />
        </div>
      ) : (
        <>
          <div className={toneCard('pink')}>
            <ToneBar tone="pink" />
            <ul aria-label="Products in your wishlist" className={LIST_CLASSES}>
              {wishlist.products.map((saved, index) => (
                <WishlistItem key={saved.id} saved={saved} product={products.get(saved.id)} missing={missing.has(saved.id)} onRemove={() => remove(saved)} order={index} />
              ))}
            </ul>
          </div>

          {/* Sharing: a violet card with the link and its Copy button */}
          <div className={toneCard('violet', 'mt-6 p-5 motion-safe:animate-rise')}>
            <ToneBar tone="violet" />
            <div className="flex flex-wrap items-center gap-3">
              <ToneIcon icon={Share2} tone="violet" solid />
              <label htmlFor="wishlist-link" className="font-heading text-sm font-bold text-heading">
                Wishlist link
              </label>
              <input
                id="wishlist-link"
                readOnly
                value={shareLink}
                onFocus={(event) => event.target.select()}
                className="h-10 min-w-0 flex-1 rounded-xl border border-line bg-card px-3 text-sm text-heading focus:border-violet focus:ring-4 focus:ring-violet/15 focus:outline-none sm:max-w-sm"
              />
              <Button onClick={() => void copyLink()}>
                <Copy className="size-4" aria-hidden="true" />
                Copy
              </Button>
            </div>
            <p className="mt-2 text-sm">Anyone with the link sees these products and can save them to their own wishlist.</p>
          </div>
        </>
      )}
    </section>
  )
}

/** Someone's wishlist from a shared link: the products, today's price and stock, and a button to save them all. */
function SharedWishlist({ productIds }: { productIds: number[] }) {
  const wishlist = useWishlist()
  const { products, missing } = useProductsByIds(productIds)
  const loading = productIds.some((id) => !products.has(id) && !missing.has(id))
  const found = productIds.flatMap((id) => products.get(id) ?? [])
  const notSavedYet = found.filter((product) => !wishlist.isInWishlist(product.id))

  function saveAll() {
    notSavedYet.forEach((product) => wishlist.toggle(product))
    toast.success(`${notSavedYet.length} ${notSavedYet.length === 1 ? 'product' : 'products'} saved to your wishlist`)
  }

  return (
    <section aria-labelledby="shared-title">
      <PageHero icon={Gift} tone="pink" title="A shared wishlist" titleId="shared-title" subtitle="Products someone picked for you, with today's prices." />
      {loading ? (
        <LoadingState label="Loading the shared wishlist…" className="py-16" />
      ) : found.length === 0 ? (
        <div className={EMPTY_CARD}>
          <EmptyState icon="💚" title="These products are no longer available" message="Have a look at what the shop has today." action={<ButtonLink to="/shop">Browse products</ButtonLink>} />
        </div>
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <p>Someone shared {found.length === 1 ? 'this product' : `these ${found.length} products`} with you.</p>
            <Button variant="outline" onClick={saveAll} disabled={notSavedYet.length === 0}>
              <Heart className="size-4" aria-hidden="true" />
              {notSavedYet.length === 0 ? 'All saved to your wishlist' : 'Save all to my wishlist'}
            </Button>
          </div>
          <div className={toneCard('pink')}>
            <ToneBar tone="pink" />
            <ul aria-label="Products in the shared wishlist" className={LIST_CLASSES}>
              {found.map((product, index) => (
                // No "added" date: that is the other person's
                <WishlistItem key={product.id} saved={{ ...toSavedProduct(product), addedAt: undefined }} product={product} missing={false} order={index} />
              ))}
            </ul>
          </div>
        </>
      )}
    </section>
  )
}

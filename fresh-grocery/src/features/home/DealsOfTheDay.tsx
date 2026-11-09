import { Flame } from 'lucide-react'
import { Link } from 'react-router'
import type { Product } from '../../api/types'
import { AddToCartButton } from '../../components/product/AddToCartButton'
import { CompareButton } from '../../components/product/CompareButton'
import { ProductImage } from '../../components/product/ProductImage'
import { WishlistButton } from '../../components/product/WishlistButton'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Price } from '../../components/ui/Price'
import { SectionTitle } from '../../components/ui/SectionTitle'
import { Skeleton } from '../../components/ui/Skeleton'
import { StarRating } from '../../components/ui/StarRating'
import { useDeals } from '../../hooks/useDeals'
import { useScrollToHash } from '../../hooks/useScrollToHash'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { savingsOf } from '../../utils/discount'
import { formatPrice, formatUnit } from '../../utils/format'

const DEAL_COUNT = 4

/**
 * One deal as a wide "ticket": the picture on the left with a big "20% OFF" sticker, the details on the right (name,
 * pack size and store, stars), and at the bottom what matters for a deal: the price against the old one, how much is
 * saved, a warning when only a few are left, and a full "Add to cart" button.
 *
 * The card is a container (@container): when it is wide (two deals side by side on a desktop, or one on a tablet),
 * the button sits next to the price; when it is narrow (a phone), under it. The movements are the product cards' ones
 * (lift, zoom, the compare button sliding in) plus a little tilt of the sticker, all motion-safe.
 */
function DealCard({ product }: { product: Product }) {
  const storeName = useStoreName(product.storeId)
  const productUrl = `/products/${product.id}`
  const percent = Math.round(product.discountPercentage)

  return (
    <article
      className={cn(
        '@container group relative flex h-full overflow-hidden rounded-2xl border border-line bg-card',
        'transition duration-300 ease-out hover:border-hot/40 hover:shadow-xl hover:shadow-hot/10 focus-within:border-hot/40 motion-safe:hover:-translate-y-1',
      )}
    >
      <div className="relative w-[38%] max-w-56 shrink-0">
        {/* The picture repeats the name link, so keyboard and screen reader users skip it */}
        <Link to={productUrl} tabIndex={-1} aria-hidden="true" className="block h-full overflow-hidden bg-linear-to-br from-hot/15 via-surface to-surface">
          <div className="h-full transition-transform duration-500 ease-out motion-safe:group-hover:scale-110">
            <ProductImage product={product} className="aspect-auto h-full min-h-40 rounded-none bg-transparent" emojiClassName="text-6xl drop-shadow-md @xl:text-7xl" />
          </div>
        </Link>
        {/* Read as "20% off" */}
        <span className="absolute top-2.5 left-2.5 flex flex-col items-center rounded-xl bg-deep-pink px-2 py-1.5 font-heading leading-none text-white shadow-md shadow-hot/30 transition-transform duration-300 motion-safe:group-hover:-rotate-6 motion-safe:group-hover:scale-110">
          <span className="text-lg font-bold">{percent}%</span>
          <span className="mt-0.5 text-[10px] font-bold tracking-widest uppercase">off</span>
        </span>
      </div>

      <div className="flex min-w-0 flex-1 flex-col p-3 @lg:p-4">
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <h3 className="text-[0.95rem] leading-snug @lg:text-base">
              <Link to={productUrl} className="line-clamp-2 transition-colors hover:text-brand-text">
                {product.name}
              </Link>
            </h3>
            <p className="mt-1 flex min-w-0 items-center gap-1.5 text-xs">
              <span className="shrink-0">{formatUnit(product.unit, product.unitQuantity)}</span>
              {storeName && (
                <>
                  <span className="size-1 shrink-0 rounded-full bg-muted" aria-hidden="true" />
                  <span className="truncate">
                    By <span className="text-brand-text">{storeName}</span>
                  </span>
                </>
              )}
            </p>
            <StarRating value={product.ratingAverage} count={product.ratingCount} className="mt-1.5" />
          </div>
          <div className="flex shrink-0 flex-col gap-1.5">
            <WishlistButton product={product} className="size-8" />
            <CompareButton product={product} className="card-reveal size-8" />
          </div>
        </div>

        <div className="mt-auto flex flex-col gap-3 pt-3 @lg:flex-row @lg:items-end @lg:justify-between">
          <div className="min-w-0">
            <Price sellingPrice={product.sellingPrice} price={product.price} />
            <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
              <span className="rounded-full bg-hot/10 px-2 py-0.5 font-bold text-hot-text">You save {formatPrice(savingsOf(product))}</span>
              {product.lowStock && <span className="font-semibold text-warn-text">Only {product.availableQuantity} left</span>}
            </p>
          </div>
          <AddToCartButton product={product} variant="full" className="w-full shrink-0 @lg:w-auto" />
        </div>
      </div>
    </article>
  )
}

/** Grey placeholder with the shape of a DealCard, shown while the deals load. */
function DealCardSkeleton() {
  return (
    <div className="flex h-full overflow-hidden rounded-2xl border border-line bg-card" aria-hidden="true">
      <Skeleton className="min-h-44 w-[38%] max-w-56 shrink-0 rounded-none" />
      <div className="flex flex-1 flex-col gap-2 p-4">
        <Skeleton className="h-4 w-4/5" />
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-3 w-1/3" />
        <div className="mt-auto flex items-end justify-between gap-3 pt-3">
          <Skeleton className="h-6 w-24" />
          <Skeleton className="h-10 w-32" />
        </div>
      </div>
    </div>
  )
}

/**
 * The products with the biggest discount right now (the "Hot Deals" link jumps here: #deals), as two columns of wide
 * cards on a desktop and one on a phone. The sample shows a countdown per product, but products have no "deal ends"
 * date, so there is none here; the title says how much the best deal saves instead.
 */
export function DealsOfTheDay() {
  const { deals, isPending, isError, error, refetch } = useDeals()
  useScrollToHash('deals', !isPending)
  const shown = deals.slice(0, DEAL_COUNT)
  const bestPercent = shown.length > 0 ? Math.round(shown[0].discountPercentage) : null

  return (
    <section aria-labelledby="deals" className="page-container mt-16">
      <SectionTitle id="deals" title="Deals Of The Day">
        {bestPercent !== null && (
          <p className="inline-flex items-center gap-1.5 rounded-full bg-hot/10 px-3 py-1.5 text-sm font-bold text-hot-text">
            <Flame className="size-4" aria-hidden="true" />
            Save up to {bestPercent}% today
          </p>
        )}
      </SectionTitle>

      {isError ? (
        <ErrorState title="Could not load the deals" error={error} onRetry={() => void refetch()} />
      ) : !isPending && deals.length === 0 ? (
        <EmptyState icon="🏷️" title="No deals right now" message="Discounts appear here as soon as a store offers one." />
      ) : (
        <ul className="grid gap-3 sm:gap-4 lg:grid-cols-2">
          {isPending
            ? Array.from({ length: DEAL_COUNT }, (_, index) => (
                <li key={index}>
                  <DealCardSkeleton />
                </li>
              ))
            : shown.map((product, index) => (
                <li key={product.id} className="motion-safe:animate-rise" style={{ animationDelay: `${index * 70}ms` }}>
                  <DealCard product={product} />
                </li>
              ))}
        </ul>
      )}
    </section>
  )
}

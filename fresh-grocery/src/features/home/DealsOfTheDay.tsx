import { Link } from 'react-router'
import type { Product } from '../../api/types'
import { AddToCartButton } from '../../components/product/AddToCartButton'
import { ProductImage } from '../../components/product/ProductImage'
import { Badge } from '../../components/ui/Badge'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Price } from '../../components/ui/Price'
import { SectionTitle } from '../../components/ui/SectionTitle'
import { Skeleton } from '../../components/ui/Skeleton'
import { useDeals } from '../../hooks/useDeals'
import { useScrollToHash } from '../../hooks/useScrollToHash'
import { useStoreName } from '../../hooks/useStores'
import { savingsOf } from '../../utils/discount'
import { formatPrice } from '../../utils/format'

const DEAL_COUNT = 4

function DealCard({ product }: { product: Product }) {
  const storeName = useStoreName(product.storeId)
  const productUrl = `/products/${product.id}`

  // One card, the same shape as ProductCard; the badge is pink here, so the deals stand out
  return (
    <article className="relative flex h-full flex-col rounded-2xl border border-line bg-card p-3 transition hover:border-brand-soft hover:shadow-lg hover:shadow-heading/5 sm:p-4">
      <Badge tone="hot" className="absolute top-0 left-0 z-10 rounded-none rounded-tl-2xl rounded-br-2xl px-4 py-1.5">
        -{Math.round(product.discountPercentage)}%
      </Badge>
      <Link to={productUrl} tabIndex={-1} aria-hidden="true" className="block pt-5">
        <ProductImage product={product} className="aspect-[4/3]" emojiClassName="text-6xl sm:text-7xl xl:text-8xl" />
      </Link>

      {/* Always two lines high, so the store, the savings and the price line up across a row of cards */}
      <h3 className="mt-3 min-h-[2lh] text-base leading-snug">
        <Link to={productUrl} className="line-clamp-2 hover:text-brand-text">
          {product.name}
        </Link>
      </h3>
      {storeName && (
        <p className="mt-1 text-sm">
          By <span className="text-brand-text">{storeName}</span>
        </p>
      )}
      <p className="mt-1 text-sm font-bold text-hot">You save {formatPrice(savingsOf(product))}</p>

      <div className="mt-auto flex flex-wrap items-center justify-between gap-2 pt-3">
        <Price sellingPrice={product.sellingPrice} price={product.price} />
        <AddToCartButton product={product} />
      </div>
    </article>
  )
}

/** Grey placeholder with the shape of a DealCard, shown while the deals load. */
function DealCardSkeleton() {
  return (
    <div className="flex h-full flex-col gap-3 rounded-2xl border border-line bg-card p-3 sm:p-4" aria-hidden="true">
      <Skeleton className="mt-5 aspect-[4/3]" />
      <Skeleton className="h-4 w-4/5" />
      <Skeleton className="h-3 w-1/2" />
      <div className="mt-auto flex items-center justify-between pt-3">
        <Skeleton className="h-5 w-16" />
        <Skeleton className="h-8 w-16" />
      </div>
    </div>
  )
}

/**
 * The products with the biggest discount right now (the "Hot Deals" link jumps here: /#deals).
 * The sample shows a countdown per product, but products have no "deal ends" date, so there is none here.
 */
export function DealsOfTheDay() {
  const { deals, isPending, isError, error, refetch } = useDeals()
  useScrollToHash('deals', !isPending)

  return (
    <section aria-labelledby="deals" className="page-container mt-16">
      <SectionTitle id="deals" title="Deals Of The Day" />

      {isError ? (
        <ErrorState title="Could not load the deals" error={error} onRetry={() => void refetch()} />
      ) : !isPending && deals.length === 0 ? (
        <EmptyState icon="🏷️" title="No deals right now" message="Discounts appear here as soon as a store offers one." />
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-4">
          {isPending
            ? Array.from({ length: DEAL_COUNT }, (_, index) => (
                <li key={index}>
                  <DealCardSkeleton />
                </li>
              ))
            : deals.slice(0, DEAL_COUNT).map((product) => (
                <li key={product.id}>
                  <DealCard product={product} />
                </li>
              ))}
        </ul>
      )}
    </section>
  )
}

import { ArrowRight } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { ProductCard } from '../../components/product/ProductCard'
import { ProductCardSkeleton } from '../../components/product/ProductCardSkeleton'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { ScrollArrows } from '../../components/ui/ScrollArrows'
import { SectionTitle } from '../../components/ui/SectionTitle'
import { useCategories } from '../../hooks/useCategories'
import { useProductSearch } from '../../hooks/useProductSearch'
import { useScrollRow } from '../../hooks/useScrollRow'
import { cn } from '../../utils/cn'
import { CategoryTabs } from './CategoryTabs'

/** The everyday fresh categories, found by slug (ids differ between databases). */
const FRESH_SLUGS = ['vegetables', 'fruits', 'dairy-eggs']
const PRODUCT_COUNT = 8

/**
 * "Daily Fresh Picks": the best-rated products in stock from the fresh categories, in a sideways-scrolling row.
 * (The sample calls this "Daily Best Sells", but the shop has no public sales numbers, so it does not claim to.)
 */
export function DailyFreshPicks() {
  const { data: categories = [] } = useCategories()
  const freshCategories = categories.filter((category) => FRESH_SLUGS.includes(category.slug))
  const [chosenId, setChosenId] = useState<number | undefined>(undefined)
  // Until a button is clicked, the first fresh category is shown
  const categoryId = chosenId ?? freshCategories[0]?.id

  const { data, isPending, isError, error, refetch, isPlaceholderData } = useProductSearch(
    { categoryId, inStockOnly: true, sort: 'RATING', size: PRODUCT_COUNT },
    categoryId !== undefined,
  )
  const products = data?.content ?? []
  const { rowRef, canPrev, canNext, scrollPrev, scrollNext } = useScrollRow<HTMLUListElement>(products.length)

  if (categories.length > 0 && freshCategories.length === 0) return null

  return (
    <section aria-labelledby="daily-fresh-picks" className="page-container mt-16">
      <SectionTitle id="daily-fresh-picks" title="Daily Fresh Picks">
        <div className="flex flex-wrap items-center gap-x-8 gap-y-3">
          <CategoryTabs label="Show fresh picks of" categories={freshCategories} selectedId={categoryId} onSelect={setChosenId} />
          <ScrollArrows label="fresh picks" canPrev={canPrev} canNext={canNext} onPrev={scrollPrev} onNext={scrollNext} />
        </div>
      </SectionTitle>

      <div className="flex gap-5">
        <div className="relative hidden w-72 shrink-0 flex-col items-start gap-6 overflow-hidden rounded-2xl bg-brand-soft/60 p-10 lg:flex">
          <h3 className="relative z-10 font-display text-4xl leading-tight font-semibold">Bring nature into your home</h3>
          <Link to="/shop" className={buttonClasses('primary', 'sm', 'relative z-10')}>
            Shop now
            <ArrowRight className="size-3.5" aria-hidden="true" />
          </Link>
          <span className="absolute -right-6 -bottom-4 text-[11rem] leading-none select-none" aria-hidden="true">
            🌿
          </span>
        </div>

        <div className="min-w-0 flex-1">
          {isError ? (
            <ErrorState title="Could not load the products" error={error} onRetry={() => void refetch()} />
          ) : !isPending && products.length === 0 ? (
            <EmptyState icon="🧺" title="Nothing in stock here right now" />
          ) : (
            // pt-1.5 (taken back by -mt-1.5): room for a card's hover lift, which the sideways scrolling would cut off
            <ul ref={rowRef} className={cn('scrollbar-none -mt-1.5 flex snap-x gap-5 overflow-x-auto pt-1.5 pb-2 transition-opacity', isPlaceholderData && 'opacity-50')}>
              {isPending
                ? Array.from({ length: 4 }, (_, index) => (
                    <li key={index} className="w-60 shrink-0">
                      <ProductCardSkeleton />
                    </li>
                  ))
                : products.map((product) => (
                    <li key={product.id} className="w-60 shrink-0 snap-start">
                      <ProductCard product={product} addButton="full" />
                    </li>
                  ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  )
}

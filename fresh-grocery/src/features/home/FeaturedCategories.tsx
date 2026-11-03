import { Link } from 'react-router'
import type { Category } from '../../api/types'
import { ErrorState } from '../../components/ui/ErrorState'
import { ScrollArrows } from '../../components/ui/ScrollArrows'
import { SectionTitle } from '../../components/ui/SectionTitle'
import { Skeleton } from '../../components/ui/Skeleton'
import { useCategories } from '../../hooks/useCategories'
import { useCategoryCounts } from '../../hooks/useCategoryCounts'
import { useScrollRow } from '../../hooks/useScrollRow'
import { cn } from '../../utils/cn'
import { categoryEmoji, tileBackground } from '../../utils/emoji'
import { shopLink } from '../../utils/links'

function CategoryTile({ category, count }: { category: Category; count: number | undefined }) {
  return (
    <Link
      to={shopLink({ category: category.id })}
      className={cn(
        'group flex h-full flex-col items-center gap-2 rounded-xl border border-transparent px-3 py-5 text-center transition hover:border-brand-soft hover:shadow-md',
        tileBackground(category.id),
      )}
    >
      <span className="text-5xl transition-transform group-hover:scale-110" aria-hidden="true">
        {categoryEmoji(category.slug)}
      </span>
      <span className="font-heading text-sm font-bold text-heading">{category.name}</span>
      {/* min-h-5 keeps the line's height while the count is still loading */}
      <span className="min-h-5 text-sm">{count === undefined ? '' : `${count} ${count === 1 ? 'item' : 'items'}`}</span>
    </Link>
  )
}

/** A sideways-scrolling row of category tiles with the number of products in each. */
export function FeaturedCategories() {
  const { data: categories = [], isPending, isError, error, refetch } = useCategories()
  const counts = useCategoryCounts(categories)
  const { rowRef, canPrev, canNext, scrollPrev, scrollNext } = useScrollRow<HTMLUListElement>(categories.length)

  return (
    <section aria-labelledby="featured-categories" className="page-container mt-12">
      <SectionTitle id="featured-categories" title="Featured Categories">
        <ScrollArrows label="categories" canPrev={canPrev} canNext={canNext} onPrev={scrollPrev} onNext={scrollNext} />
      </SectionTitle>

      {isError && <ErrorState compact title="Could not load the categories" error={error} onRetry={() => void refetch()} />}

      <ul ref={rowRef} className="scrollbar-none flex snap-x gap-4 overflow-x-auto pb-2">
        {isPending
          ? Array.from({ length: 8 }, (_, index) => (
              <li key={index} className="w-36 shrink-0">
                <Skeleton className="h-40 rounded-xl" />
              </li>
            ))
          : categories.map((category) => (
              <li key={category.id} className="w-36 shrink-0 snap-start">
                <CategoryTile category={category} count={counts.get(category.id)} />
              </li>
            ))}
      </ul>
    </section>
  )
}

import type { Product } from '../../api/types'
import { ProductListItem } from '../../components/product/ProductListItem'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { UnderlinedHeading } from '../../components/ui/UnderlinedHeading'
import { useDeals } from '../../hooks/useDeals'
import { useProductSearch } from '../../hooks/useProductSearch'

const ITEMS_PER_COLUMN = 3

interface ProductColumnProps {
  title: string
  products: Product[]
  isPending: boolean
  error: unknown
  onRetry: () => void
}

function ProductColumn({ title, products, isPending, error, onRetry }: ProductColumnProps) {
  return (
    <section>
      <UnderlinedHeading>{title}</UnderlinedHeading>
      {error ? (
        <ErrorState compact title="Could not load" error={error} onRetry={onRetry} />
      ) : (
        <ul className="space-y-6">
          {isPending
            ? Array.from({ length: ITEMS_PER_COLUMN }, (_, index) => (
                <li key={index} className="flex items-center gap-4">
                  <Skeleton className="size-20 shrink-0 rounded-xl" />
                  <div className="flex-1 space-y-2">
                    <Skeleton className="h-4 w-4/5" />
                    <Skeleton className="h-4 w-1/3" />
                  </div>
                </li>
              ))
            : products.map((product) => (
                <li key={product.id}>
                  <ProductListItem product={product} />
                </li>
              ))}
        </ul>
      )}
    </section>
  )
}

/**
 * Four short lists. The sample's "Top Selling" and "Trending" need sales numbers the shop does not publish,
 * so these columns use what the API can sort by. "More deals" continues after the four "Deals of the Day".
 */
export function ProductColumns() {
  const newest = useProductSearch({ sort: 'NEWEST', size: ITEMS_PER_COLUMN })
  const topRated = useProductSearch({ sort: 'RATING', size: ITEMS_PER_COLUMN })
  const budget = useProductSearch({ sort: 'PRICE_LOW_TO_HIGH', inStockOnly: true, size: ITEMS_PER_COLUMN })
  const deals = useDeals()
  // Deals 5-7 when there are enough, so this column does not repeat the "Deals of the Day" cards
  const moreDeals = deals.deals.length > 4 ? deals.deals.slice(4, 4 + ITEMS_PER_COLUMN) : deals.deals.slice(0, ITEMS_PER_COLUMN)

  const columns = [
    { title: 'Recently added', query: newest, products: newest.data?.content ?? [] },
    { title: 'Top rated', query: topRated, products: topRated.data?.content ?? [] },
    { title: 'Budget picks', query: budget, products: budget.data?.content ?? [] },
    { title: 'More deals', query: deals, products: moreDeals },
  ]

  return (
    <div className="page-container mt-16 grid gap-10 sm:grid-cols-2 xl:grid-cols-4">
      {columns.map(({ title, query, products }) => (
        <ProductColumn key={title} title={title} products={products} isPending={query.isPending} error={query.error} onRetry={() => void query.refetch()} />
      ))}
    </div>
  )
}

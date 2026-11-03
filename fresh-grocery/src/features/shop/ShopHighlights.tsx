import type { ProductSearchParams } from '../../api/types'
import { ProductRail } from '../../components/product/ProductRail'
import { shopLink } from '../../utils/links'

/** Only products in stock: a highlight should be something you can buy. */
const HIGHLIGHTS: Array<{ title: string; search: ProductSearchParams }> = [
  { title: 'New arrivals', search: { sort: 'NEWEST', inStockOnly: true, size: 10 } },
  { title: 'Top rated products', search: { sort: 'RATING', inStockOnly: true, size: 10 } },
]

/**
 * "New arrivals" and "Top rated products" at the top of the shop: side by side on big screens, one above the other
 * below. "See all" sorts the whole shop the same way.
 */
export function ShopHighlights() {
  return (
    <div className="grid gap-4 xl:grid-cols-2">
      {HIGHLIGHTS.map(({ title, search }) => (
        <ProductRail key={title} title={title} search={search} seeAllTo={shopLink({ sort: search.sort })} />
      ))}
    </div>
  )
}

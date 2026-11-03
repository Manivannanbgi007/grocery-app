import { cn } from '../../utils/cn'
import { CategoryWidget } from './sidebar/CategoryWidget'
import { ProductListWidget } from './sidebar/ProductListWidget'
import { SearchWidget } from './sidebar/SearchWidget'
import { useShopFilters } from './useShopFilters'

export type SidebarWidgetName = 'search' | 'categories' | 'topRated'

/**
 * The shop's left column: the search and the categories. The store, brand, price and stock filters are above the
 * products (ShopFilterBar); New arrivals and Top rated are at the top of the products too (ShopHighlights).
 */
const SHOP_WIDGETS: SidebarWidgetName[] = ['search', 'categories']

interface ShopSidebarProps {
  /** which boxes to show, in this order (the product page shows only the categories and the top rated products) */
  widgets?: SidebarWidgetName[]
  /** false: the categories box loads no numbers of its own (see CategoryWidget) */
  categoryCounts?: boolean
  className?: string
}

/** The shop's left column. It reads and changes the filters in the URL, so it works on any page. */
export function ShopSidebar({ widgets = SHOP_WIDGETS, categoryCounts = true, className }: ShopSidebarProps) {
  // The search box keeps what you type; a new key resets it when the search text in the URL changes
  // (for example after "Clear all" or the Back button).
  const { filters } = useShopFilters()

  return (
    <aside id="shop-sidebar" aria-label="Browse the shop" className={cn('space-y-4', className)}>
      {widgets.map((widget) => {
        switch (widget) {
          case 'search':
            return <SearchWidget key={`search-${filters.q ?? ''}`} />
          case 'categories':
            return <CategoryWidget key="categories" showCounts={categoryCounts} />
          case 'topRated':
            return <ProductListWidget key="rated" title="Top rated products" search={{ sort: 'RATING', size: 5 }} />
        }
      })}
    </aside>
  )
}

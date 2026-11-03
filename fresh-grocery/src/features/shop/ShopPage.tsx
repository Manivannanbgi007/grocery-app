import { LayoutGrid } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { BreadcrumbBar, type Crumb } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { ProductGrid } from '../../components/product/ProductGrid'
import { Button } from '../../components/ui/Button'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Pagination } from '../../components/ui/Pagination'
import { useCategories } from '../../hooks/useCategories'
import { useProductSearch } from '../../hooks/useProductSearch'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { shopLink } from '../../utils/links'
import { ShopFilterBar } from './ShopFilterBar'
import { hasActiveFilters, SHOP_PAGE_SIZE, toProductSearch, type ShopFilters } from './shopFilters'
import { ShopHighlights } from './ShopHighlights'
import { ShopSidebar } from './ShopSidebar'
import { ShopToolbar } from './ShopToolbar'
import { useShopFilters } from './useShopFilters'

/** The page title follows the filters: 'Results for "milk"', "Fruits", "Amul", "Fresh Mart" or "All products". */
function pageHeading(filters: ShopFilters, categoryName: string | undefined, storeName: string | undefined): string {
  if (filters.q) return `Results for “${filters.q}”`
  if (categoryName) return categoryName
  if (filters.brand) return filters.brand
  if (storeName) return storeName
  return 'All products'
}

/**
 * Products in a grid with sorting and paging. The filters are in the URL (see shopFilters.ts): store, brand, price
 * and stock in the bar above the products (ShopFilterBar), the search and the categories in the column on the left.
 * On phones and tablets that column opens above the products with the "Categories" button.
 * The shop's first page without filters starts with New arrivals and Top rated products; a narrowed list starts with
 * the list (the sort menu has "Newest first" and "Top rated" for it).
 */
export function ShopPage() {
  const { filters, apply } = useShopFilters()
  const { data: categories = [] } = useCategories()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useProductSearch(toProductSearch(filters))
  const [filtersOpen, setFiltersOpen] = useState(false)

  const category = categories.find((item) => item.id === filters.category)
  const heading = pageHeading(filters, category?.name, useStoreName(filters.store))
  const crumbs: Crumb[] = category ? [{ label: 'Shop', to: '/shop' }, { label: category.name }] : [{ label: 'Shop' }]
  const pageIsEmpty = data !== undefined && data.content.length === 0
  const showHighlights = !hasActiveFilters(filters) && filters.page === 1

  return (
    <>
      <PageTitle title={heading} />
      <BreadcrumbBar crumbs={crumbs} />

      <div className="page-container mt-6">
        <div className="mb-5 flex items-center justify-between gap-4">
          <h1 className="text-2xl sm:text-3xl">{heading}</h1>
          <Button variant="outline" size="sm" className="lg:hidden" aria-expanded={filtersOpen} aria-controls="shop-sidebar" onClick={() => setFiltersOpen(!filtersOpen)}>
            <LayoutGrid className="size-4" aria-hidden="true" />
            {filtersOpen ? 'Hide categories' : 'Categories'}
          </Button>
        </div>

        <div className="grid items-start gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] xl:grid-cols-[17rem_minmax(0,1fr)]">
          {/* Always shown from lg; on smaller screens only after "Categories" (above the products, not below them) */}
          <div className={cn(!filtersOpen && 'hidden', 'lg:block')}>
            <ShopSidebar />
            <Button className="mt-4 w-full lg:hidden" onClick={() => setFiltersOpen(false)}>
              {data ? `Show ${data.totalElements} ${data.totalElements === 1 ? 'product' : 'products'}` : 'Show the products'}
            </Button>
          </div>

          <div className="min-w-0 space-y-5">
            {showHighlights && <ShopHighlights />}
            <ShopFilterBar />
            <ShopToolbar total={data?.totalElements} />

            {isError ? (
              <ErrorState title="Could not load the products" error={error} onRetry={() => void refetch()} />
            ) : pageIsEmpty && data.totalElements > 0 ? (
              // e.g. an old link to page 9 when there are now only 5 pages
              <EmptyState
                icon="📄"
                title="There is nothing on this page"
                action={
                  <Link to={shopLink({ ...filters, page: 1 })} className={buttonClasses()}>
                    Go to the first page
                  </Link>
                }
              />
            ) : pageIsEmpty ? (
              <EmptyState
                icon="🔍"
                title="No products match your filters"
                message="Try another word, a different category or a wider price range."
                action={
                  <Link to={shopLink({ sort: filters.sort })} className={buttonClasses()}>
                    Clear all filters
                  </Link>
                }
              />
            ) : (
              <ProductGrid
                products={data?.content}
                isPending={isPending}
                skeletonCount={SHOP_PAGE_SIZE}
                isUpdating={isPlaceholderData}
                className="lg:grid-cols-3 xl:grid-cols-4"
              />
            )}

            {data && (
              <Pagination
                page={filters.page}
                totalPages={data.totalPages}
                // A new page scrolls back to the top, so you start reading at its first products
                onPageChange={(page) => apply({ page }, false)}
                className="flex justify-center pt-4"
              />
            )}
          </div>
        </div>
      </div>
    </>
  )
}

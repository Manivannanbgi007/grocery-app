import { useState } from 'react'
import type { ProductSearchParams } from '../../api/types'
import { ProductGrid } from '../../components/product/ProductGrid'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { SectionTitle } from '../../components/ui/SectionTitle'
import { useCategories } from '../../hooks/useCategories'
import { useProductSearch } from '../../hooks/useProductSearch'
import { CategoryTabs } from './CategoryTabs'

const TAB_COUNT = 5
/** 12 fills every row: 2, 3, 4 or 6 small cards per row */
const PRODUCT_COUNT = 12

/**
 * The best-rated products, with buttons to narrow them to one category. Small cards, so many products fit on a screen.
 * A category's products are only loaded when its button is clicked (and then cached), to keep the number of requests low.
 */
export function PopularProducts() {
  const { data: categories = [] } = useCategories()
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined)

  const search: ProductSearchParams = { categoryId, sort: 'RATING', size: PRODUCT_COUNT }
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useProductSearch(search)

  return (
    <section aria-labelledby="popular-products" className="page-container mt-16">
      <SectionTitle id="popular-products" title="Popular Products" className="mb-4">
        <CategoryTabs label="Show popular products of" categories={categories.slice(0, TAB_COUNT)} selectedId={categoryId} onSelect={setCategoryId} showAll />
      </SectionTitle>

      {isError ? (
        <ErrorState title="Could not load the products" error={error} onRetry={() => void refetch()} />
      ) : data?.content.length === 0 ? (
        <EmptyState icon="🧺" title="No products in this category yet" />
      ) : (
        // While another category loads, the current products stay visible but faded
        <ProductGrid products={data?.content} isPending={isPending} skeletonCount={PRODUCT_COUNT} isUpdating={isPlaceholderData} cardSize="small" />
      )}
    </section>
  )
}

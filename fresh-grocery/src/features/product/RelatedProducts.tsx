import type { Product } from '../../api/types'
import { ProductGrid } from '../../components/product/ProductGrid'
import { SectionTitle } from '../../components/ui/SectionTitle'
import { useProductSearch } from '../../hooks/useProductSearch'

/** Four fill one row next to the product page's sidebar, or two rows of two */
const RELATED_COUNT = 4

/** Other products of the same category, best rated first (in the product page's main column, beside the sidebar). */
export function RelatedProducts({ product }: { product: Product }) {
  // One more than needed, because the product itself may be among them
  const { data, isPending } = useProductSearch({ categoryId: product.categoryId, sort: 'RATING', size: RELATED_COUNT + 1 })
  const related = (data?.content ?? []).filter((item) => item.id !== product.id).slice(0, RELATED_COUNT)

  if (!isPending && related.length === 0) return null

  return (
    <section aria-labelledby="related-products">
      <SectionTitle id="related-products" title="Related products" />
      <ProductGrid products={related} isPending={isPending} skeletonCount={RELATED_COUNT} className="md:grid-cols-2 lg:grid-cols-2 xl:grid-cols-4" />
    </section>
  )
}

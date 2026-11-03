import type { ProductSearchParams } from '../../../api/types'
import { ProductListItem } from '../../../components/product/ProductListItem'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { useProductSearch } from '../../../hooks/useProductSearch'
import { SidebarWidget } from './SidebarWidget'

interface ProductListWidgetProps {
  title: string
  /** which products to show, e.g. { sort: 'NEWEST', size: 5 } */
  search: ProductSearchParams
}

/** A short list of products in the sidebar ("New arrivals", "Top rated products"). */
export function ProductListWidget({ title, search }: ProductListWidgetProps) {
  const { data, isPending, error, refetch } = useProductSearch(search)

  return (
    <SidebarWidget title={title}>
      {error ? (
        <ErrorState compact title="Could not load" error={error} onRetry={() => void refetch()} />
      ) : (
        <ul className="space-y-4">
          {isPending
            ? Array.from({ length: search.size ?? 5 }, (_, index) => (
                <li key={index} className="flex items-center gap-4">
                  <Skeleton className="size-20 shrink-0 rounded-xl" />
                  <Skeleton className="h-4 flex-1" />
                </li>
              ))
            : data?.content.map((product) => (
                <li key={product.id}>
                  <ProductListItem product={product} />
                </li>
              ))}
        </ul>
      )}
    </SidebarWidget>
  )
}

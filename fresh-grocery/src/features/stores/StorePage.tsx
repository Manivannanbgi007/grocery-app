import { MapPin, Phone } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { isApiError } from '../../api/client'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { ProductGrid } from '../../components/product/ProductGrid'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { useProductSearch } from '../../hooks/useProductSearch'
import { useStore } from '../../hooks/useStores'
import { addressLines } from '../../utils/address'
import { cn } from '../../utils/cn'
import { shopLink } from '../../utils/links'
import { DAY_NAMES, formatHours, indiaWeekday } from '../../utils/openingHours'
import { StoreStatus } from './StoreStatus'

const TOP_PRODUCTS = 8

/** One store: address, phone, the week's opening hours and its best-rated products. /stores/:storeId */
export function StorePage() {
  const storeId = Number(useParams().storeId)
  const { data: store, isPending, isError, error, refetch } = useStore(storeId)
  const products = useProductSearch({ storeId, sort: 'RATING', size: TOP_PRODUCTS })
  const today = indiaWeekday()

  if (isPending) {
    return (
      <div className="page-container mt-8">
        <Skeleton className="h-96" />
      </div>
    )
  }
  if (isError) {
    return (
      <div className="page-container mt-8">
        {isApiError(error, 404) ? (
          <EmptyState
            icon="🏪"
            title="Store not found"
            message="This store does not exist or is not selling right now."
            action={
              <Link to="/stores" className={buttonClasses()}>
                All stores
              </Link>
            }
          />
        ) : (
          <ErrorState title="Could not load the store" error={error} onRetry={() => void refetch()} />
        )}
      </div>
    )
  }

  return (
    <>
      <PageTitle title={store.name} />
      <BreadcrumbBar crumbs={[{ label: 'Stores', to: '/stores' }, { label: store.name }]} />
      <div className="page-container mt-8 space-y-10">
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_22rem]">
          <section aria-labelledby="store-title">
            <div className="mb-2 flex flex-wrap items-center gap-3">
              <h1 id="store-title" className="text-3xl">
                {store.name}
              </h1>
              <StoreStatus store={store} />
            </div>
            {store.description && <p className="mb-4 text-lg">{store.description}</p>}
            <p className="flex gap-2">
              <MapPin className="mt-1 size-4 shrink-0 text-brand" aria-hidden="true" />
              <span>{addressLines({ ...store, landmark: null }).join(', ')}</span>
            </p>
            <p className="mt-2 flex gap-2">
              <Phone className="mt-1 size-4 shrink-0 text-brand" aria-hidden="true" />
              <a href={`tel:${store.phone}`} className="font-bold text-brand-text hover:underline">
                {store.phone}
              </a>
            </p>
            <Link to={shopLink({ store: store.id })} className={cn(buttonClasses(), 'mt-6')}>
              Shop at {store.name}
            </Link>
          </section>
          <section aria-labelledby="hours-title" className="rounded-2xl border border-line bg-card p-6">
            <h2 id="hours-title" className="mb-3 text-xl">
              Opening hours
            </h2>
            <dl className="text-sm">
              {store.openingHours.map((day) => (
                <div key={day.dayOfWeek} className={cn('flex justify-between gap-4 border-b border-line py-2 last:border-b-0', day.dayOfWeek === today && 'font-bold text-heading')}>
                  <dt>
                    {DAY_NAMES[day.dayOfWeek]}
                    {day.dayOfWeek === today && ' (today)'}
                  </dt>
                  <dd>{formatHours(day)}</dd>
                </div>
              ))}
            </dl>
          </section>
        </div>

        <section aria-labelledby="store-products-title">
          <div className="mb-4 flex flex-wrap items-baseline justify-between gap-3">
            <h2 id="store-products-title" className="text-2xl">
              Top rated at {store.name}
            </h2>
            <Link to={shopLink({ store: store.id })} className="font-bold text-brand-text hover:underline">
              All products of {store.name}
            </Link>
          </div>
          {products.isError ? (
            <ErrorState compact title="Could not load the products" error={products.error} onRetry={() => void products.refetch()} />
          ) : products.data?.content.length === 0 ? (
            <p>{store.name} has no products in the shop yet.</p>
          ) : (
            <ProductGrid products={products.data?.content} isPending={products.isPending} skeletonCount={TOP_PRODUCTS} className="lg:grid-cols-4" />
          )}
        </section>
      </div>
    </>
  )
}

import { MapPin } from 'lucide-react'
import { Link } from 'react-router'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { LoadingState } from '../../components/ui/LoadingState'
import { useStores } from '../../hooks/useStores'
import { addressLines } from '../../utils/address'
import { shopLink } from '../../utils/links'
import { formatHours, indiaWeekday } from '../../utils/openingHours'
import { StoreStatus } from './StoreStatus'

/** Every approved store: open or closed now, today's hours, the address, and its products. /stores */
export function StoresPage() {
  const { data: stores, isPending, isError, error, refetch } = useStores()
  const today = indiaWeekday()

  let content
  if (isPending) content = <LoadingState className="min-h-64" />
  else if (isError) content = <ErrorState title="Could not load the stores" error={error} onRetry={() => void refetch()} />
  else if (stores.length === 0) content = <EmptyState icon="🏪" title="No stores yet" className="rounded-2xl bg-card" />
  else {
    content = (
      <ul aria-label="Stores" className="grid gap-6 md:grid-cols-2">
        {stores.map((store) => {
          const todaysHours = store.openingHours.find((day) => day.dayOfWeek === today)
          return (
            <li key={store.id} className="flex flex-col rounded-2xl border border-line bg-card p-6 shadow-sm shadow-heading/5">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-2xl">
                  <Link to={`/stores/${store.id}`} className="hover:text-brand-text">
                    {store.name}
                  </Link>
                </h2>
                <StoreStatus store={store} />
              </div>
              {store.description && <p className="mb-3">{store.description}</p>}
              <p className="flex gap-2 text-sm">
                <MapPin className="mt-0.5 size-4 shrink-0 text-brand" aria-hidden="true" />
                <span>{addressLines({ ...store, landmark: null }).join(', ')}</span>
              </p>
              <p className="mt-1 text-sm">Today: {todaysHours ? formatHours(todaysHours) : 'Closed'}</p>
              <div className="mt-auto flex flex-wrap gap-3 pt-5">
                <Link to={shopLink({ store: store.id })} className={buttonClasses()}>
                  Shop at {store.name}
                </Link>
                <Link to={`/stores/${store.id}`} className={buttonClasses('outline')}>
                  Opening hours and details
                </Link>
              </div>
            </li>
          )
        })}
      </ul>
    )
  }

  return (
    <>
      <PageTitle title="Our stores" />
      <BreadcrumbBar crumbs={[{ label: 'Stores' }]} />
      <div className="page-container mt-8">
        <h1 className="mb-2 text-3xl">Our stores</h1>
        <p className="mb-6">Local shops sell through Fresh Grocery. A cart holds the products of one store, and every store delivers to its own area.</p>
        {content}
      </div>
    </>
  )
}

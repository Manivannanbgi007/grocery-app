import { ShoppingBasket, Store } from 'lucide-react'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { RankedListCard } from '../../../components/sales/RankedListCard'
import { reportPeriodOf } from '../../../components/sales/reportPeriod'
import { ReportPeriodForm } from '../../../components/sales/ReportPeriodForm'
import { OrderOutcomeCard } from '../../../components/sales/OrderOutcomeCard'
import { SalesPerDayCard } from '../../../components/sales/SalesPerDayCard'
import { SALES_DEFINITION } from '../../../components/sales/salesPeriod'
import { SalesTotals } from '../../../components/sales/SalesTotals'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { indiaDay } from '../../../utils/dates'
import { usePlatformSales, usePlatformTopProducts, useTopStores } from '../reports/useAdminReports'
import { useStoreOptions } from '../stores/useAdminStores'

const DEFAULT_DAYS = 30
const TOP_COUNT = 10

/**
 * Sales of any period (up to a year), of all stores or of one: totals, sales per day, top stores and best sellers.
 * /admin/reports?from=2026-10-01&to=2026-10-26&store=1 (the last 30 days by default)
 */
export function AdminReportsPage() {
  const { filter, idFilter, setFilter, setFilters } = useUrlFilters()
  const { from, to, problem } = reportPeriodOf(filter('from'), filter('to'), DEFAULT_DAYS)
  const storeId = idFilter('store')
  const today = indiaDay()
  const { data: stores = [] } = useStoreOptions()
  // Without a valid period nothing is asked for (the hooks get the last 30 days, which are never shown then)
  const sales = usePlatformSales(problem ? today : from, to, storeId)
  const topStores = useTopStores(problem ? today : from, to, TOP_COUNT)
  const topProducts = usePlatformTopProducts(problem ? today : from, to, TOP_COUNT, storeId)

  let content
  if (problem) content = <ErrorState compact title="This period cannot be shown" error={new Error(problem)} className="rounded-2xl bg-card" />
  else if (sales.isPending) content = <LoadingState className="min-h-96" />
  else if (sales.isError) content = <ErrorState compact title="Could not load the sales" error={sales.error} onRetry={() => void sales.refetch()} className="rounded-2xl bg-card" />
  else {
    const storeRows = topStores.data?.map((store) => ({ id: store.storeId, label: store.storeName, to: `/admin/stores/${store.storeId}`, detail: `${store.orders} orders`, sales: store.sales }))
    const productRows = topProducts.data?.map((product) => ({ id: product.productId, label: product.productName, to: `/products/${product.productId}`, detail: `${product.quantity} sold`, sales: product.sales, rankedBy: product.quantity }))
    content = (
      <div className={cn('space-y-4', (sales.isPlaceholderData || topStores.isPlaceholderData || topProducts.isPlaceholderData) && 'opacity-60')}>
        <SalesTotals sales={sales.data} />
        <SalesPerDayCard days={sales.data.days} updating={sales.isPlaceholderData} />
        <div className="grid items-start gap-4 lg:grid-cols-2 xl:grid-cols-3">
          <OrderOutcomeCard days={sales.data.days} />
          {storeId === undefined && (
            <RankedListCard title="Top stores" subtitle="By sales" icon={Store} tone="orange" rows={storeRows} error={topStores.error} onRetry={() => void topStores.refetch()} />
          )}
          <RankedListCard
            title="Best sellers"
            subtitle="By units sold"
            icon={ShoppingBasket}
            tone="green"
            rows={productRows}
            error={topProducts.error}
            onRetry={() => void topProducts.refetch()}
          />
        </div>
        <p className="text-xs">{SALES_DEFINITION}</p>
      </div>
    )
  }

  return (
    <>
      <PageTitle title="Reports" />
      <h1 className="mb-6 text-3xl">Reports</h1>
      <ReportPeriodForm from={from} to={to} onShow={(first, last) => setFilters({ from: first, to: last })}>
        <FilterSelect label="Store" allLabel="All stores" value={storeId ? String(storeId) : ''} options={stores.map((store) => ({ value: String(store.id), label: store.name }))} onChange={(value) => setFilter('store', value)} />
      </ReportPeriodForm>
      {content}
    </>
  )
}

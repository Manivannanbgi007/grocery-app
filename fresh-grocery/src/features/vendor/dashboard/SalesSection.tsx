import { ShoppingBasket, TrendingUp } from 'lucide-react'
import { useSearchParams } from 'react-router'
import { PeriodPicker } from '../../../components/sales/PeriodPicker'
import { RankedListCard } from '../../../components/sales/RankedListCard'
import { OrderOutcomeCard } from '../../../components/sales/OrderOutcomeCard'
import { SalesPerDayCard } from '../../../components/sales/SalesPerDayCard'
import { SALES_DEFINITION, salesPeriodOf } from '../../../components/sales/salesPeriod'
import { SalesTotals } from '../../../components/sales/SalesTotals'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { cn } from '../../../utils/cn'
import { useDailySales, useTopProducts } from '../reports/useVendorReports'

const TOP_COUNT = 5

/**
 * Sales of the last 7, 14 or 30 days (report-service): totals, sales per day and the best sellers. The period is in the
 * URL (?days=30) and scopes everything in the section, so the numbers always agree.
 */
export function SalesSection() {
  const [searchParams] = useSearchParams()
  const { days, from, to } = salesPeriodOf(searchParams)
  const sales = useDailySales(from, to)
  const top = useTopProducts(from, to, TOP_COUNT)

  let content
  if (sales.isPending) {
    content = <LoadingState className="min-h-96" />
  } else if (sales.isError) {
    content = <ErrorState compact title="Could not load the sales" error={sales.error} onRetry={() => void sales.refetch()} className="rounded-2xl bg-card" />
  } else {
    const bestSellers = top.data?.map((product) => ({
      id: product.productId,
      label: product.productName,
      to: `/vendor/products/${product.productId}`,
      detail: `${product.quantity} sold`,
      sales: product.sales,
      rankedBy: product.quantity,
    }))
    content = (
      // While another period loads, the previous one stays on screen, dimmed
      <div className={cn('space-y-4', (sales.isPlaceholderData || top.isPlaceholderData) && 'opacity-60')}>
        <SalesTotals sales={sales.data} />
        {/* The trend (two thirds) beside how the orders ended (one third), once the content is 896 px wide */}
        <div className="grid gap-4 @4xl:grid-cols-3">
          <SalesPerDayCard days={sales.data.days} updating={sales.isPlaceholderData} className="@4xl:col-span-2" />
          <OrderOutcomeCard days={sales.data.days} />
        </div>
        <RankedListCard
          title="Best sellers"
          subtitle="By units sold in this period"
          icon={ShoppingBasket}
          tone="green"
          rows={bestSellers}
          error={top.error}
          onRetry={() => void top.refetch()}
        />
        <p className="text-xs">{SALES_DEFINITION}</p>
      </div>
    )
  }

  return (
    <section aria-labelledby="sales-title">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="grid size-11 place-items-center rounded-xl bg-linear-to-br from-sale to-sale-text text-white shadow-lg shadow-sale/30">
            <TrendingUp className="size-5" aria-hidden="true" />
          </span>
          <div>
            <h2 id="sales-title" className="text-2xl">
              Sales
            </h2>
            <p className="text-sm">Your store, day by day</p>
          </div>
        </div>
        <PeriodPicker path="/vendor" days={days} />
      </div>
      {content}
    </section>
  )
}

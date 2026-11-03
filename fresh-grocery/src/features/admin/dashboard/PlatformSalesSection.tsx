import { CircleX, IndianRupee, Receipt, ShoppingBag, ShoppingBasket, Store, TrendingUp } from 'lucide-react'
import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { DayPicker, RangePicker } from '../../../components/sales/CustomPeriodPickers'
import { PeriodPicker } from '../../../components/sales/PeriodPicker'
import { RankedListCard } from '../../../components/sales/RankedListCard'
import { OrderOutcomeCard } from '../../../components/sales/OrderOutcomeCard'
import { SalesPerDayCard } from '../../../components/sales/SalesPerDayCard'
import { chosenPeriodOf, DEFAULT_SALES_PERIOD, periodLabel, SALES_DEFINITION } from '../../../components/sales/salesPeriod'
import { periodTotalsOf } from '../../../components/sales/periodTotals'
import { ButtonLink } from '../../../components/ui/Button'
import { EmojiIllustration } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { formatDayLabel } from '../../../utils/calendar'
import { cn } from '../../../utils/cn'
import { formatPrice } from '../../../utils/format'
import { usePlatformSales, usePlatformTopProducts, useTopStores } from '../reports/useAdminReports'
import { StatCard } from '../../../components/sales/StatCard'

const TOP_COUNT = 5

/** A message in place of the numbers: a period without orders, or dates that cannot be shown. */
function PeriodMessage({ emoji, title, warn = false, children }: { emoji: string; title: string; warn?: boolean; children: ReactNode }) {
  return (
    <div role={warn ? 'alert' : undefined} className="flex flex-col items-center gap-3 rounded-2xl border border-line bg-card px-4 py-12 text-center">
      <EmojiIllustration emoji={emoji} tone={warn ? 'warm' : 'fresh'} />
      <h3 className="mt-2 text-xl">{title}</h3>
      {children}
    </div>
  )
}

/** Grey blocks in the shape of the section (tiles, chart, ring) while its first numbers load. */
function SalesLoading() {
  return (
    <div role="status" aria-label="Loading the sales" className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:gap-4 @3xl:grid-cols-4">
        {[0, 1, 2, 3].map((tile) => (
          <Skeleton key={tile} className="h-28 rounded-2xl" />
        ))}
      </div>
      <div className="grid gap-4 @4xl:grid-cols-3">
        <Skeleton className="h-96 rounded-2xl @4xl:col-span-2" />
        <Skeleton className="h-96 rounded-2xl" />
      </div>
    </div>
  )
}

/** The sales of one valid period: totals, sales per day beside how the orders ended, the top stores and the best sellers. */
function PlatformSales({ from, to }: { from: string; to: string }) {
  const sales = usePlatformSales(from, to)
  const topStores = useTopStores(from, to, TOP_COUNT)
  const topProducts = usePlatformTopProducts(from, to, TOP_COUNT)

  if (sales.isPending) return <SalesLoading />
  if (sales.isError) return <ErrorState title="Could not load the sales" error={sales.error} onRetry={() => void sales.refetch()} className="rounded-2xl border border-line bg-card" />

  const totals = periodTotalsOf(sales.data)
  const loadingAnother = sales.isPlaceholderData || topStores.isPlaceholderData || topProducts.isPlaceholderData

  // Not a single order in the period: one message instead of zeros, a flat chart and empty lists
  if (totals.orders === 0) {
    const shown = sales.data
    return (
      <div className={cn('transition-opacity', loadingAnother && 'opacity-60')}>
        <PeriodMessage emoji="📊" title={shown.from === shown.to ? 'No sales on this day' : 'No sales in this period'}>
          <p className="max-w-md">
            No orders were placed {shown.from === shown.to ? `on ${formatDayLabel(shown.from)}` : `from ${formatDayLabel(shown.from)} to ${formatDayLabel(shown.to)}`}. Pick another day
            or a longer period.
          </p>
        </PeriodMessage>
      </div>
    )
  }

  const storeRows = topStores.data?.map((store) => ({
    id: store.storeId,
    label: store.storeName,
    to: `/admin/stores/${store.storeId}`,
    detail: `${store.orders} ${store.orders === 1 ? 'order' : 'orders'}`,
    sales: store.sales,
  }))
  // A product opens in the shop, the way customers see it
  const productRows = topProducts.data?.map((product) => ({
    id: product.productId,
    label: product.productName,
    to: `/products/${product.productId}`,
    detail: `${product.quantity} sold`,
    sales: product.sales,
    rankedBy: product.quantity,
  }))
  // The reports page shows the same period with the top 10
  const fullReport = { to: `/admin/reports?from=${from}&to=${to}`, label: 'Full report' }
  return (
    // While another period loads, the previous one stays on screen, dimmed
    <div className={cn('space-y-4 transition-opacity', loadingAnother && 'opacity-60')}>
      <div className="grid grid-cols-2 gap-3 sm:gap-4 @3xl:grid-cols-4">
        <StatCard label="Sales" value={totals.sales} format={formatPrice} icon={IndianRupee} accent="blue" />
        <StatCard label="Orders" value={totals.orders} icon={ShoppingBag} accent="green" />
        <StatCard label="Cancelled or rejected" value={totals.cancelled} icon={CircleX} accent="pink" />
        <StatCard label="Average order" value={totals.averageOrder} format={formatPrice} icon={Receipt} accent="orange" />
      </div>
      {/* The trend (two thirds) beside how the orders ended (one third), once the content is 896 px wide */}
      <div className="grid gap-4 @4xl:grid-cols-3">
        <SalesPerDayCard days={sales.data.days} updating={sales.isPlaceholderData} className="@4xl:col-span-2" />
        <OrderOutcomeCard days={sales.data.days} />
      </div>
      {/* Side by side at the same height once the content is 672 px wide */}
      <div className="grid gap-4 @2xl:grid-cols-2">
        <RankedListCard
          title="Top stores"
          subtitle="By sales in this period"
          icon={Store}
          tone="orange"
          rows={storeRows}
          error={topStores.error}
          onRetry={() => void topStores.refetch()}
          more={fullReport}
        />
        <RankedListCard
          title="Best sellers"
          subtitle="By units sold in this period"
          icon={ShoppingBasket}
          tone="green"
          rows={productRows}
          error={topProducts.error}
          onRetry={() => void topProducts.refetch()}
          more={fullReport}
        />
      </div>
      <p className="text-xs">{SALES_DEFINITION}</p>
    </div>
  )
}

/**
 * The platform's sales of a period: the last 7, 14 or 30 days (tabs), one day or a custom range (calendars). The
 * period is in the URL (?days=30, or ?from=2026-10-01&to=2026-10-20) and scopes everything in the section.
 */
export function PlatformSalesSection() {
  const [searchParams, setSearchParams] = useSearchParams()
  const period = chosenPeriodOf(searchParams)

  // A day or a range from a calendar goes into the URL, like the tabs' ?days=30
  function showPeriod(from: string, to: string) {
    setSearchParams({ from, to }, { preventScrollReset: true })
  }

  return (
    <section aria-labelledby="sales-title">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-linear-to-br from-sale to-sale-text text-white shadow-lg shadow-sale/30">
            <TrendingUp className="size-5" aria-hidden="true" />
          </span>
          <div>
            <h2 id="sales-title" className="text-2xl">
              Sales
            </h2>
            <p className="text-sm">
              The whole platform, day by day
              {period.problem === null && (
                <>
                  {' · '}
                  <strong className="text-heading">{periodLabel(period.from, period.to)}</strong>
                </>
              )}
            </p>
          </div>
        </div>
        <PeriodPicker path="/admin" days={period.kind === 'last' ? period.days : null}>
          <DayPicker period={period} onChoose={showPeriod} />
          <RangePicker period={period} onChoose={showPeriod} />
        </PeriodPicker>
      </div>
      {period.problem ? (
        <PeriodMessage emoji="📅" title="These dates cannot be shown" warn>
          <p className="max-w-md">{period.problem}</p>
          <ButtonLink to="/admin" variant="light" size="sm" preventScrollReset>
            Show the last {DEFAULT_SALES_PERIOD} days
          </ButtonLink>
        </PeriodMessage>
      ) : (
        <PlatformSales from={period.from} to={period.to} />
      )}
    </section>
  )
}

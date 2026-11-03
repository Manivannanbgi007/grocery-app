import { PackageCheck, Percent, ShoppingBasket, Store } from 'lucide-react'
import { Link } from 'react-router'
import { PageTitle } from '../../../../components/layout/PageTitle'
import { KpiTile } from '../../../../components/sales/KpiTile'
import { reportPeriodOf } from '../../../../components/sales/reportPeriod'
import { ReportPeriodForm } from '../../../../components/sales/ReportPeriodForm'
import { EmptyState } from '../../../../components/ui/EmptyState'
import { ErrorState } from '../../../../components/ui/ErrorState'
import { LoadingState } from '../../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../../hooks/useUrlFilters'
import { cn } from '../../../../utils/cn'
import { formatShortDay } from '../../../../utils/dates'
import { formatPrice } from '../../../../utils/format'
import { useCommissionReport } from './useCommission'

const DEFAULT_DAYS = 30

/** "10%", or "9.5%": the commission as a share of the items (it differs when the percentage changed in the period). */
function shareOf(commission: number, itemsTotal: number): string {
  if (itemsTotal === 0) return '–'
  return `${Math.round((commission / itemsTotal) * 1000) / 10}%`
}

/**
 * What the platform earned: the commission per store on the orders delivered in a period, and what the stores get.
 * /admin/commission?from=2026-10-01&to=2026-10-26 (the last 30 days by default)
 */
export function AdminCommissionPage() {
  const { filter, setFilters } = useUrlFilters()
  const { from, to, problem } = reportPeriodOf(filter('from'), filter('to'), DEFAULT_DAYS)
  const { data: report, isPending, isError, error, refetch, isPlaceholderData } = useCommissionReport(from, to, problem === null)

  let content
  if (problem) content = <ErrorState compact title="This period cannot be shown" error={new Error(problem)} className="rounded-2xl bg-card" />
  else if (isPending) content = <LoadingState className="min-h-96" />
  else if (isError) content = <ErrorState title="Could not load the commission report" error={error} onRetry={() => void refetch()} />
  else {
    const deliveredOrders = report.stores.reduce((sum, store) => sum + store.deliveredOrders, 0)
    content = (
      <div className={cn('space-y-4', isPlaceholderData && 'opacity-60')}>
        <div className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
          <KpiTile label="Platform commission" value={formatPrice(report.totalCommission)} icon={Percent} accent="green" />
          <KpiTile label="Paid to the stores" value={formatPrice(report.totalStorePayout)} icon={Store} accent="blue" />
          <KpiTile label="Items sold" value={formatPrice(report.totalItems)} icon={ShoppingBasket} accent="orange" />
          <KpiTile label="Delivered orders" value={String(deliveredOrders)} icon={PackageCheck} accent="green" />
        </div>
        {report.stores.length === 0 ? (
          <EmptyState icon="🧾" title="No delivered orders in this period" message="Commission is earned when an order is delivered." className="rounded-2xl bg-card" />
        ) : (
          <div className="relative overflow-x-auto rounded-2xl border border-line bg-card">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <caption className="sr-only">
                Commission per store, {formatShortDay(from)} to {formatShortDay(to)}
              </caption>
              <thead className="border-b border-line bg-surface font-heading text-heading">
                <tr>
                  <th scope="col" className="px-4 py-3">Store</th>
                  <th scope="col" className="px-4 py-3 text-right">Delivered orders</th>
                  <th scope="col" className="px-4 py-3 text-right">Items</th>
                  <th scope="col" className="px-4 py-3 text-right">Commission</th>
                  <th scope="col" className="px-4 py-3 text-right">Share</th>
                  <th scope="col" className="px-4 py-3 text-right">Store gets</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {report.stores.map((store) => (
                  <tr key={store.storeId}>
                    <th scope="row" className="px-4 py-3 font-normal">
                      <Link to={`/admin/stores/${store.storeId}`} className="font-heading font-bold whitespace-nowrap text-brand-text hover:underline">
                        {store.storeName}
                      </Link>
                    </th>
                    <td className="px-4 py-3 text-right">{store.deliveredOrders}</td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">{formatPrice(store.itemsTotal)}</td>
                    <td className="px-4 py-3 text-right font-bold whitespace-nowrap text-heading">{formatPrice(store.commission)}</td>
                    <td className="px-4 py-3 text-right">{shareOf(store.commission, store.itemsTotal)}</td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">{formatPrice(store.storePayout)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="border-t border-line bg-surface font-bold text-heading">
                <tr>
                  <th scope="row" className="px-4 py-3">
                    All stores
                  </th>
                  <td className="px-4 py-3 text-right">{deliveredOrders}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">{formatPrice(report.totalItems)}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">{formatPrice(report.totalCommission)}</td>
                  <td className="px-4 py-3 text-right">{shareOf(report.totalCommission, report.totalItems)}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">{formatPrice(report.totalStorePayout)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        )}
        <p className="text-xs">
          An order counts on the day it was delivered. Commission = the order&apos;s items total × the store&apos;s commission when the order was placed; delivery fees are not
          part of it. Set a store&apos;s commission on its store page.
        </p>
      </div>
    )
  }

  return (
    <>
      <PageTitle title="Commission" />
      <h1 className="mb-6 text-3xl">Commission</h1>
      <ReportPeriodForm from={from} to={to} onShow={(first, last) => setFilters({ from: first, to: last })} />
      {content}
    </>
  )
}

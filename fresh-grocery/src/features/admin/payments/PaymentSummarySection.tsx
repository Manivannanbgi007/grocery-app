import { Banknote, CreditCard, IndianRupee, RotateCcw, Wallet } from 'lucide-react'
import { useSearchParams } from 'react-router'
import type { PaymentStatus, PaymentTotals } from '../../../api/types'
import { DonutChart, type DonutPart, type DonutTone } from '../../../components/charts/DonutChart'
import { PeriodPicker } from '../../../components/sales/PeriodPicker'
import { StatCard } from '../../../components/sales/StatCard'
import { DEFAULT_SALES_PERIOD, salesPeriodOf } from '../../../components/sales/salesPeriod'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatPrice } from '../../../utils/format'
import { PaymentLinesCard } from './PaymentLinesCard'
import { usePaymentSummary } from './useAdminPayments'

/** The payment statuses in four groups (a ring stays readable with at most four parts). */
const STATUS_GROUPS: { key: string; label: string; tone: DonutTone; statuses: PaymentStatus[] }[] = [
  { key: 'paid', label: 'Paid or cash collected', tone: 'good', statuses: ['PAID', 'COLLECTED'] },
  { key: 'open', label: 'Not paid yet', tone: 'wait', statuses: ['PENDING', 'AWAITING_PAYMENT', 'CASH_ON_DELIVERY'] },
  { key: 'stopped', label: 'Failed or cancelled', tone: 'bad', statuses: ['FAILED', 'CANCELLED'] },
  { key: 'refunded', label: 'Refunded', tone: 'info', statuses: ['REFUNDED'] },
]

/** "Payments by status": how many payments of the period are paid, open, failed or refunded (the table next to it has every line). */
function PaymentStatusCard({ totals }: { totals: PaymentTotals[] }) {
  const parts: DonutPart[] = STATUS_GROUPS.map((group) => {
    const lines = totals.filter((line) => group.statuses.includes(line.status))
    return {
      key: group.key,
      label: group.label,
      tone: group.tone,
      value: lines.reduce((sum, line) => sum + line.count, 0),
      detail: formatPrice(lines.reduce((sum, line) => sum + line.amount, 0)),
    }
  })
  return (
    <section aria-labelledby="payment-status-title" className="rounded-2xl border border-line bg-card p-4 sm:p-6">
      <h3 id="payment-status-title" className="text-lg">
        Payments by status
      </h3>
      <p className="mb-4 text-xs">The number of payments, and their amount</p>
      <DonutChart label="Payments by status" countWords={['payment', 'payments']} parts={parts} />
    </section>
  )
}


/** Money in and out of the payments created in the last 7, 14 or 30 days (?days= in the URL, the list's filters stay). */
export function PaymentSummarySection() {
  const [searchParams] = useSearchParams()
  const { linkWith } = useUrlFilters()
  const { days, from, to } = salesPeriodOf(searchParams)
  const summary = usePaymentSummary(from, to)

  let content
  if (summary.isPending) content = <LoadingState className="min-h-40" />
  else if (summary.isError) content = <ErrorState compact title="Could not load the money summary" error={summary.error} onRetry={() => void summary.refetch()} className="rounded-2xl bg-card" />
  else {
    const { paidOnline, collectedCash, refunded, received, totals } = summary.data
    content = (
      <div className={cn('space-y-4', summary.isPlaceholderData && 'opacity-60')}>
        <div className="grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
          <StatCard label="Received" value={received} format={formatPrice} hint="paid online + cash collected" icon={Wallet} accent="green" />
          <StatCard label="Paid online" value={paidOnline} format={formatPrice} hint="and not refunded" icon={CreditCard} accent="blue" />
          <StatCard label="Cash collected" value={collectedCash} format={formatPrice} hint="by the delivery partners" icon={Banknote} accent="orange" />
          <StatCard label="Refunded" value={refunded} format={formatPrice} hint="online payments given back" icon={RotateCcw} accent="pink" />
        </div>
        {totals.length > 0 && (
          // The ring and the same numbers line by line (its table view), side by side at the same height
          <div className="grid gap-4 lg:grid-cols-2">
            <PaymentStatusCard totals={totals} />
            <PaymentLinesCard totals={totals} />
          </div>
        )}
        <p className="text-xs">Payments created in the period (India time), whatever happened to them since.</p>
      </div>
    )
  }

  return (
    <section aria-labelledby="money-title" className="mb-10">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="grid size-10 place-items-center rounded-xl bg-linear-to-br from-brand to-brand-dark text-white shadow-lg shadow-brand/30">
            <IndianRupee className="size-5" aria-hidden="true" />
          </span>
          <h2 id="money-title" className="text-2xl">
            Money
          </h2>
        </div>
        <PeriodPicker path="/admin/payments" days={days} linkFor={(period) => linkWith('days', period === DEFAULT_SALES_PERIOD ? '' : String(period))} />
      </div>
      {content}
    </section>
  )
}

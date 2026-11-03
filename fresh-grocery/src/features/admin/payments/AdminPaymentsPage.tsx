import { Banknote, CreditCard } from 'lucide-react'
import { Link } from 'react-router'
import type { Payment, PaymentMethod, PaymentStatus } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterBar } from '../../../components/panel/FilterBar'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { PageHeader } from '../../../components/panel/PageHeader'
import { CELL, HEAD_CELL, ROW_ICON, TABLE_CARD, TABLE_HEAD } from '../../../components/panel/tableClasses'
import { ButtonLink } from '../../../components/ui/Button'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { LoadingState } from '../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatDateTime, formatPrice } from '../../../utils/format'
import { PAYMENT_METHOD_LABELS } from '../../orders/orderStatus'
import { useStoreOptions } from '../stores/useAdminStores'
import { PaymentsIllustration } from '../illustrations/PaymentsIllustration'
import { PAYMENT_STATUS_FILTERS, PAYMENT_STATUS_ICONS, PAYMENT_STATUS_LABELS, PAYMENT_STATUS_TONES } from './paymentStatus'
import { PaymentSummarySection } from './PaymentSummarySection'
import { useAdminPayments } from './useAdminPayments'

const METHODS: PaymentMethod[] = ['ONLINE', 'COD']

/** What else is worth knowing about a payment: the refund reference, why it failed, when it was paid. */
function paymentDetail(payment: Payment): string {
  if (payment.status === 'REFUNDED') return `Refund ${payment.refundReference ?? ''}${payment.refundedAt ? ` on ${formatDateTime(payment.refundedAt)}` : ''}`
  if (payment.status === 'FAILED') return payment.failureReason ?? ''
  if (payment.paidAt) return `Paid on ${formatDateTime(payment.paidAt)}`
  if (payment.status === 'AWAITING_PAYMENT' && payment.payBefore) return `Pay before ${formatDateTime(payment.payBefore)}`
  return ''
}

/** The money summary of a period, then every payment, newest first. /admin/payments?days=30&status=REFUNDED&method=ONLINE&store=1 */
export function AdminPaymentsPage() {
  const { page, pageSize, filter, idFilter, setFilter, goToPage, setPageSize } = useUrlFilters()
  const status = PAYMENT_STATUS_FILTERS.find((candidate) => candidate === filter('status'))
  const method = METHODS.find((candidate) => candidate === filter('method'))
  const storeId = idFilter('store')
  const { data: stores = [] } = useStoreOptions()
  const storeName = (id: number) => stores.find((store) => store.id === id)?.name ?? `Store #${id}`
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAdminPayments({ status, method, storeId }, page, pageSize)
  const filtered = Boolean(status || method || storeId)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the payments" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon={<PaymentsIllustration className="h-32 w-auto" />}
        title={filtered ? 'No payments match these filters' : 'No payments yet'}
        action={filtered ? <ButtonLink to="/admin/payments">Clear the filters</ButtonLink> : undefined}
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['payment', 'payments']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn(TABLE_CARD, isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[56rem] text-left text-sm">
            <caption className="sr-only">Payments, newest first ({data.totalElements})</caption>
            <thead className={TABLE_HEAD}>
              <tr>
                <th scope="col" className={HEAD_CELL}>Payment</th>
                <th scope="col" className={HEAD_CELL}>Status</th>
                <th scope="col" className={HEAD_CELL}>Amount</th>
                <th scope="col" className={HEAD_CELL}>Order</th>
                <th scope="col" className={HEAD_CELL}>Store</th>
                <th scope="col" className={HEAD_CELL}>Created</th>
                <th scope="col" className={HEAD_CELL}>Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((payment) => {
                const online = payment.method === 'ONLINE'
                const MethodIcon = online ? CreditCard : Banknote
                return (
                  <tr key={payment.id}>
                    <td className={cn(CELL, 'whitespace-nowrap')}>
                      {/* The method's icon: a card (online) or a note (cash) */}
                      <div className="flex items-center gap-3">
                        <span className={cn(ROW_ICON, online ? 'bg-sale-light text-sale-text' : 'bg-new-light text-warn-text')}>
                          <MethodIcon className="size-5" aria-hidden="true" />
                        </span>
                        <div>
                          <span className="font-heading font-bold text-heading">#{payment.id}</span>
                          <span className="block text-xs">{PAYMENT_METHOD_LABELS[payment.method]}</span>
                        </div>
                      </div>
                    </td>
                    <td className={CELL}>
                      <StatusBadge tone={PAYMENT_STATUS_TONES[payment.status]} icon={PAYMENT_STATUS_ICONS[payment.status]}>
                        {PAYMENT_STATUS_LABELS[payment.status]}
                      </StatusBadge>
                    </td>
                    <td className={cn(CELL, 'font-heading text-base font-bold text-heading tabular-nums')}>{formatPrice(payment.amount)}</td>
                    <td className={CELL}>
                      <Link to={`/admin/orders/${payment.orderId}`} className="font-bold whitespace-nowrap text-brand-text hover:underline">
                        Order #{payment.orderId}
                      </Link>
                    </td>
                    <td className={CELL}>{storeName(payment.storeId)}</td>
                    <td className={cn(CELL, 'whitespace-nowrap')}>{formatDateTime(payment.createdAt)}</td>
                    <td className={cn(CELL, 'text-xs')}>{paymentDetail(payment)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['payment', 'payments']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Payments" />
      <PageHeader
        title="Payments"
        icon={CreditCard}
        accent="green"
        description="Every payment of every order: paid online, cash collected by the partners, and what was given back."
        illustration={<PaymentsIllustration />}
      />
      <PaymentSummarySection />
      <h2 className="mb-4 text-2xl">Every payment</h2>
      <FilterBar>
        <FilterSelect label="Payment status" allLabel="All statuses" value={status ?? ''} options={PAYMENT_STATUS_FILTERS.map((value: PaymentStatus) => ({ value, label: PAYMENT_STATUS_LABELS[value] }))} onChange={(value) => setFilter('status', value)} />
        <FilterSelect label="Payment method" allLabel="Online and cash" value={method ?? ''} options={METHODS.map((value) => ({ value, label: PAYMENT_METHOD_LABELS[value] }))} onChange={(value) => setFilter('method', value)} />
        <FilterSelect label="Store" allLabel="All stores" value={storeId ? String(storeId) : ''} options={stores.map((store) => ({ value: String(store.id), label: store.name }))} onChange={(value) => setFilter('store', value)} />
      </FilterBar>
      {content}
    </>
  )
}

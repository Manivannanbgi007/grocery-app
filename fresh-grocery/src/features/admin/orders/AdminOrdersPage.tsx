import { Package } from 'lucide-react'
import { Link } from 'react-router'
import { GroceryBasketIllustration } from '../../../components/auth/illustrations/GroceryBasketIllustration'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterBar } from '../../../components/panel/FilterBar'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { PageHeader } from '../../../components/panel/PageHeader'
import { SearchBox } from '../../../components/panel/SearchBox'
import { StatusTabs } from '../../../components/panel/StatusTabs'
import { CELL, HEAD_CELL, ROW_ICON, TABLE_CARD, TABLE_HEAD } from '../../../components/panel/tableClasses'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { TablePagination } from '../../../components/ui/TablePagination'
import { LoadingState } from '../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatDateTime, formatPrice } from '../../../utils/format'
import { ORDER_STATUS_LABELS, ORDER_STATUS_TONES, PAYMENT_METHOD_LABELS } from '../../orders/orderStatus'
import { useStoreOptions } from '../stores/useAdminStores'
import { ADMIN_ORDER_TABS } from './adminOrderStatus'
import { useAdminOrders } from './useAdminOrders'

/**
 * Every order of the platform, newest first: by status, store and customer account.
 * /admin/orders?tab=waiting&store=1&customer=4&page=2
 */
export function AdminOrdersPage() {
  const { page, pageSize, filter, idFilter, setFilter, linkWith, goToPage, setPageSize } = useUrlFilters()
  const tab = ADMIN_ORDER_TABS.find((candidate) => candidate.key === filter('tab')) ?? ADMIN_ORDER_TABS[0]
  const storeId = idFilter('store')
  const customerUserId = idFilter('customer')
  const { data: stores = [] } = useStoreOptions()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAdminOrders({ status: tab.status, storeId, customerUserId }, page, pageSize)
  const filtered = storeId !== undefined || customerUserId !== undefined

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the orders" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon="📦"
        title={filtered ? 'No orders match these filters' : `No ${tab.key === 'all' ? '' : `${tab.label.toLowerCase()} `}orders`}
        action={filtered ? <ButtonLink to="/admin/orders">Clear the filters</ButtonLink> : undefined}
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['order', 'orders']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn(TABLE_CARD, isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[52rem] text-left text-sm">
            <caption className="sr-only">
              {tab.label} orders, newest first ({data.totalElements})
            </caption>
            <thead className={TABLE_HEAD}>
              <tr>
                <th scope="col" className={HEAD_CELL}>Order</th>
                <th scope="col" className={HEAD_CELL}>Status</th>
                <th scope="col" className={HEAD_CELL}>Store</th>
                <th scope="col" className={HEAD_CELL}>Customer</th>
                <th scope="col" className={HEAD_CELL}>Placed</th>
                <th scope="col" className={HEAD_CELL}>Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((order) => (
                <tr key={order.id}>
                  <td className={CELL}>
                    <div className="flex items-center gap-3">
                      <span className={cn(ROW_ICON, 'bg-brand-light text-brand-text')}>
                        <Package className="size-5" aria-hidden="true" />
                      </span>
                      <div>
                        <Link to={`/admin/orders/${order.id}`} className="font-heading font-bold whitespace-nowrap text-brand-text hover:underline">
                          Order #{order.id}
                        </Link>
                        <span className="block text-xs">
                          {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}
                        </span>
                      </div>
                    </div>
                  </td>
                  <td className={CELL}>
                    <StatusBadge tone={ORDER_STATUS_TONES[order.status]}>{ORDER_STATUS_LABELS[order.status]}</StatusBadge>
                  </td>
                  <td className={CELL}>
                    <Link to={`/admin/stores/${order.storeId}`} className="hover:text-brand-text hover:underline">
                      {order.storeName}
                    </Link>
                  </td>
                  <td className={CELL}>
                    <Link to={`/admin/accounts/${order.customerUserId}`} className="whitespace-nowrap hover:text-brand-text hover:underline">
                      Account #{order.customerUserId}
                    </Link>
                  </td>
                  <td className={cn(CELL, 'whitespace-nowrap')}>{formatDateTime(order.placedAt)}</td>
                  <td className={CELL}>
                    <span className="font-bold text-heading">{formatPrice(order.totalAmount)}</span>
                    <span className="block text-xs">{PAYMENT_METHOD_LABELS[order.paymentMethod]}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['order', 'orders']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Orders" />
      <PageHeader
        title="Orders"
        icon={Package}
        accent="green"
        description="Every order on the platform, newest first. Choose a status, a store or a customer account; open an order to see its steps, the payment and the delivery."
        illustration={<GroceryBasketIllustration />}
      />
      {/* Each tab has the colour of its status in the list below; lines part all | on their way | finished */}
      <StatusTabs
        label="Order status"
        active={tab.key}
        tabs={ADMIN_ORDER_TABS.map((candidate) => ({
          key: candidate.key,
          label: candidate.label,
          to: linkWith('tab', candidate.key === 'all' ? '' : candidate.key),
          tone: candidate.status ? ORDER_STATUS_TONES[candidate.status] : undefined,
          group: candidate.group,
        }))}
      />
      <FilterBar>
        <FilterSelect label="Store" allLabel="All stores" value={storeId ? String(storeId) : ''} options={stores.map((store) => ({ value: String(store.id), label: store.name }))} onChange={(value) => setFilter('store', value)} />
        <SearchBox label="Orders of one customer account" placeholder="Customer account #" value={customerUserId ? String(customerUserId) : ''} onSearch={(value) => setFilter('customer', value.replace(/\D/g, ''))} />
      </FilterBar>
      {content}
    </>
  )
}

import { AlertTriangle, Banknote, Bike, CircleCheck, Clock, CreditCard, MapPin, PackageX, Store, Truck } from 'lucide-react'
import { Link } from 'react-router'
import { DeliveryRiderIllustration } from '../../../components/auth/illustrations/DeliveryRiderIllustration'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PageHeader } from '../../../components/panel/PageHeader'
import { StatusTabs } from '../../../components/panel/StatusTabs'
import { CELL, HEAD_CELL, ROW_ICON, TABLE_CARD, TABLE_HEAD } from '../../../components/panel/tableClasses'
import { Avatar } from '../../../components/profile/Avatar'
import { StatCard } from '../../../components/sales/StatCard'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatDateTime, formatPrice } from '../../../utils/format'
import { useDeliveryCount } from '../useWorkCounts'
import { AssignPartnerButton } from './AssignPartnerButton'
import { DELIVERY_ISSUE_LABELS, DELIVERY_STATUS_LABELS, DELIVERY_STATUS_TONES, DELIVERY_TABS } from './deliveryStatus'
import { useAdminDeliveries, usePartnerList } from './useAdminDeliveries'

/**
 * Every delivery job, newest first: the ones nobody took first, with "Assign" to give them to a partner. Above the
 * list, four cards with the real number of deliveries in the main states; each opens its tab.
 * /admin/deliveries?tab=delivered&page=2
 */
export function AdminDeliveriesPage() {
  const { page, pageSize, filter, linkWith, goToPage, setPageSize } = useUrlFilters()
  const tab = DELIVERY_TABS.find((candidate) => candidate.key === filter('tab')) ?? DELIVERY_TABS[0]
  const { data: waiting } = useDeliveryCount('WAITING_FOR_PARTNER')
  const { data: onTheWay } = useDeliveryCount('OUT_FOR_DELIVERY')
  const { data: delivered } = useDeliveryCount('DELIVERED')
  const { data: failed } = useDeliveryCount('FAILED')
  const { data: partners = [] } = usePartnerList()
  const partnerName = (userId: number) => partners.find((partner) => partner.userId === userId)?.fullName ?? `Partner #${userId}`
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAdminDeliveries(tab.status, page, pageSize)
  /** The list with another tab (the first tab is the default, so it is left out). */
  const tabLink = (key: string) => linkWith('tab', key === DELIVERY_TABS[0].key ? '' : key)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the deliveries" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon={<DeliveryRiderIllustration className="h-32 w-auto" />}
        title={tab.key === 'waiting' ? 'Every delivery has a partner.' : `No ${tab.key === 'all' ? '' : `${tab.label.toLowerCase()} `}deliveries`}
        className="rounded-2xl border border-line bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['delivery', 'deliveries']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn(TABLE_CARD, isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[60rem] text-left text-sm">
            <caption className="sr-only">
              {tab.label} deliveries, newest first ({data.totalElements})
            </caption>
            <thead className={TABLE_HEAD}>
              <tr>
                <th scope="col" className={HEAD_CELL}>Order</th>
                <th scope="col" className={HEAD_CELL}>Status</th>
                <th scope="col" className={HEAD_CELL}>From → to</th>
                <th scope="col" className={HEAD_CELL}>Partner</th>
                <th scope="col" className={HEAD_CELL}>Cash</th>
                <th scope="col" className={HEAD_CELL}>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((delivery) => (
                <tr key={delivery.id}>
                  <td className={CELL}>
                    <div className="flex items-center gap-3">
                      <span className={cn(ROW_ICON, 'bg-sale-light text-sale-text')}>
                        <Bike className="size-5" aria-hidden="true" />
                      </span>
                      <div>
                        <Link to={`/admin/orders/${delivery.orderId}`} className="font-heading font-bold whitespace-nowrap text-brand-text hover:underline">
                          Order #{delivery.orderId}
                        </Link>
                        <span className="block text-xs whitespace-nowrap">since {formatDateTime(delivery.createdAt)}</span>
                      </div>
                    </div>
                  </td>
                  <td className={CELL}>
                    {/* The dot pulses for work that waits and for a delivery on its way right now */}
                    <StatusBadge tone={DELIVERY_STATUS_TONES[delivery.status]} pulse={delivery.status === 'WAITING_FOR_PARTNER' || delivery.status === 'OUT_FOR_DELIVERY'}>
                      {DELIVERY_STATUS_LABELS[delivery.status]}
                    </StatusBadge>
                    {delivery.issue && (
                      <span className="mt-1.5 flex items-start gap-1 text-xs text-hot-text">
                        <AlertTriangle className="mt-px size-3.5 shrink-0" aria-hidden="true" />
                        <span>
                          {DELIVERY_ISSUE_LABELS[delivery.issue]}
                          {delivery.issueNote && `: ${delivery.issueNote}`}
                        </span>
                      </span>
                    )}
                  </td>
                  <td className={CELL}>
                    {/* The route: the store, then the customer */}
                    <div className="flex gap-2">
                      <Store className="mt-0.5 size-4 shrink-0 text-new" aria-hidden="true" />
                      <div>
                        <span className="font-bold text-heading">{delivery.storeName}</span>
                        <span className="block text-xs">{delivery.storeAddress}</span>
                      </div>
                    </div>
                    <div className="mt-2 flex gap-2">
                      <MapPin className="mt-0.5 size-4 shrink-0 text-brand-text" aria-hidden="true" />
                      <div>
                        <span className="font-bold text-heading">{delivery.customerName}</span>
                        <span className="block text-xs">{delivery.customerAddress}</span>
                      </div>
                    </div>
                  </td>
                  <td className={CELL}>
                    {delivery.partnerUserId === null ? (
                      '–'
                    ) : (
                      <div className="flex items-center gap-2">
                        <Avatar name={partnerName(delivery.partnerUserId)} />
                        <div>
                          <span className="font-bold whitespace-nowrap text-heading">{partnerName(delivery.partnerUserId)}</span>
                          {delivery.partnerPayout !== null && <span className="block text-xs">earns {formatPrice(delivery.partnerPayout)}</span>}
                        </div>
                      </div>
                    )}
                  </td>
                  <td className={CELL}>
                    {delivery.amountToCollect > 0 ? (
                      <StatusBadge tone="new" icon={Banknote}>
                        {formatPrice(delivery.amountToCollect)}
                      </StatusBadge>
                    ) : (
                      <StatusBadge tone="brand" icon={CreditCard}>
                        Paid online
                      </StatusBadge>
                    )}
                  </td>
                  <td className={cn(CELL, 'text-right')}>{delivery.status === 'WAITING_FOR_PARTNER' && <AssignPartnerButton delivery={delivery} />}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['delivery', 'deliveries']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Deliveries" />
      <PageHeader
        title="Deliveries"
        icon={Truck}
        accent="blue"
        description="A delivery starts when the store marks an order ready for pickup. An online partner of the store's zone gets it by itself; without one, it waits here."
        illustration={<DeliveryRiderIllustration />}
      />
      {/* The real number of deliveries in the main states; each card opens its tab */}
      <section aria-label="Deliveries in numbers" className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <StatCard label="Waiting for a partner" value={waiting ?? null} hint="nobody took them yet" icon={Clock} accent={waiting ? 'amber' : 'green'} to={tabLink('waiting')} />
        <StatCard label="On the way" value={onTheWay ?? null} hint="to the customer now" icon={Bike} accent="blue" to={tabLink('on-the-way')} />
        <StatCard label="Delivered" value={delivered ?? null} hint="all time" icon={CircleCheck} accent="green" to={tabLink('delivered')} />
        <StatCard label="Failed" value={failed ?? null} hint="could not be delivered" icon={PackageX} accent="pink" to={tabLink('failed')} />
      </section>
      <StatusTabs
        label="Delivery status"
        active={tab.key}
        tabs={DELIVERY_TABS.map((candidate) => ({
          key: candidate.key,
          label: candidate.label,
          to: tabLink(candidate.key),
          count: candidate.status === 'WAITING_FOR_PARTNER' ? waiting : undefined,
          tone: candidate.status ? DELIVERY_STATUS_TONES[candidate.status] : undefined,
          group: candidate.group,
        }))}
      />
      {content}
    </>
  )
}

import { CircleCheck, CirclePause, CircleX, Clock, Mail, MapPin, Phone, Store } from 'lucide-react'
import { Link } from 'react-router'
import { StorefrontIllustration } from '../../../components/auth/illustrations/StorefrontIllustration'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterBar } from '../../../components/panel/FilterBar'
import { PageHeader } from '../../../components/panel/PageHeader'
import { SearchBox } from '../../../components/panel/SearchBox'
import { StatusTabs } from '../../../components/panel/StatusTabs'
import { CELL, HEAD_CELL, ROW_ICON, TABLE_CARD, TABLE_HEAD } from '../../../components/panel/tableClasses'
import { StatCard } from '../../../components/sales/StatCard'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StatusBadge } from '../../../components/ui/StatusBadge'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatDate } from '../../../utils/format'
import { useStoreCount } from '../useWorkCounts'
import { STORE_STATUS_LABELS, STORE_STATUS_TONES, STORE_TABS } from './storeStatus'
import { useStores } from './useAdminStores'

/** Every store: the approval queue first, then by status; search by name and city. /admin/stores?tab=active&q=fresh&city=Bengaluru */
export function AdminStoresPage() {
  const { page, pageSize, filter, setFilter, linkWith, goToPage, setPageSize } = useUrlFilters()
  const tab = STORE_TABS.find((candidate) => candidate.key === filter('tab')) ?? STORE_TABS[0]
  const keyword = filter('q')
  const city = filter('city')
  const { data: waiting } = useStoreCount('PENDING_APPROVAL')
  const { data: active } = useStoreCount('ACTIVE')
  const { data: disabled } = useStoreCount('DISABLED')
  const { data: rejected } = useStoreCount('REJECTED')
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useStores({ status: tab.status, keyword: keyword || undefined, city: city || undefined }, page, pageSize)

  /** The list with another tab, keeping the search (the first tab is the default, so it is left out). */
  const tabLink = (key: string) => linkWith('tab', key === STORE_TABS[0].key ? '' : key)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the stores" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content =
      keyword || city ? (
        <EmptyState
          icon={<StorefrontIllustration className="h-32 w-auto" />}
          title="No stores match this search"
          action={<ButtonLink to={tab.key === STORE_TABS[0].key ? '/admin/stores' : `/admin/stores?tab=${tab.key}`}>Clear the search</ButtonLink>}
          className="rounded-2xl border border-line bg-card"
        />
      ) : (
        <EmptyState icon={<StorefrontIllustration className="h-32 w-auto" />} title={tab.empty} className="rounded-2xl border border-line bg-card" />
      )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['store', 'stores']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn(TABLE_CARD, isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[52rem] text-left text-sm">
            <caption className="sr-only">
              {tab.label} stores ({data.totalElements})
            </caption>
            <thead className={TABLE_HEAD}>
              <tr>
                <th scope="col" className={HEAD_CELL}>Store</th>
                <th scope="col" className={HEAD_CELL}>Status</th>
                <th scope="col" className={HEAD_CELL}>City</th>
                <th scope="col" className={HEAD_CELL}>Contact</th>
                <th scope="col" className={HEAD_CELL}>Registered</th>
                <th scope="col" className={HEAD_CELL}>Commission</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((store) => (
                <tr key={store.id}>
                  <td className={CELL}>
                    <div className="flex items-center gap-3">
                      <span className={cn(ROW_ICON, 'bg-new-light text-warn-text')}>
                        <Store className="size-5" aria-hidden="true" />
                      </span>
                      <div>
                        <Link to={`/admin/stores/${store.id}`} className="font-heading font-bold whitespace-nowrap text-brand-text hover:underline">
                          {store.name}
                        </Link>
                        <span className="block text-xs">Store #{store.id}</span>
                      </div>
                    </div>
                  </td>
                  <td className={CELL}>
                    {/* The approval queue's dot pulses: it is work */}
                    <StatusBadge tone={STORE_STATUS_TONES[store.status]} pulse={store.status === 'PENDING_APPROVAL'}>
                      {STORE_STATUS_LABELS[store.status]}
                    </StatusBadge>
                  </td>
                  <td className={CELL}>
                    <span className="flex items-center gap-1.5 text-heading">
                      <MapPin className="size-3.5 shrink-0 text-brand-text" aria-hidden="true" />
                      {store.city}
                    </span>
                    <span className="block pl-5 text-xs">{store.pincode}</span>
                  </td>
                  <td className={CELL}>
                    <span className="flex items-center gap-1.5 whitespace-nowrap">
                      <Phone className="size-3.5 shrink-0 text-brand-text" aria-hidden="true" />
                      {store.phone}
                    </span>
                    {store.email && (
                      <span className="mt-0.5 flex items-center gap-1.5 text-xs">
                        <Mail className="size-3.5 shrink-0 text-brand-text" aria-hidden="true" />
                        {store.email}
                      </span>
                    )}
                  </td>
                  <td className={cn(CELL, 'whitespace-nowrap')}>{formatDate(store.createdAt)}</td>
                  <td className={CELL}>
                    <span className="rounded-full bg-surface px-2.5 py-1 font-heading text-xs font-bold text-heading">{store.commissionPercentage}%</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['store', 'stores']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Stores" />
      <PageHeader
        title="Stores"
        icon={Store}
        accent="orange"
        description="Every store on the platform. New stores wait here for approval; open a store to approve, reject, disable or enable it."
        illustration={<StorefrontIllustration />}
      />
      {/* The real number of stores per status; each card opens its tab (the search stays) */}
      <section aria-label="Stores in numbers" className="mb-6 grid grid-cols-2 gap-3 sm:gap-4 xl:grid-cols-4">
        <StatCard label="Waiting for approval" value={waiting ?? null} hint="new stores to check" icon={Clock} accent={waiting ? 'amber' : 'green'} to={tabLink('waiting')} />
        <StatCard label="Active" value={active ?? null} hint="customers can order" icon={CircleCheck} accent="green" to={tabLink('active')} />
        <StatCard label="Disabled" value={disabled ?? null} hint="hidden from customers" icon={CirclePause} accent="blue" to={tabLink('disabled')} />
        <StatCard label="Rejected" value={rejected ?? null} hint="not approved" icon={CircleX} accent="pink" to={tabLink('rejected')} />
      </section>
      <StatusTabs
        label="Store status"
        active={tab.key}
        tabs={STORE_TABS.map((candidate) => ({
          key: candidate.key,
          label: candidate.label,
          to: tabLink(candidate.key),
          count: candidate.status === 'PENDING_APPROVAL' ? waiting : undefined,
          tone: candidate.status ? STORE_STATUS_TONES[candidate.status] : undefined,
        }))}
      />
      <FilterBar>
        <SearchBox label="Search stores by name" placeholder="Store name" value={keyword} onSearch={(value) => setFilter('q', value)} />
        <SearchBox label="Search stores by city" placeholder="City" value={city} onSearch={(value) => setFilter('city', value)} />
      </FilterBar>
      {content}
    </>
  )
}

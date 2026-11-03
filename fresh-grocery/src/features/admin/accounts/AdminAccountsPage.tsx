import { Link } from 'react-router'
import type { User } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { SearchBox } from '../../../components/panel/SearchBox'
import { StatusTabs } from '../../../components/panel/StatusTabs'
import { Badge } from '../../../components/ui/Badge'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { LoadingState } from '../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatDate, formatDateTime } from '../../../utils/format'
import { ROLE_NAMES } from '../../auth/authContext'
import { ACCOUNT_TABS, ROLE_TONES } from './accountRules'
import { useAccounts } from './useAdminAccounts'

const STATUSES: User['status'][] = ['ACTIVE', 'DISABLED']
const STATUS_LABELS: Record<User['status'], string> = { ACTIVE: 'Active', DISABLED: 'Disabled' }

/** Every account (customers, vendors, partners, admins), oldest first. /admin/accounts?tab=customers&status=DISABLED&q=priya */
export function AdminAccountsPage() {
  const { page, pageSize, filter, setFilter, linkWith, goToPage, setPageSize } = useUrlFilters()
  const tab = ACCOUNT_TABS.find((candidate) => candidate.key === filter('tab')) ?? ACCOUNT_TABS[0]
  const status = STATUSES.find((candidate) => candidate === filter('status'))
  const keyword = filter('q')
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAccounts({ role: tab.role, status, keyword: keyword || undefined }, page, pageSize)
  const filtered = Boolean(status || keyword)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the accounts" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon="👤"
        title={filtered ? 'No accounts match these filters' : 'No accounts yet'}
        action={filtered ? <ButtonLink to="/admin/accounts">Clear the filters</ButtonLink> : undefined}
        className="rounded-2xl bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['account', 'accounts']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn('relative overflow-x-auto rounded-2xl border border-line bg-card', isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[52rem] text-left text-sm">
            <caption className="sr-only">
              {tab.label} accounts ({data.totalElements})
            </caption>
            <thead className="border-b border-line bg-surface font-heading text-heading">
              <tr>
                <th scope="col" className="px-4 py-3">Name</th>
                <th scope="col" className="px-4 py-3">Role</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">Phone</th>
                <th scope="col" className="px-4 py-3">Joined</th>
                <th scope="col" className="px-4 py-3">Last login</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {data.content.map((account) => (
                <tr key={account.id}>
                  <td className="px-4 py-3">
                    <Link to={`/admin/accounts/${account.id}`} className="font-heading font-bold whitespace-nowrap text-brand-text hover:underline">
                      {account.fullName}
                    </Link>
                    <span className="block text-xs">{account.email}</span>
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={ROLE_TONES[account.role]}>{ROLE_NAMES[account.role]}</Badge>
                  </td>
                  <td className="px-4 py-3">
                    <Badge tone={account.status === 'ACTIVE' ? 'brand' : 'neutral'}>{STATUS_LABELS[account.status]}</Badge>
                  </td>
                  <td className="px-4 py-3">{account.phone}</td>
                  <td className="px-4 py-3 whitespace-nowrap">{formatDate(account.createdAt)}</td>
                  <td className="px-4 py-3 whitespace-nowrap">{account.lastLoginAt ? formatDateTime(account.lastLoginAt) : 'Never'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['account', 'accounts']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Accounts" />
      <h1 className="mb-6 text-3xl">Accounts</h1>
      <StatusTabs label="Role" active={tab.key} tabs={ACCOUNT_TABS.map((candidate) => ({ key: candidate.key, label: candidate.label, to: linkWith('tab', candidate.key === 'all' ? '' : candidate.key) }))} />
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <SearchBox label="Search accounts by name or email" placeholder="Name or email" value={keyword} onSearch={(value) => setFilter('q', value)} />
        <FilterSelect label="Account status" allLabel="Active and disabled" value={status ?? ''} options={STATUSES.map((value) => ({ value, label: STATUS_LABELS[value] }))} onChange={(value) => setFilter('status', value)} />
      </div>
      {content}
    </>
  )
}

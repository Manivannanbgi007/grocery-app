import { Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { PageTitle } from '../../../../components/layout/PageTitle'
import { Badge } from '../../../../components/ui/Badge'
import { Button } from '../../../../components/ui/Button'
import { EmptyState } from '../../../../components/ui/EmptyState'
import { ErrorState } from '../../../../components/ui/ErrorState'
import { TablePagination } from '../../../../components/ui/TablePagination'
import { LoadingState } from '../../../../components/ui/LoadingState'
import { useUrlFilters } from '../../../../hooks/useUrlFilters'
import { cn } from '../../../../utils/cn'
import { formatDate, formatDateTime } from '../../../../utils/format'
import { useAccounts } from '../../accounts/useAdminAccounts'
import { AddAdminForm } from './AddAdminForm'

/** The platform team's accounts, and "Add an admin". Switching one off or changing its role is on the account page. /admin/admins */
export function AdminAdminsPage() {
  const { page, pageSize, goToPage, setPageSize } = useUrlFilters()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAccounts({ role: 'ADMIN' }, page, pageSize)
  const [adding, setAdding] = useState(false)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-48" />
  } else if (isError) {
    content = <ErrorState title="Could not load the admins" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = <EmptyState icon="🧑‍💼" title="No admins yet" message="Only you run the platform. Add an admin for the daily work." className="rounded-2xl bg-card" />
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['admin', 'admins']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <div className={cn('relative overflow-x-auto rounded-2xl border border-line bg-card', isPlaceholderData && 'opacity-60')}>
          <table className="w-full min-w-[44rem] text-left text-sm">
            <caption className="sr-only">Admins ({data.totalElements})</caption>
            <thead className="border-b border-line bg-surface font-heading text-heading">
              <tr>
                <th scope="col" className="px-4 py-3">Name</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">Phone</th>
                <th scope="col" className="px-4 py-3">Added</th>
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
                    <Badge tone={account.status === 'ACTIVE' ? 'brand' : 'neutral'}>{account.status === 'ACTIVE' ? 'Active' : 'Disabled'}</Badge>
                  </td>
                  <td className="px-4 py-3">{account.phone}</td>
                  <td className="px-4 py-3 whitespace-nowrap">{formatDate(account.createdAt)}</td>
                  <td className="px-4 py-3 whitespace-nowrap">{account.lastLoginAt ? formatDateTime(account.lastLoginAt) : 'Never'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['admin', 'admins']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  return (
    <>
      <PageTitle title="Admins" />
      <div className="mb-2 flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl">Admins</h1>
        {!adding && (
          <Button onClick={() => setAdding(true)}>
            <Plus className="size-4" aria-hidden="true" />
            Add an admin
          </Button>
        )}
      </div>
      <p className="mb-6 text-sm">Admins run the daily work in this panel. Open an admin to switch the account off or give it another role.</p>
      {adding && (
        <section aria-labelledby="new-admin-title" className="mb-6 rounded-2xl border border-brand/40 bg-card p-6">
          <h2 id="new-admin-title" className="mb-4 text-xl">
            New admin
          </h2>
          <AddAdminForm onDone={() => setAdding(false)} />
        </section>
      )}
      {content}
    </>
  )
}

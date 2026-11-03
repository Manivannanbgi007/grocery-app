import { ArrowLeft } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { isApiError } from '../../../api/client'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Badge } from '../../../components/ui/Badge'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { formatDate, formatDateTime } from '../../../utils/format'
import { ROLE_NAMES, useAuth } from '../../auth/authContext'
import { ROLE_TONES } from './accountRules'
import { AccountStatusPanel } from './AccountStatusPanel'
import { CustomerDetails } from './CustomerDetails'
import { PartnerDetails } from './PartnerDetails'
import { RoleCard } from './RoleCard'
import { useAccount } from './useAdminAccounts'

/**
 * One account: who it is, whether it can log in, and what belongs to it (orders, store, deliveries). The super admin
 * can also change its role. /admin/accounts/:userId
 */
export function AdminAccountPage() {
  const { admin } = useAuth()
  const accountId = Number(useParams().userId)
  const { data: account, isPending, isError, error, refetch } = useAccount(accountId)

  if (isPending) return <LoadingState className="min-h-96" />
  if (isError) {
    if (isApiError(error, 404)) {
      return <EmptyState icon="👤" title="Account not found" message={`There is no account #${accountId}.`} action={<ButtonLink to="/admin/accounts">All accounts</ButtonLink>} className="rounded-2xl bg-card" />
    }
    return <ErrorState title="Could not load the account" error={error} onRetry={() => void refetch()} />
  }

  return (
    <>
      <PageTitle title={account.fullName} />
      <Link to="/admin/accounts" className="mb-3 inline-flex items-center gap-1 text-sm font-bold text-brand-text hover:underline">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All accounts
      </Link>
      <div className="mb-1 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl">{account.fullName}</h1>
        <Badge tone={ROLE_TONES[account.role]}>{ROLE_NAMES[account.role]}</Badge>
      </div>
      <p className="mb-6 text-sm">
        {account.email} · {account.phone} · account #{account.id} · joined on {formatDate(account.createdAt)} · last login{' '}
        {account.lastLoginAt ? formatDateTime(account.lastLoginAt) : 'never'}
      </p>

      <div className="max-w-4xl space-y-6">
        <AccountStatusPanel account={account} />
        {admin?.role === 'SUPER_ADMIN' && <RoleCard account={account} />}
        {account.role === 'CUSTOMER' && <CustomerDetails account={account} />}
        {account.role === 'VENDOR' && (
          <section className="rounded-2xl border border-line bg-card p-6">
            <h2 className="mb-2 text-xl">Store</h2>
            {account.storeId ? (
              <Link to={`/admin/stores/${account.storeId}`} className="font-bold text-brand-text hover:underline">
                Store #{account.storeId}
              </Link>
            ) : (
              <p className="text-sm">No approved store yet. A store is linked to its vendor when an admin approves it (see Stores, waiting for approval).</p>
            )}
          </section>
        )}
        {account.role === 'DELIVERY_PARTNER' && <PartnerDetails userId={account.id} />}
      </div>
    </>
  )
}

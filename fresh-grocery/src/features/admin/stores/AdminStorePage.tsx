import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { isApiError } from '../../../api/client'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Badge } from '../../../components/ui/Badge'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { addressLines } from '../../../utils/address'
import { formatDate } from '../../../utils/format'
import { DAY_NAMES, formatHours } from '../../../utils/openingHours'
import { useAuth } from '../../auth/authContext'
import { CommissionCard } from '../owner/commission/CommissionCard'
import { STORE_STATUS_LABELS, STORE_STATUS_TONES } from './storeStatus'
import { StoreStatusPanel } from './StoreStatusPanel'
import { useStore } from './useAdminStores'

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-line bg-card p-6">
      <h2 className="mb-3 text-xl">{title}</h2>
      {children}
    </section>
  )
}

/** One line of the details list: a label and its value. */
function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap justify-between gap-x-4 gap-y-1 border-b border-line py-2 last:border-b-0">
      <dt>{label}</dt>
      <dd className="text-right font-bold text-heading">{children}</dd>
    </div>
  )
}

/**
 * Everything about one store, and the status actions (approve, reject, disable, enable). The super admin can also set
 * its commission. /admin/stores/:storeId
 */
export function AdminStorePage() {
  const { admin } = useAuth()
  const storeId = Number(useParams().storeId)
  const { data: store, isPending, isError, error, refetch } = useStore(storeId)

  if (isPending) return <LoadingState className="min-h-96" />
  if (isError) {
    if (isApiError(error, 404)) {
      return <EmptyState icon="🏪" title="Store not found" message={`There is no store #${storeId}.`} action={<ButtonLink to="/admin/stores">All stores</ButtonLink>} className="rounded-2xl bg-card" />
    }
    return <ErrorState title="Could not load the store" error={error} onRetry={() => void refetch()} />
  }

  return (
    <>
      <PageTitle title={store.name} />
      <Link to="/admin/stores" className="mb-3 inline-flex items-center gap-1 text-sm font-bold text-brand-text hover:underline">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All stores
      </Link>
      <div className="mb-1 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl">{store.name}</h1>
        <Badge tone={STORE_STATUS_TONES[store.status]}>{STORE_STATUS_LABELS[store.status]}</Badge>
      </div>
      <p className="mb-6 text-sm">
        Store #{store.id} · vendor account #{store.ownerUserId} · registered on {formatDate(store.createdAt)}
      </p>

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-6">
          <StoreStatusPanel store={store} />
          <Card title="Store details">
            {store.description && <p className="mb-3">{store.description}</p>}
            <dl className="text-sm">
              <Detail label="Phone">{store.phone}</Detail>
              <Detail label="Email">{store.email ?? '–'}</Detail>
              <Detail label="Address">
                {addressLines({ ...store, landmark: null }).map((line) => (
                  <span key={line} className="block">
                    {line}
                  </span>
                ))}
              </Detail>
              <Detail label="Platform commission">{store.commissionPercentage}% of each order</Detail>
              <Detail label="Taking new orders">{store.acceptingOrders ? 'Yes' : 'No, paused by the vendor'}</Detail>
              <Detail label="Open now">{store.openNow ? 'Yes' : 'No'}</Detail>
            </dl>
          </Card>
        </div>
        <div className="space-y-6">
          {admin?.role === 'SUPER_ADMIN' && <CommissionCard key={store.commissionPercentage} store={store} />}
          <Card title="Opening hours">
            <dl className="text-sm">
              {store.openingHours.map((day) => (
                <Detail key={day.dayOfWeek} label={DAY_NAMES[day.dayOfWeek]}>
                  {formatHours(day)}
                </Detail>
              ))}
            </dl>
          </Card>
          <Card title="Delivery areas">
            {store.deliveryPincodes.length === 0 ? (
              <p className="text-sm">No PIN codes yet: the store cannot deliver anywhere.</p>
            ) : (
              <ul aria-label="PIN codes it delivers to" className="flex flex-wrap gap-2">
                {store.deliveryPincodes.map((pincode) => (
                  <li key={pincode} className="rounded-full bg-brand-light px-3 py-1 text-sm font-bold text-brand-text">
                    {pincode}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
    </>
  )
}

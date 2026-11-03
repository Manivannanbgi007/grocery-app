import { Navigate } from 'react-router'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { LoadingState } from '../../components/ui/LoadingState'
import { RequireCustomer } from '../auth/RequireCustomer'
import { OpenOrders } from '../orders/OpenOrders'
import { useOpenOrders } from '../orders/useOrders'

/**
 * /orders/track - "Track my order" in the account menu: straight to the tracking page of the one open order, a choice
 * when there are several, or a note when none is on its way.
 */
export function TrackOrderPage() {
  return (
    <RequireCustomer>
      <PageTitle title="Track my order" />
      <BreadcrumbBar crumbs={[{ label: 'My orders', to: '/orders' }, { label: 'Track my order' }]} />
      <div className="page-container mt-10 max-w-4xl">
        <TrackOrderContent />
      </div>
    </RequireCustomer>
  )
}

function TrackOrderContent() {
  const { openOrders, isPending, isError, error, refetch } = useOpenOrders()

  if (isPending) return <LoadingState label="Looking for your open orders…" className="min-h-64" />
  if (isError) return <ErrorState title="Could not load your orders" error={error} onRetry={() => void refetch()} />
  if (openOrders.length === 1) return <Navigate to={`/orders/${openOrders[0].id}/track`} replace />

  return (
    <>
      <h1 className="mb-6 text-3xl md:text-4xl">Track my order</h1>
      {openOrders.length === 0 ? (
        <EmptyState
          icon="🛵"
          title="No order is on its way"
          message="When you have placed an order, you can follow it here: from the store to your door, live on the map."
          action={<ButtonLink to="/orders">My orders</ButtonLink>}
        />
      ) : (
        <OpenOrders orders={openOrders} title="Which order do you want to follow?" description={`You have ${openOrders.length} open orders.`} />
      )}
    </>
  )
}

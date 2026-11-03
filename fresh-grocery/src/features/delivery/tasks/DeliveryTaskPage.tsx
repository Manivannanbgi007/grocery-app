import { ArrowLeft, SearchX } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { isApiError } from '../../../api/client'
import { PageTitle } from '../../../components/layout/PageTitle'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { PAGE_WIDTH } from '../layout/pageWidth'
import { TaskPanel } from './TaskPanel'
import { useTask } from './useTasks'

/** One of the partner's deliveries (also a finished one, from the history). /delivery/deliveries/:deliveryId */
export function DeliveryTaskPage() {
  const deliveryId = Number(useParams().deliveryId)
  const { data: delivery, isPending, isError, error, refetch } = useTask(deliveryId)

  let content
  if (isPending) content = <LoadingState label="Loading the delivery…" className="min-h-96" />
  else if (isError && isApiError(error, 404)) {
    content = (
      <EmptyState
        icon={<SearchX className="mx-auto size-12 text-muted" />}
        title="Delivery not found"
        message="It is not one of your deliveries."
        action={<ButtonLink to="/delivery">Back to today</ButtonLink>}
        className="rounded-2xl border border-line bg-card shadow-sm"
      />
    )
  } else if (isError) content = <ErrorState title="Could not load the delivery" error={error} onRetry={() => void refetch()} />
  else content = <TaskPanel delivery={delivery} />

  return (
    <div className={PAGE_WIDTH}>
      <PageTitle title={delivery ? `Order #${delivery.orderId}` : 'Delivery'} />
      <Link
        to="/delivery/history"
        className="mb-4 inline-flex items-center gap-1.5 rounded-full border border-line bg-card px-3 py-1.5 text-sm font-bold text-heading transition-colors hover:border-brand hover:text-brand-text"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        History
      </Link>
      {content}
    </div>
  )
}

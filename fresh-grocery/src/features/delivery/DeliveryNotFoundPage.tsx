import { PageTitle } from '../../components/layout/PageTitle'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'

/** Any /delivery/... address that is not a page of the delivery panel. */
export function DeliveryNotFoundPage() {
  return (
    <>
      <PageTitle title="Page not found" />
      <EmptyState
        icon={<span className="font-heading text-7xl font-bold text-brand-text">404</span>}
        title="Page not found"
        message="This page is not part of the delivery panel."
        action={<ButtonLink to="/delivery">Back to today</ButtonLink>}
        className="rounded-2xl bg-card"
      />
    </>
  )
}

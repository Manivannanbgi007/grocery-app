import { PageTitle } from '../../components/layout/PageTitle'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'

/** Any /vendor/... address that is not a page of the vendor panel. */
export function VendorNotFoundPage() {
  return (
    <>
      <PageTitle title="Page not found" />
      <EmptyState
        icon={<span className="font-heading text-7xl font-bold text-brand-text">404</span>}
        title="Page not found"
        message="This page is not part of the vendor panel."
        action={<ButtonLink to="/vendor">Back to the dashboard</ButtonLink>}
        className="rounded-2xl bg-card"
      />
    </>
  )
}

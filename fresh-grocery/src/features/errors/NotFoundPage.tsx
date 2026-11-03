import { PageTitle } from '../../components/layout/PageTitle'
import { ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'

/** Shown for any address that is not a page of the app. */
export function NotFoundPage() {
  return (
    <>
      <PageTitle title="Page not found" />
      <div className="page-container py-12">
        <EmptyState
          icon={<span className="font-heading text-7xl font-bold text-brand-text">404</span>}
          title="Page not found"
          message="We could not find the page you were looking for. The link may be wrong, or the page may have moved."
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <ButtonLink to="/">Back to home</ButtonLink>
              <ButtonLink to="/shop" variant="outline">
                Browse products
              </ButtonLink>
            </div>
          }
        />
      </div>
    </>
  )
}

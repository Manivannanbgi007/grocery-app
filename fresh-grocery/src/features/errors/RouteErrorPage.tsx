import { isRouteErrorResponse, useRouteError } from 'react-router'
import { PageTitle } from '../../components/layout/PageTitle'
import { Button } from '../../components/ui/Button'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { EmptyState } from '../../components/ui/EmptyState'
import { NotFoundPage } from './NotFoundPage'

/**
 * Shown by the router when a page crashes (a bug, not a failed API call: those show an ErrorState in the page).
 * Inside a page it keeps the header and footer; if the layout itself crashes it is shown on its own.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundPage />

  return (
    <>
      <PageTitle title="Something went wrong" />
      <div className="page-container py-12">
        <EmptyState
          icon="🥀"
          title="Something went wrong"
          message="This page could not be shown. Reloading usually helps. If it keeps happening, please try again later."
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <Button onClick={() => window.location.reload()}>Reload page</Button>
              {/* A plain link (full page load) also clears whatever state broke the page */}
              <a href="/" className={buttonClasses('outline')}>
                Go to home
              </a>
            </div>
          }
        />
      </div>
    </>
  )
}

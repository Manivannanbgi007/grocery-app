import { LoadingState } from '../../components/ui/LoadingState'

/**
 * Shown by the pages that need a login while the app checks, right after it starts, whether the browser is still
 * logged in (useAuth().restoring). Without it, a reload of the cart would send a logged-in customer to the login page.
 */
export function RestoringLogin() {
  return <LoadingState label="Checking your login…" className="min-h-[60vh]" />
}

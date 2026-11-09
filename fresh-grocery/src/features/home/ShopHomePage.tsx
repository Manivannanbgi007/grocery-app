import { Navigate, useLocation } from 'react-router'
import { CUSTOMER_HOME, useAuth } from '../auth/authContext'
import { HomePage } from './HomePage'

/**
 * The shop's home page at '/': for guests (and vendors or admins looking at the shop). A logged-in customer is sent on
 * to their own address, /customer/home (CUSTOMER_HOME), keeping a "#deals" or "?…" part, so the address bar always
 * says which page they are on. While the login is still being restored after a reload, the page shows as for a guest
 * (no waiting), and moves on once the customer is known.
 *
 * The logout shows this page first and logs out right after (see AccountMenu); its LOGGING_OUT_STATE (authContext)
 * keeps it here, or it would go on to /customer/home, whose guard would then send the logged-out guest to the login page.
 */
export function ShopHomePage() {
  const { customer } = useAuth()
  const location = useLocation()
  const loggingOut = (location.state as { loggingOut?: boolean } | null)?.loggingOut === true

  if (customer && !loggingOut) {
    return <Navigate to={{ pathname: CUSTOMER_HOME, search: location.search, hash: location.hash }} replace />
  }
  return <HomePage />
}

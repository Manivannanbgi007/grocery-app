import { useLocation, useNavigate, type Location } from 'react-router'
import { useAuth } from './authContext'

/**
 * The mark useLogOutToLogin() puts on the page (its history entry) just before the logout: "the user logged out on
 * this page". The page's guard then sends the guest to the plain login page.
 */
const LOGGED_OUT_HERE = { loggedOutHere: true }

function isLoggedOutHere(location: Location): boolean {
  return (location.state as typeof LOGGED_OUT_HERE | null)?.loggedOutHere === true
}

/**
 * Where a page that needs a login sends a guest (RequireCustomer, RequireVendor ...): the panel's login page with
 * ?next= to come back to this page afterwards, e.g. /vendor/login?next=%2Fvendor%2Forders. Not right after the user
 * logged out on this page: then the plain login page (/vendor/login), so the address stays clean.
 */
export function loginPathFor(login: string, location: Location): string {
  if (isLoggedOutHere(location)) return login
  return `${login}?next=${encodeURIComponent(location.pathname + location.search)}`
}

/**
 * "Log out" that ends on a login page with a clean address (/vendor/login, never /vendor/login?next=...).
 *
 * The order matters. Logging out first would let the page's guard see a guest at once and send them to
 * /vendor/login?next=<this page> before any navigation of ours. So:
 * 1. the same page again, with the LOGGED_OUT_HERE mark (flushSync: on screen at once, before step 2);
 * 2. log out: the guard sees the mark and goes to the plain login page;
 * 3. go to the login page ourselves too, for a page without a guard (e.g. a login page whose address has a ?next=).
 */
export function useLogOutToLogin(): (login: string) => Promise<void> {
  const { logOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  return async (login: string) => {
    const here = location.pathname + location.search + location.hash
    // preventScrollReset: the page stays where it is for this moment (no jump to the top)
    await navigate(here, { replace: true, state: LOGGED_OUT_HERE, flushSync: true, preventScrollReset: true })
    logOut()
    // replace, like the guard: the login page takes the place of this page in the browser's history
    void navigate(login, { replace: true })
  }
}

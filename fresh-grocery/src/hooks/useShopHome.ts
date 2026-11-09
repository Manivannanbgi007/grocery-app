import { CUSTOMER_HOME, useAuth } from '../features/auth/authContext'

/**
 * The shop's home page for whoever is looking. A logged-in customer has their own address, /customer/home, so the
 * address bar says which page they are on; everyone else (guests, and vendors or admins looking at the shop) has '/'.
 * '/' itself sends a logged-in customer on to /customer/home (features/home/ShopHomePage.tsx), so a link that still
 * says '/' works too; these links just avoid that extra step.
 */
export function useShopHome() {
  const { customer } = useAuth()
  const home = customer ? CUSTOMER_HOME : '/'
  return {
    home,
    /** A link to the home page or one of its sections, for this visitor: '/' -> home, '/#deals' -> home + '#deals'. Other links stay. */
    homeHref: (to: string) => (to === '/' || to.startsWith('/#') ? home + to.slice(1) : to),
  }
}

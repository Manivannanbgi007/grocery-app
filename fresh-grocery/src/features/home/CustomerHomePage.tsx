import { RequireCustomer } from '../auth/RequireCustomer'
import { HomePage } from './HomePage'

/**
 * A logged-in customer's home page (/customer/home, CUSTOMER_HOME): the shop's home page, where the shop's login goes when
 * no other page is waiting. A guest logs in first and comes back; another kind of account is told it is for customers.
 */
export function CustomerHomePage() {
  return (
    <RequireCustomer>
      <HomePage />
    </RequireCustomer>
  )
}

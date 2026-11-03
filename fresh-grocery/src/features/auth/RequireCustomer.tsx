import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './authContext'
import { loginPathFor } from './loginRedirect'
import { OtherAccountNotice } from './OtherAccountNotice'
import { RestoringLogin } from './RestoringLogin'

/**
 * Shows the page only to a logged-in customer. A guest goes to the login page and comes back here afterwards
 * (after "Log out" just the plain login page, see loginPathFor); another kind of account (a vendor) is told that the
 * page is for customers.
 */
export function RequireCustomer({ children }: { children: ReactNode }) {
  const { user, customer, restoring } = useAuth()
  const location = useLocation()

  if (restoring) return <RestoringLogin />
  if (!user) return <Navigate to={loginPathFor('/login', location)} replace />
  if (!customer) return <OtherAccountNotice user={user} panel="CUSTOMER" />
  return children
}

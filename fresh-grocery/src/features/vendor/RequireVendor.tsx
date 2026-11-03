import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { useAuth } from '../auth/authContext'
import { loginPathFor } from '../auth/loginRedirect'
import { OtherAccountNotice } from '../auth/OtherAccountNotice'
import { RestoringLogin } from '../auth/RestoringLogin'

/**
 * Shows the vendor panel only to a logged-in vendor. A guest goes to the vendor login and comes back here afterwards
 * (after "Log out" just the plain login page, see loginPathFor); another kind of account (a customer) is told that the
 * panel is for vendors.
 */
export function RequireVendor({ children }: { children: ReactNode }) {
  const { user, vendor, restoring } = useAuth()
  const location = useLocation()

  if (restoring) return <RestoringLogin />
  if (!user) return <Navigate to={loginPathFor('/vendor/login', location)} replace />
  if (!vendor) {
    return (
      <AuthScreen look="vendor" panelName="Vendor panel" title="Vendor panel">
        <OtherAccountNotice user={user} panel="VENDOR" />
      </AuthScreen>
    )
  }
  return children
}

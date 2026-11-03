import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { useAuth } from '../auth/authContext'
import { loginPathFor } from '../auth/loginRedirect'
import { OtherAccountNotice } from '../auth/OtherAccountNotice'
import { RestoringLogin } from '../auth/RestoringLogin'

/**
 * Shows the delivery panel only to a logged-in delivery partner. A guest goes to the delivery login and comes back here
 * afterwards (after "Log out" just the plain login page, see loginPathFor); another kind of account is told that the
 * panel is for delivery partners.
 */
export function RequirePartner({ children }: { children: ReactNode }) {
  const { user, partner, restoring } = useAuth()
  const location = useLocation()

  if (restoring) return <RestoringLogin />
  if (!user) return <Navigate to={loginPathFor('/delivery/login', location)} replace />
  if (!partner) {
    return (
      <AuthScreen look="delivery" panelName="Delivery panel" title="Delivery panel">
        <OtherAccountNotice user={user} panel="DELIVERY" />
      </AuthScreen>
    )
  }
  return children
}

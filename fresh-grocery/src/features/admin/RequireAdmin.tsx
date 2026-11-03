import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { useAuth } from '../auth/authContext'
import { loginPathFor } from '../auth/loginRedirect'
import { OtherAccountNotice } from '../auth/OtherAccountNotice'
import { RestoringLogin } from '../auth/RestoringLogin'

/**
 * Shows the admin panel only to a logged-in admin (or the super admin). A guest goes to the admin login and comes back
 * here afterwards (after "Log out" just the plain login page, see loginPathFor); another kind of account is told that
 * the panel is for the platform team.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user, admin, restoring } = useAuth()
  const location = useLocation()

  if (restoring) return <RestoringLogin />
  if (!user) return <Navigate to={loginPathFor('/admin/login', location)} replace />
  if (!admin) {
    return (
      <AuthScreen look="admin" panelName="Admin panel" title="Admin panel">
        <OtherAccountNotice user={user} panel="ADMIN" />
      </AuthScreen>
    )
  }
  return children
}

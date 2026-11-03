import { ShieldCheck } from 'lucide-react'
import { Navigate, useSearchParams } from 'react-router'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { PANELS, useAuth } from '../auth/authContext'
import { LoginForm, type DemoAccount } from '../auth/LoginForm'
import { OtherAccountNotice } from '../auth/OtherAccountNotice'
import { safeNextPath } from '../auth/validation'

/** The seed admins, offered only while developing (mock API and the backend's local seed). */
const DEMO_ADMINS: DemoAccount[] = [
  { email: 'admin@grocery.com', label: 'Platform Admin' },
  { email: 'superadmin@grocery.com', label: 'Super Admin' },
]

/** Log in to the admin panel (admins and the super admin), then go to the page in ?next= (the dashboard by default). */
export function AdminLoginPage() {
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const next = safeNextPath(searchParams.get('next') ?? '/admin')

  if (user && PANELS.ADMIN.roles.includes(user.role)) return <Navigate to={next} replace />

  return (
    <AuthScreen look="admin" title="Admin login" panelName="Admin panel">
      {user ? (
        <OtherAccountNotice user={user} panel="ADMIN" />
      ) : (
        <>
          <AuthHeading eyebrow="Platform team" title="Admin login">
            Stores, orders, deliveries, customers and the catalog.
          </AuthHeading>
          <LoginForm
            panel="ADMIN"
            next={next}
            demoAccounts={DEMO_ADMINS}
            footer={
              <p className="mt-6 flex items-center justify-center gap-2 text-center text-sm">
                <ShieldCheck className="size-4 shrink-0 text-brand-text" aria-hidden="true" />
                Admin accounts are created by the super admin.
              </p>
            }
          />
        </>
      )}
    </AuthScreen>
  )
}

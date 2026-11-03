import { Navigate, useSearchParams } from 'react-router'
import { AuthDivider } from '../../components/auth/AuthDivider'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { ButtonLink } from '../../components/ui/Button'
import { useAuth } from '../auth/authContext'
import { LoginForm, type DemoAccount } from '../auth/LoginForm'
import { OtherAccountNotice } from '../auth/OtherAccountNotice'
import { safeNextPath } from '../auth/validation'

/** The seed vendors, offered only while developing (mock API and the backend's local seed). */
const DEMO_VENDORS: DemoAccount[] = [
  { email: 'vendor1@grocery.com', label: 'Fresh Mart' },
  { email: 'vendor2@grocery.com', label: 'Green Basket' },
]

/** Log in to the vendor panel, then go to the page in ?next= (the panel's start page by default). */
export function VendorLoginPage() {
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const next = safeNextPath(searchParams.get('next') ?? '/vendor')

  if (user && user.role === 'VENDOR') return <Navigate to={next} replace />

  return (
    <AuthScreen look="vendor" title="Vendor login" panelName="Vendor panel">
      {user ? (
        <OtherAccountNotice user={user} panel="VENDOR" />
      ) : (
        <>
          <AuthHeading eyebrow="Welcome back" title="Vendor login">
            Manage your store, products and orders.
          </AuthHeading>
          <LoginForm
            panel="VENDOR"
            next={next}
            demoAccounts={DEMO_VENDORS}
            footer={
              <>
                <AuthDivider label="Want to sell on Fresh Grocery?" />
                <ButtonLink to="/register?role=VENDOR" variant="outline" size="lg" className="w-full rounded-xl">
                  Become a vendor
                </ButtonLink>
              </>
            }
          />
        </>
      )}
    </AuthScreen>
  )
}

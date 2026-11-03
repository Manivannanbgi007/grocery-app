import { Navigate, useSearchParams } from 'react-router'
import { AuthDivider } from '../../components/auth/AuthDivider'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { ButtonLink } from '../../components/ui/Button'
import { useAuth } from '../auth/authContext'
import { LoginForm, type DemoAccount } from '../auth/LoginForm'
import { OtherAccountNotice } from '../auth/OtherAccountNotice'
import { safeNextPath } from '../auth/validation'

/** The seed partners, offered only while developing (mock API and the backend's local seed). */
const DEMO_PARTNERS: DemoAccount[] = [
  { email: 'delivery1@grocery.com', label: 'Arjun Singh' },
  { email: 'delivery2@grocery.com', label: 'Karthik Reddy' },
]

/** Log in to the delivery panel, then go to the page in ?next= (today's tasks by default). */
export function DeliveryLoginPage() {
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const next = safeNextPath(searchParams.get('next') ?? '/delivery')

  if (user && user.role === 'DELIVERY_PARTNER') return <Navigate to={next} replace />

  return (
    <AuthScreen look="delivery" title="Delivery login" panelName="Delivery panel">
      {user ? (
        <OtherAccountNotice user={user} panel="DELIVERY" />
      ) : (
        <>
          <AuthHeading eyebrow="Welcome back" title="Delivery login">
            Pick up orders from stores and bring them to customers.
          </AuthHeading>
          <LoginForm
            panel="DELIVERY"
            next={next}
            demoAccounts={DEMO_PARTNERS}
            footer={
              <>
                <AuthDivider label="Want to deliver with us?" />
                <ButtonLink to="/register?role=DELIVERY_PARTNER" variant="outline" size="lg" className="w-full rounded-xl">
                  Become a delivery partner
                </ButtonLink>
              </>
            }
          />
        </>
      )}
    </AuthScreen>
  )
}

import { Bike, Info, Store, UserPlus } from 'lucide-react'
import { Link, Navigate, useSearchParams } from 'react-router'
import { AuthDivider } from '../../components/auth/AuthDivider'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { ButtonLink } from '../../components/ui/Button'
import { CUSTOMER_HOME, useAuth } from './authContext'
import { LoginForm, type DemoAccount } from './LoginForm'
import { OtherAccountNotice } from './OtherAccountNotice'
import { safeNextPath } from './validation'

/** The seed customers, offered only while developing (they exist in the mock API and in the backend's local seed). */
const DEMO_CUSTOMERS: DemoAccount[] = [
  { email: 'customer1@grocery.com', label: 'Priya Sharma' },
  { email: 'customer2@grocery.com', label: 'Rahul Verma' },
]

/**
 * The shop's login, on a page of its own (outside the shop's header and footer): email and password, then back to the
 * page in ?next= (e.g. the cart), or to the customer's home page (CUSTOMER_HOME) when no other page is waiting.
 */
export function LoginPage() {
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  // The page that sent the customer here (?next=/cart), or '/' when none did
  const asked = safeNextPath(searchParams.get('next'))
  // Where logging in ends: that page, but the customer's own home page instead of the shop's home page
  const next = asked === '/' ? CUSTOMER_HOME : asked

  // Already logged in (e.g. opened /login from a bookmark, or just logged in): a customer goes on, another account is told so
  if (user && user.role === 'CUSTOMER') return <Navigate to={next} replace />
  if (user) {
    return (
      <AuthScreen look="shop" title="Log in">
        <OtherAccountNotice user={user} panel="CUSTOMER" />
      </AuthScreen>
    )
  }

  return (
    <AuthScreen look="shop" title="Log in">
      <AuthHeading eyebrow="Welcome back" title="Log in">
        Log in to shop, see your orders and follow your deliveries.
      </AuthHeading>
      {asked !== '/' && (
        <p className="mb-6 flex gap-2 rounded-xl bg-brand-light px-4 py-3 text-sm text-brand-text">
          <Info className="size-5 shrink-0" aria-hidden="true" />
          This page needs a customer account. After logging in you go straight back to where you were.
        </p>
      )}

      <LoginForm
        panel="CUSTOMER"
        next={next}
        demoAccounts={DEMO_CUSTOMERS}
        footer={
          <>
            <AuthDivider label="New to Fresh Grocery?" />
            {/* The sign-up keeps its own default: the page that was asked for, else the shop's home page */}
            <ButtonLink to={`/register?next=${encodeURIComponent(asked)}`} variant="outline" size="lg" className="w-full rounded-xl">
              <UserPlus className="size-5" aria-hidden="true" />
              Create an account
            </ButtonLink>
            {/* The other two logins, as small pills that turn green on hover */}
            <div className="mt-6 text-center text-sm">
              <p>Selling or delivering with us?</p>
              <div className="mt-3 flex flex-wrap justify-center gap-2">
                <Link
                  to="/vendor/login"
                  className="inline-flex items-center gap-1.5 rounded-full border border-line px-3.5 py-1.5 font-bold text-heading transition-colors hover:border-brand hover:bg-brand-light hover:text-brand-text"
                >
                  <Store className="size-4" aria-hidden="true" />
                  Vendor login
                </Link>
                <Link
                  to="/delivery/login"
                  className="inline-flex items-center gap-1.5 rounded-full border border-line px-3.5 py-1.5 font-bold text-heading transition-colors hover:border-brand hover:bg-brand-light hover:text-brand-text"
                >
                  <Bike className="size-4" aria-hidden="true" />
                  Delivery login
                </Link>
              </div>
            </div>
          </>
        }
      />
    </AuthScreen>
  )
}

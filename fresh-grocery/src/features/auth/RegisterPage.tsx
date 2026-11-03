import { ArrowRight, Bike, LockKeyhole, Mail, ShoppingBasket, Store, UserRound, type LucideIcon } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { SocialLoginButtons } from '../../components/auth/SocialLoginButtons'
import type { AuthLook } from '../../components/auth/authLooks'
import { Button } from '../../components/ui/Button'
import { PasswordField } from '../../components/ui/PasswordField'
import { TextField } from '../../components/ui/TextField'
import { cn } from '../../utils/cn'
import { panelOf, useAuth, type Panel } from './authContext'
import { PasswordRules } from './PasswordRules'
import { hasErrors, safeNextPath, serverFieldErrors, validateRegistration, type FieldErrors, type RegisterValues } from './validation'

const FIELDS = ['fullName', 'email', 'phone', 'password'] as const

type AccountType = 'CUSTOMER' | 'VENDOR' | 'DELIVERY_PARTNER'

/** What each kind of account shows and where the new account goes. */
const ACCOUNT_TYPES: Record<
  AccountType,
  { tab: string; icon: LucideIcon; look: AuthLook; eyebrow: string; title: string; intro: string; button: string; login: string; welcome?: string; home?: string }
> = {
  CUSTOMER: {
    tab: 'Shop',
    icon: ShoppingBasket,
    look: 'shop',
    eyebrow: 'Join Fresh Grocery',
    title: 'Create an account',
    intro: 'Shop fresh groceries from stores near you.',
    button: 'Create account',
    login: '/login',
  },
  VENDOR: {
    tab: 'Sell',
    icon: Store,
    look: 'vendor',
    eyebrow: 'For store owners',
    title: 'Become a vendor',
    intro: 'Sell your products on Fresh Grocery. Next, you tell us about your store.',
    button: 'Create vendor account',
    login: '/vendor/login',
    welcome: 'Now tell us about your store.',
    home: '/vendor',
  },
  DELIVERY_PARTNER: {
    tab: 'Deliver',
    icon: Bike,
    look: 'delivery',
    eyebrow: 'For delivery partners',
    title: 'Become a delivery partner',
    intro: 'Deliver groceries near you and earn per delivery. Next, you send your vehicle and licence details.',
    button: 'Create partner account',
    login: '/delivery/login',
    welcome: 'Now send your vehicle and licence details.',
    home: '/delivery',
  },
}

/** Shop, Sell or Deliver: the kind of account to create, as links (/register?role=...). Typed values stay. */
function AccountTypeSwitch({ active, next }: { active: AccountType; next: string }) {
  const linkFor = (type: AccountType) => (type === 'CUSTOMER' ? `/register?next=${encodeURIComponent(next)}` : `/register?role=${type}`)
  return (
    <nav aria-label="Kind of account" className="mb-6 grid grid-cols-3 gap-1 rounded-2xl border border-line bg-card p-1">
      {(Object.keys(ACCOUNT_TYPES) as AccountType[]).map((type) => {
        const { tab, icon: Icon } = ACCOUNT_TYPES[type]
        return (
          <Link
            key={type}
            to={linkFor(type)}
            replace
            aria-current={type === active ? 'page' : undefined}
            className={cn(
              'flex items-center justify-center gap-2 rounded-xl px-2 py-2.5 font-heading text-sm font-bold transition-colors',
              type === active ? 'bg-brand-light text-brand-text' : 'text-heading hover:text-brand-text',
            )}
          >
            <Icon className="size-4" aria-hidden="true" />
            {tab}
          </Link>
        )
      })}
    </nav>
  )
}

/**
 * Create an account, logged in straight away. Normally a customer account; "Become a vendor" opens /register?role=VENDOR
 * (then the vendor panel's store registration) and "Become a delivery partner" /register?role=DELIVERY_PARTNER (then the
 * delivery panel's KYC form).
 */
export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const next = safeNextPath(searchParams.get('next'))
  const roleParam = searchParams.get('role')
  const accountType: AccountType = roleParam === 'VENDOR' || roleParam === 'DELIVERY_PARTNER' ? roleParam : 'CUSTOMER'
  const texts = ACCOUNT_TYPES[accountType]

  const [values, setValues] = useState<RegisterValues>({ fullName: '', email: '', phone: '', password: '' })
  const [errors, setErrors] = useState<FieldErrors<keyof RegisterValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user && !submitting) return <Navigate to={next} replace />

  function change(field: keyof RegisterValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateRegistration(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    setSubmitting(true)
    try {
      const created = await register({ ...values, fullName: values.fullName.trim(), email: values.email.trim(), role: accountType })
      const firstName = created.fullName.split(' ')[0]
      if (texts.home) {
        // The vendor panel starts with registering the store, the delivery panel with the KYC details
        toast.success(`Welcome, ${firstName}! ${texts.welcome}`)
        navigate(texts.home, { replace: true })
      } else {
        toast.success(`Welcome to Fresh Grocery, ${firstName}!`)
        navigate(next, { replace: true })
      }
    } catch (error) {
      setSubmitting(false)
      const fieldErrors = isApiError(error) ? serverFieldErrors(error, FIELDS) : {}
      if (hasErrors(fieldErrors)) setErrors(fieldErrors)
      else setFormError(error instanceof Error ? error.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <AuthScreen look={texts.look} title={texts.title}>
      <AuthHeading eyebrow={texts.eyebrow} title={texts.title}>
        {texts.intro}
      </AuthHeading>
      <AccountTypeSwitch active={accountType} next={next} />

      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        {formError && (
          <p role="alert" className="rounded-xl border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
            {formError}
          </p>
        )}
        <TextField label="Full name" autoComplete="name" icon={UserRound} value={values.fullName} onChange={(event) => change('fullName', event.target.value)} error={errors.fullName} />
        <TextField label="Email" type="email" autoComplete="email" icon={Mail} placeholder="name@example.com" value={values.email} onChange={(event) => change('email', event.target.value)} error={errors.email} />
        <TextField
          label="Mobile number"
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          maxLength={10}
          leading="+91"
          value={values.phone}
          onChange={(event) => change('phone', event.target.value.replace(/\D/g, ''))}
          error={errors.phone}
        />
        <PasswordField
          label="Password"
          autoComplete="new-password"
          icon={LockKeyhole}
          hint={<PasswordRules password={values.password} />}
          value={values.password}
          onChange={(event) => change('password', event.target.value)}
          error={errors.password}
        />
        <Button type="submit" size="lg" loading={submitting} className="w-full rounded-xl shadow-lg shadow-brand/25">
          {texts.button}
          {!submitting && <ArrowRight className="size-5" aria-hidden="true" />}
        </Button>
      </form>

      {/* A sign-up with Google creates this account type too (the mobile number is asked on the next page) */}
      <SocialLoginButtons
        panel={panelOf(accountType) as Exclude<Panel, 'ADMIN'>}
        next={texts.home ?? next}
        role={accountType}
        action="Sign up"
      />

      <p className="mt-6 text-center text-sm">
        Already have an account?{' '}
        <Link to={accountType === 'CUSTOMER' ? `/login?next=${encodeURIComponent(next)}` : texts.login} className="font-bold text-brand-text hover:underline">
          Log in
        </Link>
      </p>
    </AuthScreen>
  )
}

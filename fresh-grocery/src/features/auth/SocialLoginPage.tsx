import { ArrowRight, LockKeyhole, Mail, UserRound } from 'lucide-react'
import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import { exchangeSocialCode, socialLink, socialSignUp, type SocialSessionResult } from '../../api/socialAuth'
import type { AuthResult } from '../../api/types'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import type { AuthLook } from '../../components/auth/authLooks'
import { Button, ButtonLink } from '../../components/ui/Button'
import { LoadingState } from '../../components/ui/LoadingState'
import { PasswordField } from '../../components/ui/PasswordField'
import { TextField } from '../../components/ui/TextField'
import { LOGIN_LINKS, PANELS, panelOf, ROLE_NAMES, useAuth, WrongPanelError, type Panel } from './authContext'
import { readSocialLogin, type SocialLoginIntent } from './socialLoginIntent'
import { emailError, hasErrors, mobileError, nameError, removeEmpty, serverFieldErrors, type FieldErrors } from './validation'

/*
 * Where a login with Google (Microsoft, Facebook, Apple) comes back to: /login/social#code=... or #error=...
 * (docs/social-login.md). The one-time code is exchanged once; then the user is logged in, or finishes the sign-up
 * (mobile number), or enters the password of the existing account with the same email.
 */

const LOOKS: Record<Exclude<Panel, 'ADMIN'>, AuthLook> = { CUSTOMER: 'shop', VENDOR: 'vendor', DELIVERY: 'delivery' }
const PROVIDER_NAMES: Record<string, string> = { google: 'Google', microsoft: 'Microsoft', facebook: 'Facebook', apple: 'Apple', test: 'the test provider' }
const SIGNUP_FIELDS = ['fullName', 'email', 'phone'] as const

/** The reasons the backend sends (#error=...): never the provider's own text. */
const ERRORS: Record<string, string> = {
  cancelled: 'You cancelled the login. You can try again, or log in with your email and password.',
  failed: 'The login with your account provider did not work. Please try again.',
}

type PageState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string; otherPanel?: Panel | null }
  | { kind: 'signup'; result: SocialSessionResult }
  | { kind: 'password'; result: SocialSessionResult }

/**
 * Each code is exchanged once, also when React runs the effect twice while developing (a second call would fail): both
 * runs share the call that is on its way. Once it has answered, the code is forgotten.
 */
const exchanges = new Map<string, Promise<SocialSessionResult>>()

function exchangeOnce(code: string): Promise<SocialSessionResult> {
  let exchange = exchanges.get(code)
  if (!exchange) {
    exchange = exchangeSocialCode(code).finally(() => exchanges.delete(code))
    exchanges.set(code, exchange)
  }
  return exchange
}

function providerName(provider: string | undefined): string {
  return (provider && PROVIDER_NAMES[provider]) ?? 'your account provider'
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong. Please try again.'
}

export function SocialLoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { finishSocialLogin, restoring } = useAuth()
  const [intent] = useState<SocialLoginIntent>(readSocialLogin)
  // Read once: the address is cleaned right away (below)
  const [fragment] = useState(() => new URLSearchParams(location.hash.replace(/^#/, '')))
  const code = fragment.get('code')
  const [state, setState] = useState<PageState>(() => {
    const error = fragment.get('error')
    if (error) return { kind: 'error', message: ERRORS[error] ?? ERRORS.failed }
    return code ? { kind: 'loading' } : { kind: 'error', message: ERRORS.failed }
  })

  // The one-time code must not stay in the address bar or the history
  useEffect(() => {
    if (location.hash) navigate('/login/social', { replace: true })
  }, [location.hash, navigate])

  /** Logged in (directly, after the sign-up, or after the password): the session starts, then the page the user wanted. */
  function complete(auth: AuthResult) {
    try {
      const user = finishSocialLogin(auth, intent.panel)
      toast.success(`Welcome, ${user.fullName.split(' ')[0]}!`)
      navigate(intent.next, { replace: true })
    } catch (error) {
      if (!(error instanceof WrongPanelError)) throw error
      setState({ kind: 'error', message: error.message, otherPanel: panelOf(error.role) })
    }
  }

  useEffect(() => {
    // After the app's check for an earlier login (AuthProvider's /refresh): if both went at once and the exchange won,
    // the old login's refused refresh would remove the new refresh cookie
    if (!code || restoring) return
    let active = true
    exchangeOnce(code)
      .then((result) => {
        if (!active) return
        if (result.status === 'LOGGED_IN' && result.auth) complete(result.auth)
        else setState({ kind: result.status === 'SIGNUP_REQUIRED' ? 'signup' : 'password', result })
      })
      .catch((error: unknown) => active && setState({ kind: 'error', message: messageOf(error) }))
    return () => {
      active = false
    }
    // complete() is the same logic every time; the effect must run once per code
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code, restoring])

  const look = LOOKS[intent.panel as Exclude<Panel, 'ADMIN'>]
  let content: ReactNode
  if (state.kind === 'loading') {
    content = <LoadingState label="Signing you in…" className="min-h-64" />
  } else if (state.kind === 'signup') {
    content = <SignupForm result={state.result} intent={intent} onDone={complete} />
  } else if (state.kind === 'password') {
    content = <PasswordForm result={state.result} onDone={complete} />
  } else {
    content = (
      <>
        <AuthHeading eyebrow="Log in" title="That did not work" />
        <p role="alert" className="mb-6 rounded-xl border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {state.message}
        </p>
        <div className="flex flex-wrap gap-3">
          <ButtonLink to={PANELS[intent.panel].login} size="lg" className="rounded-xl">
            Back to the login
          </ButtonLink>
          {state.otherPanel && (
            <ButtonLink to={PANELS[state.otherPanel].login} variant="outline" size="lg" className="rounded-xl">
              {LOGIN_LINKS[state.otherPanel]}
            </ButtonLink>
          )}
        </div>
      </>
    )
  }

  return (
    <AuthScreen look={look} title="Log in" panelName={intent.panel === 'CUSTOMER' ? undefined : PANELS[intent.panel].label}>
      {content}
    </AuthScreen>
  )
}

/** No account yet: what the provider did not give (the mobile number; the email when it is missing or unverified). */
function SignupForm({ result, intent, onDone }: { result: SocialSessionResult; intent: SocialLoginIntent; onDone: (auth: AuthResult) => void }) {
  const [values, setValues] = useState({ fullName: result.fullName ?? '', email: result.email ?? '', phone: '' })
  const [errors, setErrors] = useState<FieldErrors<(typeof SIGNUP_FIELDS)[number]>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function change(field: (typeof SIGNUP_FIELDS)[number], value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = removeEmpty({
      fullName: nameError(values.fullName),
      email: result.emailLocked ? undefined : emailError(values.email),
      phone: mobileError(values.phone),
    })
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    setSubmitting(true)
    try {
      const auth = await socialSignUp({
        fullName: values.fullName.trim(),
        email: result.emailLocked ? undefined : values.email.trim(),
        phone: values.phone,
        role: intent.role,
      })
      onDone(auth)
    } catch (error) {
      setSubmitting(false)
      const fieldErrors = isApiError(error) ? serverFieldErrors(error, SIGNUP_FIELDS) : {}
      if (hasErrors(fieldErrors)) setErrors(fieldErrors)
      else setFormError(messageOf(error))
    }
  }

  return (
    <>
      <AuthHeading eyebrow={`Signed in with ${providerName(result.provider)}`} title="Finish creating your account">
        One more step: your mobile number, for deliveries and order updates. Account type: {ROLE_NAMES[intent.role]}.
      </AuthHeading>
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        {formError && (
          <p role="alert" className="rounded-xl border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
            {formError}
          </p>
        )}
        <TextField label="Full name" autoComplete="name" icon={UserRound} value={values.fullName} onChange={(event) => change('fullName', event.target.value)} error={errors.fullName} />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          icon={Mail}
          value={values.email}
          readOnly={result.emailLocked}
          hint={result.emailLocked ? `Confirmed by ${providerName(result.provider)}` : undefined}
          onChange={(event) => change('email', event.target.value)}
          error={errors.email}
        />
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
        <Button type="submit" size="lg" loading={submitting} className="w-full rounded-xl shadow-lg shadow-brand/25">
          Create account
          {!submitting && <ArrowRight className="size-5" aria-hidden="true" />}
        </Button>
      </form>
    </>
  )
}

/** An account with this email exists: its password connects the provider once (then the provider logs in directly). */
function PasswordForm({ result, onDone }: { result: SocialSessionResult; onDone: (auth: AuthResult) => void }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const name = providerName(result.provider)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!password) {
      setError('Please enter your password.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      onDone(await socialLink(password))
    } catch (failure) {
      setSubmitting(false)
      setError(messageOf(failure))
    }
  }

  return (
    <>
      <AuthHeading eyebrow={`Signed in with ${name}`} title="Connect your account">
        An account with <strong className="text-heading">{result.email}</strong> already exists. Enter its password once to
        connect {name}; next time {name} logs you in directly.
      </AuthHeading>
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        <PasswordField
          label="Password"
          autoComplete="current-password"
          icon={LockKeyhole}
          value={password}
          onChange={(event) => {
            setPassword(event.target.value)
            setError(null)
          }}
          error={error ?? undefined}
        />
        <p className="text-right text-sm">
          <Link to="/forgot-password" className="font-bold text-brand-text hover:underline">
            Forgot your password?
          </Link>
        </p>
        <Button type="submit" size="lg" loading={submitting} className="w-full rounded-xl shadow-lg shadow-brand/25">
          Connect and log in
          {!submitting && <ArrowRight className="size-5" aria-hidden="true" />}
        </Button>
      </form>
    </>
  )
}

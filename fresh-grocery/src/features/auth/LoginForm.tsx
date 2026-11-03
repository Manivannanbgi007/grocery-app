import { ArrowRight, CircleAlert, FlaskConical, LockKeyhole, Mail, UserRound } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import { SocialLoginButtons } from '../../components/auth/SocialLoginButtons'
import { Appear } from '../../components/ui/Appear'
import { Button } from '../../components/ui/Button'
import { PasswordField } from '../../components/ui/PasswordField'
import { TextField } from '../../components/ui/TextField'
import { LOGIN_LINKS, PANELS, panelOf, useAuth, WrongPanelError, type Panel } from './authContext'
import { hasErrors, serverFieldErrors, validateLogin, type FieldErrors, type LoginValues } from './validation'

/** A seed account of the mock API and of the backend's local seed, offered on the login pages while developing. */
export interface DemoAccount {
  email: string
  /** who it is, e.g. "Priya Sharma" or "Fresh Mart" */
  label: string
}

/** The password of every seed account (local only). */
const DEMO_PASSWORD = 'Password@123'

interface LoginFormProps {
  /** the panel to log in to: an account of another panel is refused with a link to its own login */
  panel: Panel
  /** where to go after logging in */
  next: string
  /** shown under the "Log in" button, e.g. the way to create an account */
  footer?: ReactNode
  /** while developing: buttons that fill in one of these accounts */
  demoAccounts?: DemoAccount[]
}

/**
 * Email, password and "Log in", used by the shop's and every panel's login page. Below it "Continue with Google" and the
 * other providers the server offers, except on the admin panel (admins keep the password only).
 */
export function LoginForm({ panel, next, footer, demoAccounts }: LoginFormProps) {
  const { logIn } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState<LoginValues>({ email: '', password: '' })
  const [errors, setErrors] = useState<FieldErrors<keyof LoginValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  /** the panel of an account that belongs elsewhere: the error then links to that panel's login */
  const [otherPanel, setOtherPanel] = useState<Panel | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function change(field: keyof LoginValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function fillInDemoAccount(email: string) {
    setValues({ email, password: DEMO_PASSWORD })
    setErrors({})
    setFormError(null)
    setOtherPanel(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateLogin(values)
    setErrors(found)
    setFormError(null)
    setOtherPanel(null)
    if (hasErrors(found)) return

    setSubmitting(true)
    try {
      const loggedIn = await logIn({ email: values.email.trim(), password: values.password }, panel)
      toast.success(`Welcome back, ${loggedIn.fullName.split(' ')[0]}!`)
      navigate(next, { replace: true })
    } catch (error) {
      setSubmitting(false)
      if (error instanceof WrongPanelError) {
        setFormError(error.message)
        setOtherPanel(panelOf(error.role))
      } else if (isApiError(error, 400)) setErrors(serverFieldErrors(error, ['email', 'password']))
      else setFormError(error instanceof Error ? error.message : 'Something went wrong. Please try again.')
    }
  }

  // The look: each block below sits in an <Appear>, so the email, the password, the button and the rest rise in one
  // after the other when the page opens (order 1, 2, 3 …). It only adds movement; the form works the same without it.
  return (
    <>
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        {formError && (
          // Shakes once when it appears (animate-shake in styles/index.css), so the eye goes to it
          <p role="alert" className="flex gap-2.5 rounded-xl border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading motion-safe:animate-shake">
            <CircleAlert className="mt-0.5 size-4 shrink-0 text-hot-text" aria-hidden="true" />
            <span>
              {formError}
              {otherPanel && (
                <>
                  {' '}
                  <Link to={PANELS[otherPanel].login} className="font-bold text-brand-text hover:underline">
                    {LOGIN_LINKS[otherPanel]}
                  </Link>
                </>
              )}
            </span>
          </p>
        )}
        <Appear order={1}>
          <TextField label="Email" type="email" autoComplete="email" icon={Mail} placeholder="name@example.com" value={values.email} onChange={(event) => change('email', event.target.value)} error={errors.email} />
        </Appear>
        <Appear order={2}>
          <PasswordField
            label="Password"
            autoComplete="current-password"
            icon={LockKeyhole}
            value={values.password}
            onChange={(event) => change('password', event.target.value)}
            error={errors.password}
          />
          <p className="mt-2 text-right text-sm">
            <Link to="/forgot-password" className="font-bold text-brand-text hover:underline">
              Forgot your password?
            </Link>
          </p>
        </Appear>
        <Appear order={3}>
          {/* group: the arrow slides to the right while the mouse is over the button (group-hover) */}
          <Button type="submit" size="lg" loading={submitting} className="group w-full rounded-xl shadow-lg shadow-brand/25">
            Log in
            {!submitting && <ArrowRight className="size-5 transition-transform duration-200 group-hover:translate-x-1" aria-hidden="true" />}
          </Button>
        </Appear>
      </form>

      {panel !== 'ADMIN' && (
        <Appear order={4}>
          <SocialLoginButtons panel={panel} next={next} />
        </Appear>
      )}

      <Appear order={5}>{footer}</Appear>

      {import.meta.env.DEV && demoAccounts && (
        <section aria-labelledby="demo-accounts-title" className="mt-8 rounded-2xl border border-dashed border-brand-soft bg-brand-light/40 p-4">
          <h2 id="demo-accounts-title" className="flex items-center gap-2 text-sm">
            <FlaskConical className="size-4 text-brand-text" aria-hidden="true" />
            Demo accounts (local only)
          </h2>
          <ul className="mt-3 grid gap-2 sm:grid-cols-2">
            {demoAccounts.map((account) => (
              <li key={account.email}>
                {/* Lifts a little on hover, and its round icon turns green */}
                <button
                  type="button"
                  onClick={() => fillInDemoAccount(account.email)}
                  className="group flex w-full items-center gap-3 rounded-xl border border-line bg-card px-3 py-2 text-left transition duration-200 hover:-translate-y-0.5 hover:border-brand hover:shadow-md"
                >
                  <span className="grid size-9 shrink-0 place-items-center rounded-full bg-brand-light text-brand-text transition-colors group-hover:bg-brand group-hover:text-white">
                    <UserRound className="size-4" aria-hidden="true" />
                  </span>
                  <span className="min-w-0">
                    <span className="block text-sm font-bold text-heading">{account.label}</span>
                    <span className="block truncate text-xs">{account.email}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs">
            A click fills in the form. Password: <code>{DEMO_PASSWORD}</code>
          </p>
        </section>
      )}
    </>
  )
}

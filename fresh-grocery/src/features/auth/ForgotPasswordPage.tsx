import { ArrowLeft, ArrowRight, Mail } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { forgotPassword } from '../../api/auth'
import { isApiError } from '../../api/client'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { EnvelopeIllustration } from '../../components/auth/illustrations/EnvelopeIllustration'
import { PadlockIllustration } from '../../components/auth/illustrations/PadlockIllustration'
import { Button, ButtonLink } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import { emailError, serverFieldErrors } from './validation'

/**
 * "Forgot your password?": the backend emails a code (valid 30 minutes) when the email has an account, and answers
 * the same either way. The local backend also returns the code, which this page shows while developing.
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [localCode, setLocalCode] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = emailError(email)
    setError(found)
    setFormError(null)
    if (found) return

    setSubmitting(true)
    try {
      const result = await forgotPassword(email.trim())
      setSentTo(email.trim())
      setLocalCode(result?.resetToken ?? null)
    } catch (submitError) {
      const fieldError = isApiError(submitError) ? serverFieldErrors(submitError, ['email']).email : undefined
      if (fieldError) setError(fieldError)
      else setFormError(submitError instanceof Error ? submitError.message : 'Something went wrong. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthScreen look="shop" title="Forgot your password?" phoneBanner={false}>
      {sentTo ? (
        <div role="status">
          <EnvelopeIllustration className="mb-4 h-36 w-auto" />
          <h1 className="text-3xl sm:text-4xl">Check your email</h1>
          <p className="mt-2">
            If <strong className="text-heading">{sentTo}</strong> has an account, we have sent it a code to choose a new password. The code works for 30 minutes.
          </p>
          {import.meta.env.DEV && (
            <p className="mt-4 rounded-xl border border-dashed border-brand-soft bg-brand-light/40 px-4 py-3 text-xs">
              Local development: the email is in Mailpit (http://localhost:8025).
              {localCode && (
                <>
                  {' '}
                  The local backend also returned the code: <code className="break-all">{localCode}</code>
                </>
              )}
            </p>
          )}
          <ButtonLink to={localCode && import.meta.env.DEV ? `/reset-password?code=${localCode}` : '/reset-password'} size="lg" className="mt-6 w-full rounded-xl shadow-lg shadow-brand/25">
            Enter the code
            <ArrowRight className="size-5" aria-hidden="true" />
          </ButtonLink>
        </div>
      ) : (
        <>
          <PadlockIllustration className="mb-4 h-32 w-auto" />
          <AuthHeading eyebrow="No problem" title="Forgot your password?">
            Enter the email you log in with. We send you a code to choose a new password.
          </AuthHeading>
          <form onSubmit={handleSubmit} noValidate className="space-y-5">
            {formError && (
              <p role="alert" className="rounded-xl border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
                {formError}
              </p>
            )}
            <TextField label="Email" type="email" autoComplete="email" icon={Mail} placeholder="name@example.com" value={email} onChange={(event) => setEmail(event.target.value)} error={error} />
            <Button type="submit" size="lg" loading={submitting} className="w-full rounded-xl shadow-lg shadow-brand/25">
              Send me a code
            </Button>
          </form>
        </>
      )}
      <p className="mt-6 text-center text-sm">
        <Link to="/login" className="inline-flex items-center gap-1.5 font-bold text-brand-text hover:underline">
          <ArrowLeft className="size-4" aria-hidden="true" />
          Back to the login
        </Link>
      </p>
    </AuthScreen>
  )
}

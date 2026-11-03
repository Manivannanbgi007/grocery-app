import { KeyRound, LockKeyhole } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { resetPassword } from '../../api/auth'
import { isApiError } from '../../api/client'
import { AuthHeading } from '../../components/auth/AuthHeading'
import { AuthScreen } from '../../components/auth/AuthScreen'
import { PadlockIllustration } from '../../components/auth/illustrations/PadlockIllustration'
import { Button, ButtonLink } from '../../components/ui/Button'
import { PasswordField } from '../../components/ui/PasswordField'
import { TextField } from '../../components/ui/TextField'
import { PasswordRules } from './PasswordRules'
import { hasErrors, passwordError, removeEmpty, serverFieldErrors, type FieldErrors } from './validation'

interface ResetValues {
  code: string
  newPassword: string
}

/** A new password with the code from the email (?code= fills it in, e.g. from the forgot-password page while developing). */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const [values, setValues] = useState<ResetValues>({ code: searchParams.get('code') ?? '', newPassword: '' })
  const [errors, setErrors] = useState<FieldErrors<keyof ResetValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  function change(field: keyof ResetValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = removeEmpty({ code: values.code.trim() ? undefined : 'Please enter the code from the email.', newPassword: passwordError(values.newPassword) })
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    setSubmitting(true)
    try {
      await resetPassword(values.code.trim(), values.newPassword)
      setDone(true)
    } catch (error) {
      setSubmitting(false)
      const fieldErrors = isApiError(error) ? serverFieldErrors(error, ['newPassword'] as const) : {}
      // 400 "Reset token is invalid or has expired"
      if (isApiError(error, 400) && error.message.startsWith('Reset token')) setErrors({ code: 'This code is wrong or has expired. Ask for a new one.' })
      else if (hasErrors(fieldErrors)) setErrors(fieldErrors)
      else setFormError(error instanceof Error ? error.message : 'Something went wrong. Please try again.')
    }
  }

  if (done) {
    return (
      <AuthScreen look="shop" title="Password changed" phoneBanner={false}>
        <div role="status">
          <PadlockIllustration open className="mb-4 h-36 w-auto" />
          <h1 className="text-3xl sm:text-4xl">Your password is changed</h1>
          <p className="mt-2">Log in with your new password.</p>
          <ButtonLink to="/login" size="lg" className="mt-6 w-full rounded-xl shadow-lg shadow-brand/25">
            Log in
          </ButtonLink>
        </div>
      </AuthScreen>
    )
  }

  return (
    <AuthScreen look="shop" title="Choose a new password" phoneBanner={false}>
      <PadlockIllustration className="mb-4 h-32 w-auto" />
      <AuthHeading eyebrow="Almost there" title="Choose a new password">
        Enter the code from the email and your new password.
      </AuthHeading>
      <form onSubmit={handleSubmit} noValidate className="space-y-5">
        {formError && (
          <p role="alert" className="rounded-xl border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
            {formError}
          </p>
        )}
        <TextField label="Code from the email" autoComplete="one-time-code" icon={KeyRound} value={values.code} onChange={(event) => change('code', event.target.value)} error={errors.code} />
        <PasswordField
          label="New password"
          autoComplete="new-password"
          icon={LockKeyhole}
          hint={<PasswordRules password={values.newPassword} />}
          value={values.newPassword}
          onChange={(event) => change('newPassword', event.target.value)}
          error={errors.newPassword}
        />
        <Button type="submit" size="lg" loading={submitting} className="w-full rounded-xl shadow-lg shadow-brand/25">
          Save new password
        </Button>
      </form>
      <p className="mt-6 text-center text-sm">
        No code, or it expired?{' '}
        <Link to="/forgot-password" className="font-bold text-brand-text hover:underline">
          Ask for a new one
        </Link>
      </p>
    </AuthScreen>
  )
}

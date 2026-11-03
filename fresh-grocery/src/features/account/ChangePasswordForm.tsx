import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import { Button } from '../../components/ui/Button'
import { PasswordField } from '../../components/ui/PasswordField'
import { hasErrors, passwordError, removeEmpty, serverFieldErrors, type FieldErrors } from '../auth/validation'
import { useChangePassword } from './useAccount'

interface PasswordValues {
  currentPassword: string
  newPassword: string
}

const EMPTY: PasswordValues = { currentPassword: '', newPassword: '' }

function validatePasswords(values: PasswordValues): FieldErrors<keyof PasswordValues> {
  return removeEmpty({
    currentPassword: values.currentPassword ? undefined : 'Please enter your current password.',
    newPassword:
      passwordError(values.newPassword) ?? (values.newPassword === values.currentPassword ? 'Choose a password that is different from the current one.' : undefined),
  })
}

/** Change the login password (auth-service). The customer stays logged in. */
export function ChangePasswordForm() {
  const change = useChangePassword()
  const [values, setValues] = useState<PasswordValues>(EMPTY)
  const [errors, setErrors] = useState<FieldErrors<keyof PasswordValues>>({})
  const [formError, setFormError] = useState<string | null>(null)

  function update(field: keyof PasswordValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validatePasswords(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    change.mutate(values, {
      onSuccess: () => {
        setValues(EMPTY)
        toast.success('Your password is changed')
      },
      onError: (error) => {
        if (isApiError(error, 401)) return
        const fieldErrors = isApiError(error) ? serverFieldErrors(error, ['currentPassword', 'newPassword'] as const) : {}
        // "Current password is incorrect" belongs next to that field
        if (error.message === 'Current password is incorrect') setErrors({ currentPassword: 'This is not your current password.' })
        else if (hasErrors(fieldErrors)) setErrors(fieldErrors)
        else setFormError(error.message)
      },
    })
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="max-w-md space-y-5">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <PasswordField
        label="Current password"
        autoComplete="current-password"
        value={values.currentPassword}
        onChange={(event) => update('currentPassword', event.target.value)}
        error={errors.currentPassword}
      />
      <PasswordField
        label="New password"
        autoComplete="new-password"
        hint="8 to 64 characters, with at least one letter and one digit."
        value={values.newPassword}
        onChange={(event) => update('newPassword', event.target.value)}
        error={errors.newPassword}
      />
      <Button type="submit" loading={change.isPending}>
        Change password
      </Button>
    </form>
  )
}

import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../../api/client'
import { Button } from '../../../../components/ui/Button'
import { PasswordField } from '../../../../components/ui/PasswordField'
import { TextField } from '../../../../components/ui/TextField'
import { hasErrors, serverFieldErrors, validateRegistration, type FieldErrors, type RegisterValues } from '../../../auth/validation'
import { useCreateAdmin } from '../../accounts/useAdminAccounts'

const FIELDS = ['fullName', 'email', 'phone', 'password'] as const
const EMPTY: RegisterValues = { fullName: '', email: '', phone: '', password: '' }

/** A new admin account (CreateAdminRequest has the same rules as a registration). */
export function AddAdminForm({ onDone }: { onDone: () => void }) {
  const create = useCreateAdmin()
  const [values, setValues] = useState<RegisterValues>(EMPTY)
  const [errors, setErrors] = useState<FieldErrors<keyof RegisterValues>>({})
  const [formError, setFormError] = useState<string | null>(null)

  function change(field: keyof RegisterValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateRegistration(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    create.mutate(
      { fullName: values.fullName.trim(), email: values.email.trim(), phone: values.phone, password: values.password },
      {
        onSuccess: (admin) => {
          toast.success(`${admin.fullName} is an admin now and can log in at the admin panel`)
          onDone()
        },
        onError: (error) => {
          const fieldErrors = isApiError(error) ? serverFieldErrors(error, FIELDS) : {}
          // The registration's "Log in instead?" does not fit here
          if (isApiError(error, 409) && fieldErrors.email) fieldErrors.email = 'This email address is already registered.'
          if (hasErrors(fieldErrors)) setErrors(fieldErrors)
          else if (!isApiError(error, 401)) setFormError(error.message)
        },
      },
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Full name" autoComplete="off" value={values.fullName} onChange={(event) => change('fullName', event.target.value)} error={errors.fullName} />
        <TextField label="Email" type="email" autoComplete="off" value={values.email} onChange={(event) => change('email', event.target.value)} error={errors.email} />
        <TextField label="Mobile number" inputMode="numeric" maxLength={10} value={values.phone} onChange={(event) => change('phone', event.target.value.replace(/\D/g, ''))} error={errors.phone} />
        <PasswordField
          label="First password"
          autoComplete="new-password"
          value={values.password}
          onChange={(event) => change('password', event.target.value)}
          error={errors.password}
          hint="8 to 64 characters with a letter and a digit. Give it to the new admin; they can choose their own with “Forgot your password?” on the login page."
        />
      </div>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={create.isPending}>
          Add admin
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import type { CustomerProfile, Gender } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { DateField } from '../../components/ui/DateField'
import { SelectField } from '../../components/ui/SelectField'
import { TextField } from '../../components/ui/TextField'
import { hasErrors, mobileError, nameError, removeEmpty, serverFieldErrors, type FieldErrors } from '../auth/validation'
import { useUpdateProfile } from './useAccount'

interface ProfileValues {
  fullName: string
  phone: string
  /** "1994-05-17", or empty */
  dateOfBirth: string
  gender: Gender | ''
}

const FIELDS = ['fullName', 'phone', 'dateOfBirth', 'gender'] as const

const GENDER_OPTIONS: { value: Gender | ''; label: string }[] = [
  { value: '', label: 'Not given' },
  { value: 'FEMALE', label: 'Female' },
  { value: 'MALE', label: 'Male' },
  { value: 'OTHER', label: 'Other' },
  { value: 'PREFER_NOT_TO_SAY', label: 'Prefer not to say' },
]

/** Today as "2026-10-26" (the latest date a birthday can have). */
const today = () => new Date().toISOString().slice(0, 10)

function validateProfile(values: ProfileValues): FieldErrors<keyof ProfileValues> {
  return removeEmpty({
    fullName: nameError(values.fullName),
    phone: mobileError(values.phone),
    dateOfBirth: values.dateOfBirth && values.dateOfBirth >= today() ? 'Please choose a date in the past.' : undefined,
  })
}

/**
 * The profile (customer-service): name and mobile number for deliveries, birthday and gender.
 * The login email belongs to the account (auth-service) and cannot be changed here.
 */
export function ProfileForm({ profile }: { profile: CustomerProfile }) {
  const update = useUpdateProfile()
  const [values, setValues] = useState<ProfileValues>({
    fullName: profile.fullName,
    phone: profile.phone,
    dateOfBirth: profile.dateOfBirth ?? '',
    gender: profile.gender ?? '',
  })
  const [errors, setErrors] = useState<FieldErrors<keyof ProfileValues>>({})
  const [formError, setFormError] = useState<string | null>(null)

  function change<Field extends keyof ProfileValues>(field: Field, value: ProfileValues[Field]) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateProfile(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    update.mutate(
      { fullName: values.fullName.trim(), phone: values.phone, dateOfBirth: values.dateOfBirth || null, gender: values.gender || null },
      {
        onSuccess: () => toast.success('Your profile is saved'),
        onError: (error) => {
          const fieldErrors = isApiError(error) ? serverFieldErrors(error, FIELDS) : {}
          if (hasErrors(fieldErrors)) setErrors(fieldErrors)
          else if (!isApiError(error, 401)) setFormError(error.message)
        },
      },
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <div>
        <p className="font-heading text-sm font-bold text-heading">Email</p>
        <p className="mt-1 text-heading">{profile.email}</p>
        <p className="mt-1 text-xs">You log in with this email; it cannot be changed here.</p>
      </div>
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField label="Full name" autoComplete="name" value={values.fullName} onChange={(event) => change('fullName', event.target.value)} error={errors.fullName} />
        <TextField
          label="Mobile number"
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          maxLength={10}
          leading="+91"
          hint="For your deliveries."
          value={values.phone}
          onChange={(event) => change('phone', event.target.value.replace(/\D/g, ''))}
          error={errors.phone}
        />
        <DateField label="Date of birth (optional)" max={today()} clearable value={values.dateOfBirth} onChange={(day) => change('dateOfBirth', day)} error={errors.dateOfBirth} />
        <SelectField label="Gender (optional)" value={values.gender} onChange={(gender) => change('gender', gender as ProfileValues['gender'])} options={GENDER_OPTIONS} error={errors.gender} />
      </div>
      <Button type="submit" loading={update.isPending}>
        Save profile
      </Button>
    </form>
  )
}

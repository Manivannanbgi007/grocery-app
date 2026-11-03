import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../../api/client'
import { Button } from '../../../../components/ui/Button'
import { TextField } from '../../../../components/ui/TextField'
import { hasErrors, removeEmpty, serverFieldErrors, type FieldErrors } from '../../../auth/validation'
import { useFeatureFlagChanges } from './useFeatureFlags'

interface FlagValues {
  key: string
  description: string
  enabled: boolean
}

const FIELDS = ['key', 'description'] as const
/** UPPER_SNAKE_CASE, 3 to 60 characters, starting with a letter (FeatureFlagRequest) */
const FLAG_KEY = /^[A-Z][A-Z0-9_]{2,59}$/

function validateFlag(values: FlagValues): FieldErrors<'key' | 'description'> {
  const errors: FieldErrors<'key' | 'description'> = {}
  if (!values.key) errors.key = 'Enter a key, e.g. CITY_PUNE.'
  else if (!FLAG_KEY.test(values.key)) errors.key = 'Use 3 to 60 capital letters, digits and _, starting with a letter, e.g. CITY_PUNE.'
  if (!values.description.trim()) errors.description = 'Say what the flag switches on, e.g. “Stores and customers in Pune can use the app”.'
  else if (values.description.trim().length > 255) errors.description = 'Use at most 255 characters.'
  return removeEmpty(errors)
}

/** "city pune" -> "CITY_PUNE" while typing: keys are UPPER_SNAKE_CASE. */
function toFlagKey(typed: string): string {
  return typed.toUpperCase().replace(/[\s-]+/g, '_')
}

/** A new feature flag: its key, what it does, and whether it starts switched on. */
export function FeatureFlagForm({ onDone }: { onDone: () => void }) {
  const { create } = useFeatureFlagChanges()
  const [values, setValues] = useState<FlagValues>({ key: '', description: '', enabled: false })
  const [errors, setErrors] = useState<FieldErrors<'key' | 'description'>>({})
  const [formError, setFormError] = useState<string | null>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateFlag(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    create.mutate(
      { key: values.key, description: values.description.trim(), enabled: values.enabled },
      {
        onSuccess: (flag) => {
          toast.success(`${flag.key} added (${flag.enabled ? 'on' : 'off'})`)
          onDone()
        },
        onError: (error) => {
          const fieldErrors = isApiError(error, 400) ? serverFieldErrors(error, FIELDS) : {}
          if (isApiError(error, 409)) setErrors({ key: 'A flag with this key exists already.' })
          else if (hasErrors(fieldErrors)) setErrors(fieldErrors)
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
      <div className="grid gap-4 sm:grid-cols-[16rem_1fr]">
        <TextField
          label="Key"
          placeholder="CITY_PUNE"
          autoCapitalize="characters"
          spellCheck={false}
          value={values.key}
          onChange={(event) => {
            setValues((current) => ({ ...current, key: toFlagKey(event.target.value) }))
            setErrors((current) => ({ ...current, key: undefined }))
          }}
          error={errors.key}
        />
        <TextField
          label="What it switches on"
          value={values.description}
          onChange={(event) => {
            setValues((current) => ({ ...current, description: event.target.value }))
            setErrors((current) => ({ ...current, description: undefined }))
          }}
          error={errors.description}
        />
      </div>
      <label className="flex items-center gap-2 text-sm text-heading">
        <input type="checkbox" checked={values.enabled} onChange={(event) => setValues((current) => ({ ...current, enabled: event.target.checked }))} className="size-4 accent-brand" />
        Switch it on now
      </label>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={create.isPending}>
          Add flag
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

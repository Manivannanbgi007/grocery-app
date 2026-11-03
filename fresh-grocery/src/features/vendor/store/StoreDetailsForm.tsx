import { useId, useState, type ChangeEvent, type FormEvent } from 'react'
import { isApiError } from '../../../api/client'
import type { StoreDetailsRequest } from '../../../api/vendorStore'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { cn } from '../../../utils/cn'
import { hasErrors, serverFieldErrors, type FieldErrors } from '../../auth/validation'
import { STORE_FIELDS, toStoreDetailsRequest, validateStoreDetails, type StoreDetailsValues } from './storeValidation'

interface StoreDetailsFormProps {
  initialValues: StoreDetailsValues
  submitLabel: string
  /** Saves the details. When it fails, the form shows the backend's field errors or message. */
  onSave: (request: StoreDetailsRequest) => Promise<unknown>
  onCancel?: () => void
}

const DESCRIPTION_MAX = 500

/** The store's name, contact and address: registering a store, and changing it later. */
export function StoreDetailsForm({ initialValues, submitLabel, onSave, onCancel }: StoreDetailsFormProps) {
  const descriptionId = useId()
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState<FieldErrors<keyof StoreDetailsValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  function change(field: keyof StoreDetailsValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateStoreDetails(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    setSaving(true)
    try {
      await onSave(toStoreDetailsRequest(values))
    } catch (error) {
      const fieldErrors = isApiError(error) ? serverFieldErrors(error, STORE_FIELDS) : {}
      if (hasErrors(fieldErrors)) setErrors(fieldErrors)
      else setFormError(error instanceof Error ? error.message : 'Something went wrong. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const text = (field: keyof StoreDetailsValues) => ({
    value: values[field],
    onChange: (event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value),
    error: errors[field],
  })

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField label="Store name" autoComplete="organization" className="sm:col-span-2" {...text('name')} />
        <div className="sm:col-span-2">
          <label htmlFor={descriptionId} className="mb-1.5 block font-heading text-sm font-bold text-heading">
            Description (optional)
          </label>
          <textarea
            id={descriptionId}
            value={values.description}
            onChange={(event) => change('description', event.target.value)}
            maxLength={DESCRIPTION_MAX}
            rows={2}
            placeholder="e.g. Fresh vegetables, fruits and daily essentials"
            aria-invalid={errors.description ? true : undefined}
            className={cn('w-full rounded-lg border p-3 text-heading outline-none focus:border-brand', errors.description ? 'border-hot' : 'border-line')}
          />
          {errors.description && <p className="mt-1 text-sm text-hot">{errors.description}</p>}
        </div>
        <TextField
          label="Store phone"
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          maxLength={10}
          leading="+91"
          hint="Customers and delivery partners call this number."
          {...text('phone')}
          onChange={(event) => change('phone', event.target.value.replace(/\D/g, ''))}
        />
        <TextField label="Store email (optional)" type="email" autoComplete="email" {...text('email')} />
        <TextField label="Street address" autoComplete="address-line1" className="sm:col-span-2" {...text('addressLine1')} />
        <TextField label="Area, landmark (optional)" autoComplete="address-line2" className="sm:col-span-2" {...text('addressLine2')} />
        <TextField label="City" autoComplete="address-level2" {...text('city')} />
        <TextField label="State" autoComplete="address-level1" {...text('state')} />
        <TextField
          label="PIN code"
          inputMode="numeric"
          autoComplete="postal-code"
          maxLength={6}
          {...text('pincode')}
          onChange={(event) => change('pincode', event.target.value.replace(/\D/g, ''))}
        />
      </div>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={saving}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button variant="ghost" onClick={onCancel} disabled={saving}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  )
}

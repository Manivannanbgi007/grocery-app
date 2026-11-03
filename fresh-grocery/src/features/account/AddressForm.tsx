import { useState, type ChangeEvent, type FormEvent } from 'react'
import type { AddressRequest } from '../../api/customers'
import { isApiError } from '../../api/client'
import type { AddressLabel } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/TextField'
import { ADDRESS_LABELS } from '../../utils/address'
import { cn } from '../../utils/cn'
import { hasErrors, serverFieldErrors, type FieldErrors } from '../auth/validation'
import { ADDRESS_FIELDS, toAddressRequest, validateAddress, type AddressField, type AddressValues } from './addressValidation'

interface AddressFormProps {
  initialValues: AddressValues
  submitLabel: string
  /** Saves the address. When it fails, the form shows the backend's field errors or message. */
  onSave: (request: AddressRequest) => Promise<unknown>
  onCancel?: () => void
  /** false for the first address, which always becomes the default */
  showDefaultOption?: boolean
}

const LABELS: AddressLabel[] = ['HOME', 'WORK', 'OTHER']

/** A delivery address form (checkout and address book), with the backend's rules checked as the customer submits. */
export function AddressForm({ initialValues, submitLabel, onSave, onCancel, showDefaultOption = true }: AddressFormProps) {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState<FieldErrors<AddressField>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  function change<Field extends keyof AddressValues>(field: Field, value: AddressValues[Field]) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateAddress(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    setSaving(true)
    try {
      await onSave(toAddressRequest(values))
    } catch (error) {
      const fieldErrors = isApiError(error) ? serverFieldErrors(error, ADDRESS_FIELDS) : {}
      if (hasErrors(fieldErrors)) setErrors(fieldErrors)
      else setFormError(error instanceof Error ? error.message : 'Something went wrong. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const text = (field: Exclude<AddressField, 'label'>) => ({
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

      <fieldset>
        <legend className="mb-1.5 font-heading text-sm font-bold text-heading">Save as</legend>
        <div className="flex flex-wrap gap-2">
          {LABELS.map((label) => (
            <label
              key={label}
              className={cn(
                'cursor-pointer rounded-full border px-4 py-1.5 text-sm font-bold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand',
                values.label === label ? 'border-brand bg-brand-light text-brand-text' : 'border-line text-heading hover:border-brand',
              )}
            >
              <input type="radio" name="address-label" value={label} checked={values.label === label} onChange={() => change('label', label)} className="sr-only" />
              {ADDRESS_LABELS[label]}
            </label>
          ))}
        </div>
      </fieldset>

      <div className="grid gap-5 sm:grid-cols-2">
        <TextField label="Full name" autoComplete="name" {...text('contactName')} />
        <TextField
          label="Mobile number"
          type="tel"
          inputMode="numeric"
          autoComplete="tel-national"
          maxLength={10}
          leading="+91"
          {...text('contactPhone')}
          onChange={(event) => change('contactPhone', event.target.value.replace(/\D/g, ''))}
        />
        <TextField label="Flat, house no., building" autoComplete="address-line1" className="sm:col-span-2" {...text('addressLine1')} />
        <TextField label="Street, area (optional)" autoComplete="address-line2" className="sm:col-span-2" {...text('addressLine2')} />
        <TextField label="Landmark (optional)" hint="Helps the delivery partner find you, e.g. Near Indiranagar Metro." className="sm:col-span-2" {...text('landmark')} />
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

      {showDefaultOption && (
        <label className="flex items-center gap-2 text-sm text-heading">
          <input type="checkbox" checked={values.setAsDefault} onChange={(event) => change('setAsDefault', event.target.checked)} className="size-4 accent-brand" />
          Make this my default address
        </label>
      )}

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

import { useState, type ChangeEvent, type FormEvent } from 'react'
import { isApiError } from '../../api/client'
import type { DiscountType } from '../../api/types'
import type { CouponRequest } from '../../api/vendorCoupons'
import { Button } from '../../components/ui/Button'
import { DateField } from '../../components/ui/DateField'
import { TextField } from '../../components/ui/TextField'
import { cn } from '../../utils/cn'
import { hasErrors, serverFieldErrors, type FieldErrors } from '../auth/validation'
import { COUPON_FIELDS, toCouponRequest, validateCoupon, type CouponValues } from './couponValidation'

interface CouponFormProps {
  initialValues: CouponValues
  submitLabel: string
  /** Saves the coupon. When it fails, the form shows the backend's field errors or message (e.g. a code in use). */
  onSave: (request: CouponRequest) => Promise<unknown>
  onCancel: () => void
}

const TYPES: { type: DiscountType; label: string }[] = [
  { type: 'PERCENTAGE', label: 'Percentage off' },
  { type: 'FLAT', label: 'Fixed amount off' },
]

/** A coupon (a store's in the vendor panel, the platform's in the admin panel): code, what it gives, from when to when, and how often it can be used. */
export function CouponForm({ initialValues, submitLabel, onSave, onCancel }: CouponFormProps) {
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState<FieldErrors<keyof CouponValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const percentage = values.discountType === 'PERCENTAGE'

  function change<Field extends keyof CouponValues>(field: Field, value: CouponValues[Field]) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateCoupon(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    setSaving(true)
    try {
      await onSave(toCouponRequest(values))
    } catch (error) {
      const fieldErrors = isApiError(error) ? serverFieldErrors(error, COUPON_FIELDS) : {}
      // 409 "Coupon code FRESH20 is already taken. Choose another code." belongs next to the code
      if (isApiError(error, 409)) setErrors({ code: error.message })
      else if (hasErrors(fieldErrors)) setErrors(fieldErrors)
      else setFormError(error instanceof Error ? error.message : 'Something went wrong. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const text = (field: keyof CouponValues) => ({
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
        <TextField label="Code" hint="What customers type, e.g. FRESH20." {...text('code')} onChange={(event) => change('code', event.target.value.toUpperCase())} />
        <TextField label="Description" hint="Shown to customers." {...text('description')} />
        <fieldset className="sm:col-span-2">
          <legend className="mb-1.5 font-heading text-sm font-bold text-heading">Discount</legend>
          <div className="flex flex-wrap gap-2">
            {TYPES.map(({ type, label }) => (
              <label
                key={type}
                className={cn(
                  'cursor-pointer rounded-full border px-4 py-1.5 text-sm font-bold has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand',
                  values.discountType === type ? 'border-brand bg-brand-light text-brand-text' : 'border-line text-heading hover:border-brand',
                )}
              >
                <input type="radio" name="discount-type" checked={values.discountType === type} onChange={() => change('discountType', type)} className="sr-only" />
                {label}
              </label>
            ))}
          </div>
        </fieldset>
        <TextField
          label={percentage ? 'Discount (%)' : 'Amount off (₹)'}
          inputMode="decimal"
          leading={percentage ? undefined : '₹'}
          trailing={percentage ? <span className="pr-4 text-sm">%</span> : undefined}
          {...text('discountValue')}
        />
        {percentage ? (
          <TextField label="At most (optional)" inputMode="decimal" leading="₹" hint="The most it takes off an order." {...text('maxDiscountAmount')} />
        ) : (
          <div />
        )}
        <TextField label="Minimum items total" inputMode="decimal" leading="₹" hint="0 = no minimum." {...text('minOrderAmount')} />
        <div />
        <DateField label="First day" value={values.validFrom} onChange={(day) => change('validFrom', day)} error={errors.validFrom} />
        <DateField label="Last day" min={values.validFrom || undefined} value={values.validUntil} onChange={(day) => change('validUntil', day)} error={errors.validUntil} />
        <TextField label="Uses in total (optional)" inputMode="numeric" hint="Empty = no limit." {...text('usageLimit')} />
        <TextField label="Uses per customer" inputMode="numeric" {...text('perCustomerLimit')} />
      </div>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={saving}>
          {submitLabel}
        </Button>
        <Button variant="ghost" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

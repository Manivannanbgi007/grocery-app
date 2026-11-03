import { useId, useState, type ChangeEvent, type FormEvent } from 'react'
import { isApiError } from '../../../api/client'
import type { ProductUnit } from '../../../api/types'
import type { ProductRequest } from '../../../api/vendorProducts'
import { Button } from '../../../components/ui/Button'
import { SelectField } from '../../../components/ui/SelectField'
import { TextField } from '../../../components/ui/TextField'
import { useCategories } from '../../../hooks/useCategories'
import { cn } from '../../../utils/cn'
import { formatPrice } from '../../../utils/format'
import { hasErrors, serverFieldErrors, type FieldErrors } from '../../auth/validation'
import { GST_RATES, PRODUCT_FIELDS, sellingPriceOf, toProductRequest, UNIT_LABELS, validateProduct, type ProductValues } from './productValidation'

interface ProductFormProps {
  initialValues: ProductValues
  submitLabel: string
  /** Saves the product. When it fails, the form shows the backend's field errors or message (e.g. a SKU in use). */
  onSave: (request: ProductRequest) => Promise<unknown>
}

const DESCRIPTION_MAX = 1000

/** A product's details: name, category, code, pack size and prices (with the price customers will pay). */
export function ProductForm({ initialValues, submitLabel, onSave }: ProductFormProps) {
  const descriptionId = useId()
  const { data: categories = [] } = useCategories()
  const [values, setValues] = useState(initialValues)
  const [errors, setErrors] = useState<FieldErrors<keyof ProductValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  function change<Field extends keyof ProductValues>(field: Field, value: ProductValues[Field]) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateProduct(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    setSaving(true)
    try {
      await onSave(toProductRequest(values))
    } catch (error) {
      const fieldErrors = isApiError(error) ? serverFieldErrors(error, PRODUCT_FIELDS) : {}
      // 409 "Your store already has a product with SKU ..." belongs next to the code
      if (isApiError(error, 409)) setErrors({ sku: error.message })
      else if (hasErrors(fieldErrors)) setErrors(fieldErrors)
      else setFormError(error instanceof Error ? error.message : 'Something went wrong. Please try again.')
    } finally {
      setSaving(false)
    }
  }

  const text = (field: keyof ProductValues) => ({
    value: values[field],
    onChange: (event: ChangeEvent<HTMLInputElement>) => change(field, event.target.value),
    error: errors[field],
  })

  const price = Number(values.price)
  const discount = Number(values.discountPercentage || 0)
  const showPreview = values.price.trim() !== '' && price > 0 && discount >= 0 && discount <= 90
  const gstRates = [...new Set([...GST_RATES, Number(values.taxPercentage)])].sort((a, b) => a - b)

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField label="Product name" className="sm:col-span-2" {...text('name')} />
        <SelectField
          label="Category"
          value={values.categoryId}
          onChange={(categoryId) => change('categoryId', categoryId)}
          placeholder="Choose a category"
          options={categories.map((category) => ({ value: String(category.id), label: category.name }))}
          error={errors.categoryId}
        />
        <TextField label="Brand (optional)" {...text('brand')} />
        <TextField label="Product code (SKU)" hint="Your own code, unique in your store." {...text('sku')} />
        <div className="grid grid-cols-2 gap-3">
          <TextField label="Pack size" inputMode="decimal" {...text('unitQuantity')} />
          <SelectField label="Unit" value={values.unit} onChange={(unit) => change('unit', unit as ProductUnit)} options={Object.entries(UNIT_LABELS).map(([unit, label]) => ({ value: unit, label }))} />
        </div>
        <div className="sm:col-span-2">
          <label htmlFor={descriptionId} className="mb-1.5 block font-heading text-sm font-bold text-heading">
            Description (optional)
          </label>
          <textarea
            id={descriptionId}
            value={values.description}
            onChange={(event) => change('description', event.target.value)}
            maxLength={DESCRIPTION_MAX}
            rows={3}
            aria-invalid={errors.description ? true : undefined}
            className={cn('w-full rounded-lg border p-3 text-heading outline-none focus:border-brand', errors.description ? 'border-hot' : 'border-line')}
          />
          {errors.description && <p className="mt-1 text-sm text-hot">{errors.description}</p>}
        </div>
        <TextField label="Price (MRP)" inputMode="decimal" leading="₹" {...text('price')} />
        <TextField label="Discount" inputMode="decimal" trailing={<span className="pr-4 text-sm">%</span>} {...text('discountPercentage')} />
        <SelectField
          label="GST"
          value={values.taxPercentage}
          onChange={(rate) => change('taxPercentage', rate)}
          options={gstRates.map((rate) => ({ value: String(rate), label: `${rate} %` }))}
          hint="Included in the price."
        />
        <div role="status" className="flex items-center rounded-lg bg-brand-light px-4 py-3 text-sm text-heading">
          {showPreview ? (
            <span>
              Customers pay <strong className="text-brand-text">{formatPrice(sellingPriceOf(price, discount))}</strong>
              {discount > 0 && <> instead of {formatPrice(price)}</>}.
            </span>
          ) : (
            'Enter the price to see what customers pay.'
          )}
        </div>
      </div>
      <Button type="submit" loading={saving}>
        {submitLabel}
      </Button>
    </form>
  )
}

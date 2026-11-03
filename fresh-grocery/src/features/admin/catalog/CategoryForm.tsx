import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import type { CategoryRequest } from '../../../api/adminCatalog'
import { isApiError } from '../../../api/client'
import type { Category } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { useCategoryChanges } from './useAdminCatalog'

interface CategoryValues {
  name: string
  description: string
  imageUrl: string
  /** a whole number as typed, '' = 0 */
  displayOrder: string
}

type CategoryErrors = Partial<Record<keyof CategoryValues, string>>

/** The CategoryRequest rules (CategoryRequest.java), checked before asking the server. */
function validateCategory(values: CategoryValues): CategoryErrors {
  const errors: CategoryErrors = {}
  if (!values.name.trim()) errors.name = 'Enter a name, e.g. Frozen Foods.'
  else if (values.name.trim().length > 60) errors.name = 'Use at most 60 characters.'
  if (values.description.length > 255) errors.description = 'Use at most 255 characters.'
  if (values.imageUrl.length > 500) errors.imageUrl = 'Use at most 500 characters.'
  if (values.displayOrder.trim() && !/^\d+$/.test(values.displayOrder.trim())) errors.displayOrder = 'Enter a whole number, 0 or more.'
  return errors
}

interface CategoryFormProps {
  /** the category to change; left out to add a new one */
  category?: Category
  onDone: () => void
}

/** Add or change a category of the shop: name, description, picture and position. */
export function CategoryForm({ category, onDone }: CategoryFormProps) {
  const { create, update } = useCategoryChanges()
  const [values, setValues] = useState<CategoryValues>({
    name: category?.name ?? '',
    description: category?.description ?? '',
    imageUrl: category?.imageUrl ?? '',
    displayOrder: category?.displayOrder === null || category?.displayOrder === undefined ? '' : String(category.displayOrder),
  })
  const [errors, setErrors] = useState<CategoryErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  function change(field: keyof CategoryValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateCategory(values)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length > 0) return

    const request: CategoryRequest = {
      name: values.name.trim(),
      description: values.description.trim() || null,
      imageUrl: values.imageUrl.trim() || null,
      displayOrder: values.displayOrder.trim() ? Number(values.displayOrder) : null,
    }
    const callbacks = {
      onSuccess: () => {
        toast.success(category ? `${request.name} saved` : `${request.name} added`)
        onDone()
      },
      onError: (error: Error) => {
        if (isApiError(error, 409)) setErrors({ name: error.message })
        else if (!isApiError(error, 401)) setFormError(error.message)
      },
    }
    if (category) update.mutate({ categoryId: category.id, request }, callbacks)
    else create.mutate(request, callbacks)
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-[1fr_10rem]">
        <TextField label="Category name" value={values.name} onChange={(event) => change('name', event.target.value)} error={errors.name} />
        <TextField label="Position" inputMode="numeric" hint="lower = first" value={values.displayOrder} onChange={(event) => change('displayOrder', event.target.value)} error={errors.displayOrder} />
      </div>
      <TextField label="Description (optional)" value={values.description} onChange={(event) => change('description', event.target.value)} error={errors.description} />
      <TextField label="Picture address (optional)" placeholder="https://..." value={values.imageUrl} onChange={(event) => change('imageUrl', event.target.value)} error={errors.imageUrl} hint="Without a picture the shop shows the category's emoji." />
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={create.isPending || update.isPending}>
          {category ? 'Save category' : 'Add category'}
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

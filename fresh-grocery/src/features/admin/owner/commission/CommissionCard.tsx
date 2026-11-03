import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../../api/client'
import type { StoreDetails } from '../../../../api/types'
import { Button } from '../../../../components/ui/Button'
import { TextField } from '../../../../components/ui/TextField'
import { serverFieldErrors } from '../../../auth/validation'
import { useUpdateCommission } from './useCommission'

const MAX_COMMISSION = 50
const PERCENTAGE = /^\d{1,2}(\.\d{1,2})?$/

/** The UpdateCommissionRequest rule: 0 to 50 with at most two decimals. */
function commissionError(typed: string): string | undefined {
  if (!typed.trim()) return 'Enter a percentage, e.g. 10.'
  if (!PERCENTAGE.test(typed.trim()) || Number(typed) > MAX_COMMISSION) return `Use a number from 0 to ${MAX_COMMISSION}, with at most two decimals.`
  return undefined
}

/** Super admin only (store page): the share of each order's items the platform keeps. */
export function CommissionCard({ store }: { store: StoreDetails }) {
  const update = useUpdateCommission(store.id)
  const [typed, setTyped] = useState(String(store.commissionPercentage))
  const [error, setError] = useState<string | undefined>()
  const unchanged = Number(typed) === store.commissionPercentage

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = commissionError(typed)
    setError(found)
    if (found) return

    update.mutate(Number(typed), {
      onSuccess: (changed) => toast.success(`${changed.name} now pays ${changed.commissionPercentage}% commission on new orders`),
      onError: (saveError) => {
        const fieldError = isApiError(saveError, 400) ? serverFieldErrors(saveError, ['commissionPercentage'] as const).commissionPercentage : undefined
        if (fieldError) setError(fieldError)
        else if (!isApiError(saveError, 401)) toast.error(saveError.message)
      },
    })
  }

  return (
    <section aria-labelledby="commission-title" className="rounded-2xl border border-line bg-card p-6">
      <h2 id="commission-title" className="mb-1 text-xl">
        Platform commission
      </h2>
      <p className="mb-4 text-sm">The platform keeps this share of the items of each delivered order; the store gets the rest. A change counts for orders placed from now on.</p>
      <form onSubmit={handleSubmit} noValidate className="flex flex-wrap items-start gap-3">
        <TextField
          label="Commission"
          trailing={<span className="pr-3 text-sm text-heading">%</span>}
          inputMode="decimal"
          value={typed}
          onChange={(event) => {
            setTyped(event.target.value)
            setError(undefined)
          }}
          error={error}
          className="w-32"
        />
        <Button type="submit" variant="outline" loading={update.isPending} disabled={unchanged} className="mt-7">
          Save
        </Button>
      </form>
    </section>
  )
}

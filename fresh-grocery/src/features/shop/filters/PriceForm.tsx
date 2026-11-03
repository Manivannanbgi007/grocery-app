import { useId, useState, type FormEvent } from 'react'
import { Button } from '../../../components/ui/Button'
import type { ShopFilters } from '../shopFilters'

/** An empty box means "no limit". */
function toPrice(text: string): number | undefined {
  return text.trim() === '' ? undefined : Number(text)
}

interface PriceFormProps {
  minPrice: number | undefined
  maxPrice: number | undefined
  onApply: (changes: Partial<ShopFilters>) => void
}

/** The filter bar's price range: the two boxes keep what you type until "Apply price". */
export function PriceForm({ minPrice, maxPrice, onApply }: PriceFormProps) {
  const [min, setMin] = useState(minPrice?.toString() ?? '')
  const [max, setMax] = useState(maxPrice?.toString() ?? '')
  const [error, setError] = useState<string | null>(null)
  const id = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const newMin = toPrice(min)
    const newMax = toPrice(max)
    if (newMin !== undefined && newMax !== undefined && newMin > newMax) {
      setError('The lowest price must not be higher than the highest price.')
      return
    }
    setError(null)
    onApply({ minPrice: newMin, maxPrice: newMax })
  }

  const inputClass = 'h-10 w-28 rounded-lg border border-line bg-card px-3 text-sm text-heading outline-none focus:border-brand'

  return (
    <form onSubmit={handleSubmit} noValidate>
      <fieldset>
        <legend className="mb-2 font-heading text-sm font-bold text-heading">Price (₹)</legend>
        <div className="flex flex-wrap items-center gap-2">
          <label htmlFor={`${id}-min`} className="sr-only">
            Lowest price
          </label>
          <input id={`${id}-min`} type="number" min={0} inputMode="decimal" placeholder="Min" value={min} onChange={(event) => setMin(event.target.value)} className={inputClass} />
          <span aria-hidden="true">–</span>
          <label htmlFor={`${id}-max`} className="sr-only">
            Highest price
          </label>
          <input id={`${id}-max`} type="number" min={0} inputMode="decimal" placeholder="Max" value={max} onChange={(event) => setMax(event.target.value)} className={inputClass} />
          <Button type="submit">Apply price</Button>
        </div>
        {error && (
          <p role="alert" className="mt-2 text-sm text-hot-text">
            {error}
          </p>
        )}
      </fieldset>
    </form>
  )
}

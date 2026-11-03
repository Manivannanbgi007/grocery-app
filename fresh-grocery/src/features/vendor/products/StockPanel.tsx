import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Stock } from '../../../api/types'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { TextField } from '../../../components/ui/TextField'
import { useStock, useStockChanges } from './useVendorProducts'

const MAX_QUANTITY = 1_000_000
const DEFAULT_THRESHOLD = 5

/** A whole number from 0 (or 1) to 1,000,000, or null. */
function wholeNumber(text: string, min: number): number | null {
  if (!/^\d+$/.test(text.trim())) return null
  const value = Number(text)
  return value >= min && value <= MAX_QUANTITY ? value : null
}

/** "Counted the shelf": the exact number of units, and when to warn about low stock. The first save creates the stock. */
function SetStockForm({ productId, stock }: { productId: number; stock: Stock | null }) {
  const { set } = useStockChanges(productId)
  const [onHand, setOnHand] = useState(stock ? String(stock.quantityOnHand) : '')
  const [threshold, setThreshold] = useState(String(stock?.lowStockThreshold ?? DEFAULT_THRESHOLD))
  const [errors, setErrors] = useState<{ onHand?: string; threshold?: string; form?: string }>({})

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const quantityOnHand = wholeNumber(onHand, 0)
    const lowStockThreshold = wholeNumber(threshold, 0)
    const found = {
      onHand: quantityOnHand === null ? 'Enter a whole number from 0 to 1,000,000.' : undefined,
      threshold: lowStockThreshold === null ? 'Enter a whole number from 0 to 1,000,000.' : undefined,
    }
    setErrors(found)
    if (quantityOnHand === null || lowStockThreshold === null) return
    set.mutate(
      { quantityOnHand, lowStockThreshold },
      {
        onSuccess: () => toast.success('Stock saved'),
        // e.g. "Quantity on hand cannot be less than the 3 units reserved for open orders"
        onError: (error) => setErrors({ form: isApiError(error, 401) ? undefined : error.message }),
      },
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-3">
      <p className="font-heading font-bold text-heading">{stock ? 'Counted the shelf' : 'Set the stock'}</p>
      {errors.form && (
        <p role="alert" className="text-sm text-hot">
          {errors.form}
        </p>
      )}
      <div className="grid grid-cols-2 gap-3">
        <TextField label="Units in the store" inputMode="numeric" value={onHand} onChange={(event) => setOnHand(event.target.value)} error={errors.onHand} />
        <TextField label="Warn at" inputMode="numeric" hint="units left" value={threshold} onChange={(event) => setThreshold(event.target.value)} error={errors.threshold} />
      </div>
      <Button type="submit" size="sm" loading={set.isPending}>
        Save stock
      </Button>
    </form>
  )
}

/** "Goods arrived": adds the received units to the units on hand. */
function AddStockForm({ productId }: { productId: number }) {
  const { add } = useStockChanges(productId)
  const [received, setReceived] = useState('')
  const [error, setError] = useState<string | undefined>()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const quantity = wholeNumber(received, 1)
    if (quantity === null) {
      setError('Enter how many units arrived (at least 1).')
      return
    }
    setError(undefined)
    add.mutate(quantity, {
      onSuccess: (stock) => {
        setReceived('')
        toast.success(`Added ${quantity}: ${stock.quantityOnHand} units in the store now`)
      },
      onError: (addError) => setError(isApiError(addError, 401) ? undefined : addError.message),
    })
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-3">
      <p className="font-heading font-bold text-heading">Goods arrived</p>
      <TextField label="Units received" inputMode="numeric" value={received} onChange={(event) => setReceived(event.target.value)} error={error} />
      <Button type="submit" size="sm" variant="light" loading={add.isPending}>
        Add to stock
      </Button>
    </form>
  )
}

/** The stock of one product (inventory-service): the numbers, and the two ways to change them. */
export function StockPanel({ productId }: { productId: number }) {
  const stockQuery = useStock(productId)

  let content
  if (stockQuery.isPending) {
    content = <LoadingState className="min-h-40" />
  } else if (stockQuery.isError) {
    content = <ErrorState compact title="Could not load the stock" error={stockQuery.error} onRetry={() => void stockQuery.refetch()} />
  } else if (stockQuery.data === null) {
    content = (
      <>
        <p className="mb-4 rounded-lg bg-new/10 px-4 py-3 text-sm text-heading">No stock yet: customers see this product as sold out until you set it.</p>
        <SetStockForm productId={productId} stock={null} />
      </>
    )
  } else {
    const stock = stockQuery.data
    const row = 'flex justify-between gap-4 border-b border-line py-2'
    content = (
      <>
        <dl className="mb-2 text-sm">
          <div className={row}>
            <dt>In the store</dt>
            <dd className="font-bold text-heading">{stock.quantityOnHand}</dd>
          </div>
          <div className={row}>
            <dt>Reserved for open orders</dt>
            <dd className="font-bold text-heading">{stock.reservedQuantity}</dd>
          </div>
          <div className={row}>
            <dt>Customers can buy</dt>
            <dd className="font-heading text-lg font-bold text-brand-text">{stock.availableQuantity}</dd>
          </div>
        </dl>
        <p className="mb-5 flex flex-wrap items-center gap-2 text-sm">
          Warn at {stock.lowStockThreshold} left
          {stock.outOfStock ? <Badge tone="hot">Out of stock</Badge> : stock.lowStock && <Badge tone="new">Low stock</Badge>}
        </p>
        {/* Side by side only while the card is wide (below lg); in the side column of large screens they stack */}
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-1">
          <AddStockForm productId={productId} />
          {/* A new key re-creates the form with the saved numbers after every change */}
          <SetStockForm key={stock.updatedAt} productId={productId} stock={stock} />
        </div>
      </>
    )
  }

  return (
    <section aria-labelledby="stock-title" className="rounded-2xl border border-line bg-card p-6">
      <h2 id="stock-title" className="mb-4 text-xl">
        Stock
      </h2>
      {content}
    </section>
  )
}

import { useQuery } from '@tanstack/react-query'
import { Sparkles, Tag, TicketPercent, X } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { getCoupons } from '../../api/coupons'
import { queryKeys } from '../../api/queryKeys'
import type { Cart } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { couponBenefit, couponCondition } from '../../utils/discount'
import { formatPrice } from '../../utils/format'
import { useCartChanges } from './useCart'

interface CouponSectionProps {
  cart: Cart
  changes: ReturnType<typeof useCartChanges>
}

/**
 * Coupon box, the coupon in use, and the coupons this cart could use (click one to apply it).
 * Whether a coupon can be used is decided by the backend; its message is shown as it comes, e.g.
 * "Coupon SAVE10 needs an items total of at least 199.00 (add 12.00 more)".
 */
export function CouponSection({ cart, changes }: CouponSectionProps) {
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const inputId = useId()
  const { applyCoupon, removeCoupon } = changes

  // The coupons of the platform and of the cart's store
  const { data: coupons = [] } = useQuery({
    queryKey: queryKeys.coupons(cart.storeId ?? undefined),
    queryFn: ({ signal }) => getCoupons(cart.storeId ?? undefined, signal),
  })
  const suggestions = coupons.filter((coupon) => coupon.code !== cart.couponCode)

  function apply(couponCode: string) {
    setError(null)
    applyCoupon.mutate(couponCode, {
      onSuccess: (updated) => {
        setCode('')
        toast.success(`Coupon ${updated.couponCode} applied: you save ${formatPrice(updated.totals.couponDiscount)}`)
      },
      onError: (applyError) => setError(applyError.message),
    })
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const typed = code.trim().toUpperCase()
    if (!/^[A-Z0-9]{4,20}$/.test(typed)) {
      setError('A coupon code has 4 to 20 letters or digits.')
      return
    }
    apply(typed)
  }

  // The look: pink, the colour of savings on the cart page. The coupon in use is a dashed "ticket"; the coupons to try
  // are tickets too, which rise in one after the other and lift a little under the mouse.
  return (
    <section aria-labelledby={`${inputId}-title`} className="relative overflow-hidden rounded-3xl border border-line bg-card bg-linear-to-br from-hot/10 via-card to-card p-5">
      <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-linear-to-r from-hot to-violet" />
      <h2 id={`${inputId}-title`} className="flex items-center gap-3 text-lg">
        <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-xl bg-hot text-white shadow-md shadow-hot/30">
          <Tag className="size-5" />
        </span>
        Coupon
      </h2>

      {cart.couponCode && (
        <div className="mt-4 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-2xl border-2 border-dashed border-hot/40 bg-hot/5 px-3 py-2 text-sm">
          <TicketPercent className="size-5 text-hot-text" aria-hidden="true" />
          <span className="font-heading font-bold tracking-wider text-heading">{cart.couponCode}</span>
          {/* couponMessage is set when the coupon cannot be used any more (e.g. the cart total dropped) */}
          {cart.couponMessage ? <span className="text-hot">{cart.couponMessage}</span> : <span className="text-brand-text">You save {formatPrice(cart.totals.couponDiscount)}</span>}
          <Button
            variant="danger-outline"
            size="sm"
            onClick={() => removeCoupon.mutate()}
            disabled={removeCoupon.isPending}
            className="ml-auto"
            aria-label={`Remove coupon ${cart.couponCode}`}
          >
            <X className="size-4" aria-hidden="true" />
            Remove
          </Button>
        </div>
      )}

      <form onSubmit={handleSubmit} noValidate className="mt-4 flex max-w-md gap-2">
        <label htmlFor={inputId} className="sr-only">
          Coupon code
        </label>
        <input
          id={inputId}
          value={code}
          onChange={(event) => setCode(event.target.value)}
          placeholder="Coupon code"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className="h-10 min-w-0 flex-1 rounded-xl border border-line bg-card px-3 text-sm text-heading uppercase transition-shadow outline-none placeholder:normal-case focus:border-hot focus:ring-4 focus:ring-hot/15"
        />
        <Button type="submit" size="sm" loading={applyCoupon.isPending} className="h-10">
          Apply coupon
        </Button>
      </form>
      {error && (
        <p id={`${inputId}-error`} role="alert" className="mt-2 text-sm text-hot">
          {error}
        </p>
      )}

      {suggestions.length > 0 && (
        <div className="mt-5">
          <p className="mb-2 flex items-center gap-1.5 text-xs font-bold tracking-wide text-body uppercase">
            <Sparkles className="size-3.5 text-hot-text" aria-hidden="true" />
            Coupons you can try
          </p>
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {suggestions.map((coupon, index) => (
              <li key={coupon.code} className="motion-safe:animate-rise" style={{ animationDelay: `${index * 70}ms` }}>
                <button
                  type="button"
                  onClick={() => apply(coupon.code)}
                  disabled={applyCoupon.isPending}
                  aria-label={`Apply coupon ${coupon.code}: ${couponBenefit(coupon)}`}
                  className="group flex size-full items-center gap-3 rounded-2xl border-2 border-dashed border-hot/30 bg-card px-3 py-2.5 text-left transition duration-200 hover:-translate-y-0.5 hover:border-hot hover:shadow-md disabled:opacity-50"
                >
                  <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-full bg-hot/10 text-hot-text transition-colors group-hover:bg-hot group-hover:text-white">
                    <TicketPercent className="size-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block font-heading text-sm font-bold tracking-wider text-heading">{coupon.code}</span>
                    <span className="block text-xs text-hot-text">
                      {couponBenefit(coupon)}
                      {couponCondition(coupon) && <span className="text-body"> · {couponCondition(coupon)}</span>}
                    </span>
                  </span>
                  <span className="shrink-0 rounded-full bg-hot px-2.5 py-1 font-heading text-xs font-bold text-white shadow-sm shadow-hot/30">Apply</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  )
}

import { Copy, TicketPercent } from 'lucide-react'
import { toast } from 'sonner'
import type { Coupon } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Countdown } from '../../components/ui/Countdown'
import { ErrorState } from '../../components/ui/ErrorState'
import { SectionTitle } from '../../components/ui/SectionTitle'
import { Skeleton } from '../../components/ui/Skeleton'
import { usePublicCoupons } from '../../hooks/usePublicCoupons'
import { useScrollToHash } from '../../hooks/useScrollToHash'
import { useStoreName } from '../../hooks/useStores'
import { cn } from '../../utils/cn'
import { couponBenefit, couponCondition } from '../../utils/discount'

async function copyCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    toast.success(`Coupon ${code} copied. Use it in your cart.`)
  } catch {
    // The clipboard is not available (e.g. an old browser): show the code instead
    toast.info(`Your coupon code: ${code}`)
  }
}

/**
 * The offer cards take turns in four colours (only the look; Tailwind needs the whole class names here): the card's
 * dashed border and light gradient, the benefit's colour, the code's dashed border and the ticket icon's gradient.
 */
const CARD_LOOKS = [
  { card: 'border-hot/40 from-hot/10 via-card to-violet-light', text: 'text-hot-text', code: 'border-hot', icon: 'from-hot to-violet shadow-hot/30' },
  { card: 'border-violet/40 from-violet-light via-card to-sale-light', text: 'text-violet-text', code: 'border-violet', icon: 'from-violet to-deep-blue shadow-violet/30' },
  { card: 'border-sale/50 from-sale-light via-card to-brand-light', text: 'text-sale-text', code: 'border-sale', icon: 'from-sale to-brand shadow-sale/30' },
  { card: 'border-new/50 from-new-light via-card to-warn-light', text: 'text-warn-text', code: 'border-new', icon: 'from-new to-hot shadow-new/30' },
]

/**
 * One coupon as a colourful "ticket": a floating ticket icon, what it gives, where it works, the time left and the code
 * to copy. It lifts a little under the mouse (motion-safe: not when the system asks for less motion).
 */
function OfferCard({ coupon, look }: { coupon: Coupon; look: (typeof CARD_LOOKS)[number] }) {
  const storeName = useStoreName(coupon.storeId)
  const condition = couponCondition(coupon)
  const where = coupon.storeId === null ? 'All stores' : `Only at ${storeName ?? 'one store'}`

  return (
    <article className={cn('relative flex h-full flex-col gap-3 overflow-hidden rounded-3xl border-2 border-dashed bg-linear-to-br p-5 transition duration-300 hover:-translate-y-1 hover:shadow-xl', look.card)}>
      {/* A soft light circle in the corner: decoration only */}
      <span aria-hidden="true" className="absolute -top-10 -right-10 size-32 rounded-full bg-card/60 blur-2xl" />
      <div className="relative flex items-start justify-between gap-3">
        <span aria-hidden="true" className={cn('grid size-11 shrink-0 place-items-center rounded-2xl bg-linear-to-br text-white shadow-lg motion-safe:animate-float', look.icon)}>
          <TicketPercent className="size-6" />
        </span>
        <span className="rounded-full bg-card px-3 py-1 text-xs font-bold whitespace-nowrap text-heading shadow-sm">{where}</span>
      </div>
      <h3 className={cn('relative text-2xl', look.text)}>{couponBenefit(coupon)}</h3>
      {coupon.description && <p className="relative text-heading">{coupon.description}</p>}
      {condition && <p className="relative text-sm">{condition}</p>}

      <div className="relative mt-auto space-y-3 pt-2">
        <Countdown until={coupon.validUntil} />
        <div className="flex flex-wrap items-center justify-between gap-2">
          <code className={cn('rounded-lg border-2 border-dashed bg-card px-3 py-1 font-heading font-bold tracking-widest text-heading', look.code)}>{coupon.code}</code>
          <Button variant="light" size="sm" onClick={() => void copyCode(coupon.code)} aria-label={`Copy coupon code ${coupon.code}`}>
            <Copy className="size-4" aria-hidden="true" />
            Copy code
          </Button>
        </div>
      </div>
    </article>
  )
}

/** The coupons anyone can use, with the time left until each one ends (the "Offers" link jumps here: /#offers). */
export function OffersStrip() {
  const { coupons, isPending, isError, error, refetch } = usePublicCoupons()
  useScrollToHash('offers', !isPending)

  // Coupons are a bonus on the home page: with none, the section is simply left out
  if (!isPending && !isError && coupons.length === 0) return null

  return (
    <section aria-labelledby="offers" className="page-container mt-16">
      <SectionTitle id="offers" title="Offers For You" />

      {isError ? (
        <ErrorState title="Could not load the offers" error={error} onRetry={refetch} />
      ) : (
        <ul className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
          {isPending
            ? Array.from({ length: 4 }, (_, index) => (
                <li key={index}>
                  <Skeleton className="h-64 rounded-2xl" />
                </li>
              ))
            : coupons.map((coupon, index) => (
                // The cards rise in one after the other, each in the next colour of CARD_LOOKS
                <li key={coupon.code} className="motion-safe:animate-rise" style={{ animationDelay: `${index * 90}ms` }}>
                  <OfferCard coupon={coupon} look={CARD_LOOKS[index % CARD_LOOKS.length]} />
                </li>
              ))}
        </ul>
      )}
    </section>
  )
}

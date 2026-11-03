import { Bike } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import type { Order } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { StarInput } from '../../components/ui/StarInput'
import { StarRating } from '../../components/ui/StarRating'
import { useDeliveryRating, useRateDelivery } from './useOrders'

const COMMENT_MAX = 500

/** "Rate the delivery" of a delivered order: once, 1 to 5 stars and an optional comment. Afterwards it shows the rating. */
export function DeliveryRatingPanel({ order }: { order: Order }) {
  const ratingQuery = useDeliveryRating(order.id, true)
  const rate = useRateDelivery(order.id)
  const [stars, setStars] = useState<number | null>(null)
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)
  const commentId = useId()

  // 404: this order has no delivery record (nothing to rate)
  if (ratingQuery.isPending || isApiError(ratingQuery.error, 404)) return null

  const partner = ratingQuery.data?.partnerName ?? order.deliveryPartnerName ?? 'your delivery partner'

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (stars === null) {
      setError('Please choose 1 to 5 stars.')
      return
    }
    setError(null)
    rate.mutate(
      { rating: stars, comment: comment.trim() || null },
      {
        onSuccess: () => toast.success('Thank you for rating the delivery'),
        onError: (rateError) => setError(rateError.message),
      },
    )
  }

  let content
  if (ratingQuery.isError) {
    content = <ErrorState compact title="Could not load your delivery rating" error={ratingQuery.error} onRetry={() => void ratingQuery.refetch()} />
  } else if (ratingQuery.data.rating !== null) {
    const { rating, comment: savedComment } = ratingQuery.data
    content = (
      <div className="space-y-1 text-sm text-heading">
        <p>
          You rated the delivery by {partner}: {rating} out of 5.
        </p>
        <StarRating value={rating} />
        {savedComment && <p className="italic">“{savedComment}”</p>}
      </div>
    )
  } else {
    content = (
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <StarInput label={`How was the delivery by ${partner}?`} value={stars} onChange={setStars} error={error ?? undefined} />
        <div>
          <label htmlFor={commentId} className="mb-1.5 block font-heading text-sm font-bold text-heading">
            Anything to add? (optional)
          </label>
          <textarea
            id={commentId}
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            maxLength={COMMENT_MAX}
            rows={2}
            placeholder="e.g. Very polite and on time"
            className="w-full rounded-lg border border-line bg-card p-3 text-sm text-heading outline-none focus:border-brand"
          />
        </div>
        <Button type="submit" loading={rate.isPending}>
          Send rating
        </Button>
      </form>
    )
  }

  return (
    // Yellow, like the stars: a bar along the top, a light amber wash in the corner, and a scooter that floats up and down
    // (motion-safe: not when the system asks for less motion)
    <section aria-labelledby="delivery-rating-title" className="relative overflow-hidden rounded-3xl border border-line bg-card bg-linear-to-br from-warn-light via-card to-card p-6">
      <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-accent" />
      <h2 id="delivery-rating-title" className="mb-4 flex items-center gap-3 text-xl">
        <span aria-hidden="true" className="grid size-9 shrink-0 place-items-center rounded-xl bg-accent text-ink shadow-md shadow-accent/30 motion-safe:animate-float">
          <Bike className="size-5" />
        </span>
        Rate the delivery
      </h2>
      {content}
    </section>
  )
}

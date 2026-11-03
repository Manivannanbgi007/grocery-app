import { Star } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { isApiError } from '../../api/client'
import type { OrderItem } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { StarInput } from '../../components/ui/StarInput'
import { useWriteReview } from './useOrders'

const COMMENT_MAX = 1000

interface ProductReviewFormProps {
  /** the delivered order the product came with */
  orderId: number
  item: OrderItem
}

/**
 * "Review this product" under a product of a delivered order. The backend allows one review per product and order;
 * it does not tell beforehand whether there is one, so "already reviewed" (409) is shown when it answers that.
 */
export function ProductReviewForm({ orderId, item }: ProductReviewFormProps) {
  const [open, setOpen] = useState(false)
  const [stars, setStars] = useState<number | null>(null)
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<'published' | 'already-reviewed' | null>(null)
  const commentId = useId()
  const write = useWriteReview(item.productId)

  if (result === 'published') {
    return (
      <p role="status" className="text-sm font-bold text-brand-text">
        Thank you! Your review of {item.productName} is published.
      </p>
    )
  }
  if (result === 'already-reviewed') {
    return (
      <p role="status" className="text-sm text-heading">
        You have already reviewed {item.productName} for this order.
      </p>
    )
  }
  if (!open) {
    return (
      <Button variant="light" size="sm" onClick={() => setOpen(true)} aria-label={`Review ${item.productName}`}>
        <Star className="size-4" aria-hidden="true" />
        Review this product
      </Button>
    )
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (stars === null) {
      setError('Please choose 1 to 5 stars.')
      return
    }
    setError(null)
    write.mutate(
      { orderId, rating: stars, comment: comment.trim() || null },
      {
        onSuccess: () => setResult('published'),
        onError: (writeError) => (isApiError(writeError, 409) ? setResult('already-reviewed') : setError(writeError.message)),
      },
    )
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-3 rounded-xl bg-surface p-4">
      <StarInput label={`Your rating for ${item.productName}`} value={stars} onChange={setStars} error={error ?? undefined} />
      <div>
        <label htmlFor={commentId} className="mb-1.5 block font-heading text-sm font-bold text-heading">
          Your review (optional)
        </label>
        <textarea
          id={commentId}
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          maxLength={COMMENT_MAX}
          rows={3}
          placeholder="What did you like, what not?"
          className="w-full rounded-lg border border-line bg-card p-3 text-sm text-heading outline-none focus:border-brand"
        />
      </div>
      <div className="flex gap-3">
        <Button type="submit" size="sm" loading={write.isPending}>
          Publish review
        </Button>
        <Button variant="ghost" size="sm" onClick={() => setOpen(false)} disabled={write.isPending}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Review } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { FilterSelect } from '../../../components/panel/FilterSelect'
import { Badge } from '../../../components/ui/Badge'
import { Button, ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { ReasonDialog } from '../../../components/ui/ReasonDialog'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StarRating } from '../../../components/ui/StarRating'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { cn } from '../../../utils/cn'
import { formatDate } from '../../../utils/format'
import { useStoreOptions } from '../stores/useAdminStores'
import { useAdminReviews, useProductNames, useReviewModeration } from './useAdminCatalog'

const HIDE_REASONS = ['Offensive language', 'Not about the product', 'Looks fake or paid']
const RATINGS = [5, 4, 3, 2, 1]

/** "Hide" (with a reason) or "Show" for one review. */
function ModerationButton({ review }: { review: Review }) {
  const { hide, show } = useReviewModeration()
  const [open, setOpen] = useState(false)

  function failed(error: Error) {
    setOpen(false)
    if (!isApiError(error, 401)) toast.error(error.message)
  }

  if (review.status === 'HIDDEN') {
    return (
      <Button size="sm" variant="light" loading={show.isPending} aria-label={`Show the review of ${review.reviewerName}`} onClick={() => show.mutate(review.id, { onSuccess: () => toast.success('The review is visible again'), onError: failed })}>
        Show
      </Button>
    )
  }
  return (
    <>
      <Button size="sm" variant="outline" aria-label={`Hide the review of ${review.reviewerName}`} onClick={() => setOpen(true)}>
        Hide
      </Button>
      <ReasonDialog
        open={open}
        title={`Hide the review of ${review.reviewerName}?`}
        message="Customers no longer see it, and the product's rating is worked out again without it."
        commonReasons={HIDE_REASONS}
        requiredMessage="Please give the reason (it is kept with the review)."
        confirmLabel="Hide review"
        loading={hide.isPending}
        onConfirm={(reason) =>
          hide.mutate(
            { reviewId: review.id, reason },
            {
              onSuccess: () => {
                setOpen(false)
                toast.success('The review is hidden')
              },
              onError: failed,
            },
          )
        }
        onCancel={() => setOpen(false)}
      />
    </>
  )
}

/** Every review: filter by status, stars and store; hide the ones that break the rules. /admin/reviews?status=HIDDEN&rating=1&store=1 */
export function AdminReviewsPage() {
  const { page, pageSize, filter, idFilter, setFilter, linkWith, goToPage, setPageSize } = useUrlFilters()
  const status = filter('status') === 'HIDDEN' || filter('status') === 'VISIBLE' ? (filter('status') as Review['status']) : undefined
  const rating = RATINGS.find((candidate) => String(candidate) === filter('rating'))
  const storeId = idFilter('store')
  const { data: stores = [] } = useStoreOptions()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useAdminReviews({ status, rating, storeId }, page, pageSize)
  const productNames = useProductNames([...new Set(data?.content.map((review) => review.productId) ?? [])])
  const storeName = (id: number) => stores.find((store) => store.id === id)?.name ?? `Store #${id}`
  const filtered = Boolean(status || rating || storeId)

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the reviews" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon="⭐"
        title={filtered ? 'No reviews match these filters' : 'No reviews yet'}
        action={filtered ? <ButtonLink to="/admin/reviews">Clear the filters</ButtonLink> : undefined}
        className="rounded-2xl bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['review', 'reviews']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <ul aria-label="Reviews" className={cn('space-y-3', isPlaceholderData && 'opacity-60')}>
          {data.content.map((review) => (
            <li key={review.id} className={cn('rounded-2xl border bg-card p-5', review.status === 'HIDDEN' ? 'border-hot/40' : 'border-line')}>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <span className="font-heading font-bold text-heading">{productNames.get(review.productId) ?? `Product #${review.productId}`}</span>
                <StarRating value={review.rating} />
                <span className="text-sm">{review.rating} of 5</span>
                {review.status === 'HIDDEN' && <Badge tone="hot">Hidden</Badge>}
                <span className="ml-auto">
                  <ModerationButton review={review} />
                </span>
              </div>
              {review.comment && <p className="mt-2 text-heading">{review.comment}</p>}
              {review.hiddenReason && <p className="mt-2 text-sm text-hot">Hidden because: “{review.hiddenReason}”</p>}
              <p className="mt-2 text-xs">
                {review.reviewerName} · {formatDate(review.createdAt)} ·{' '}
                <Link to={`/admin/orders/${review.orderId}`} className="hover:text-brand-text hover:underline">
                  order #{review.orderId}
                </Link>{' '}
                ·{' '}
                <Link to={`/admin/stores/${review.storeId}`} className="hover:text-brand-text hover:underline">
                  {storeName(review.storeId)}
                </Link>
              </p>
            </li>
          ))}
        </ul>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['review', 'reviews']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  const chip = (active: boolean) =>
    cn('rounded-full border px-3 py-1 text-sm font-bold', active ? 'border-brand bg-brand text-white' : 'border-line bg-card text-heading hover:border-brand hover:text-brand-text')

  return (
    <>
      <PageTitle title="Reviews" />
      <h1 className="mb-6 text-3xl">Reviews</h1>
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <FilterSelect label="Review status" allLabel="Visible and hidden" value={status ?? ''} options={[{ value: 'VISIBLE', label: 'Visible' }, { value: 'HIDDEN', label: 'Hidden' }]} onChange={(value) => setFilter('status', value)} />
        <FilterSelect label="Store" allLabel="All stores" value={storeId ? String(storeId) : ''} options={stores.map((store) => ({ value: String(store.id), label: store.name }))} onChange={(value) => setFilter('store', value)} />
        <nav aria-label="Stars" className="flex flex-wrap gap-2">
          <Link to={linkWith('rating', '')} aria-current={rating === undefined ? 'page' : undefined} className={chip(rating === undefined)}>
            All
          </Link>
          {RATINGS.map((stars) => (
            <Link key={stars} to={linkWith('rating', String(stars))} aria-current={rating === stars ? 'page' : undefined} aria-label={`${stars} stars`} className={chip(rating === stars)}>
              {stars}★
            </Link>
          ))}
        </nav>
      </div>
      {content}
    </>
  )
}

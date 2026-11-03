import { Link, useSearchParams } from 'react-router'
import { PageTitle } from '../../../components/layout/PageTitle'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { TablePagination } from '../../../components/ui/TablePagination'
import { useUrlFilters } from '../../../hooks/useUrlFilters'
import { LoadingState } from '../../../components/ui/LoadingState'
import { StarRating } from '../../../components/ui/StarRating'
import { cn } from '../../../utils/cn'
import { formatDate } from '../../../utils/format'
import { useMyProductNames, useMyReviews } from '../reports/useVendorReports'

const RATINGS = [5, 4, 3, 2, 1]

/** "/vendor/reviews?rating=1&page=2" (all ratings and page 1 are left out) */
function reviewsLink(rating: number | undefined, page = 1): string {
  const params = new URLSearchParams()
  if (rating) params.set('rating', String(rating))
  if (page > 1) params.set('page', String(page))
  const query = params.toString()
  return query ? `/vendor/reviews?${query}` : '/vendor/reviews'
}

/** What customers say about the store's products, newest first; one star rating at a time to find the unhappy ones. */
export function VendorReviewsPage() {
  const [searchParams] = useSearchParams()
  const ratingParam = Number(searchParams.get('rating'))
  const rating = RATINGS.includes(ratingParam) ? ratingParam : undefined
  const { page, pageSize, goToPage, setPageSize } = useUrlFilters()
  const { data, isPending, isError, error, refetch, isPlaceholderData } = useMyReviews(rating, page, pageSize)
  const productNames = useMyProductNames([...new Set(data?.content.map((review) => review.productId) ?? [])])

  let content
  if (isPending) {
    content = <LoadingState className="min-h-64" />
  } else if (isError) {
    content = <ErrorState title="Could not load the reviews" error={error} onRetry={() => void refetch()} />
  } else if (data.content.length === 0) {
    content = (
      <EmptyState
        icon="⭐"
        title={rating ? `No ${rating}-star reviews` : 'No reviews yet'}
        message={rating ? undefined : 'Customers can review the products of a delivered order.'}
        className="rounded-2xl bg-card"
      />
    )
  } else {
    content = (
      <>
        <TablePagination position="top" list={data} page={page} pageSize={pageSize} itemWords={['review', 'reviews']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mb-4" />
        <ul aria-label="Reviews" className={cn('space-y-3', isPlaceholderData && 'opacity-60')}>
          {data.content.map((review) => (
            <li key={review.id} className="rounded-2xl border border-line bg-card p-5">
              <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                <Link to={`/vendor/products/${review.productId}`} className="font-heading font-bold text-brand-text hover:underline">
                  {productNames.get(review.productId) ?? `Product #${review.productId}`}
                </Link>
                <StarRating value={review.rating} />
                <span className="text-sm">{review.rating} of 5</span>
              </div>
              <p className="mt-2 text-heading">{review.comment ?? <span className="text-body italic">No comment</span>}</p>
              <p className="mt-2 text-xs">
                {review.reviewerName} · {formatDate(review.createdAt)} · order #{review.orderId}
              </p>
            </li>
          ))}
        </ul>
        <TablePagination position="bottom" list={data} page={page} pageSize={pageSize} itemWords={['review', 'reviews']} onPageChange={goToPage} onPageSizeChange={setPageSize} className="mt-6" />
      </>
    )
  }

  const chip = (active: boolean) =>
    cn('inline-block rounded-full border px-4 py-1.5 text-sm font-bold', active ? 'border-brand bg-brand text-white' : 'border-line bg-card text-heading hover:border-brand hover:text-brand-text')

  return (
    <>
      <PageTitle title="Reviews" />
      <h1 className="mb-6 text-3xl">Reviews</h1>
      <nav aria-label="Filter by stars" className="mb-6">
        <ul className="flex flex-wrap gap-2">
          <li>
            <Link to={reviewsLink(undefined)} aria-current={rating === undefined ? 'page' : undefined} className={chip(rating === undefined)}>
              All
            </Link>
          </li>
          {RATINGS.map((stars) => (
            <li key={stars}>
              <Link to={reviewsLink(stars)} aria-current={rating === stars ? 'page' : undefined} aria-label={`${stars} stars`} className={chip(rating === stars)}>
                {stars} ★
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      {content}
    </>
  )
}

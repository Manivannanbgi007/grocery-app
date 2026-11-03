import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { queryKeys } from '../../api/queryKeys'
import { getProductReviews } from '../../api/reviews'
import { ErrorState } from '../../components/ui/ErrorState'
import { Pagination } from '../../components/ui/Pagination'
import { Skeleton } from '../../components/ui/Skeleton'
import { StarRating } from '../../components/ui/StarRating'
import { formatDate } from '../../utils/format'

const REVIEWS_PER_PAGE = 5

/** The customers' reviews of a product, newest first, 5 per page. */
export function ReviewList({ productId }: { productId: number }) {
  const [page, setPage] = useState(1)
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: queryKeys.reviews(productId, page),
    queryFn: ({ signal }) => getProductReviews(productId, page - 1, REVIEWS_PER_PAGE, signal),
    placeholderData: keepPreviousData,
  })

  if (isPending) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-20" />
        <Skeleton className="h-20" />
      </div>
    )
  }
  if (isError) return <ErrorState compact title="Could not load the reviews" error={error} onRetry={() => void refetch()} />

  if (data.totalElements === 0) {
    return <p>No reviews yet. Customers can review a product after it has been delivered to them.</p>
  }

  return (
    <>
      <ul className="divide-y divide-line">
        {data.content.map((review) => (
          <li key={review.id} className="flex gap-4 py-5">
            {/* The reviewer's initial in a circle instead of a photo */}
            <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-full bg-brand-light font-heading text-lg font-bold text-brand-text">
              {review.reviewerName.charAt(0)}
            </span>
            <div>
              <p className="font-heading font-bold text-heading">{review.reviewerName}</p>
              <div className="flex flex-wrap items-center gap-x-3 text-sm">
                <StarRating value={review.rating} />
                <time dateTime={review.createdAt}>{formatDate(review.createdAt)}</time>
              </div>
              {review.comment && <p className="mt-2 text-heading">{review.comment}</p>}
            </div>
          </li>
        ))}
      </ul>
      <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} className="mt-4" />
    </>
  )
}

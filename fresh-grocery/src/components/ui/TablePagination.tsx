import { useId } from 'react'
import type { PageResponse } from '../../api/types'
import { cn } from '../../utils/cn'
import { PAGE_SIZES, rowRange } from '../../utils/pagination'
import { Pagination } from './Pagination'
import { Select } from './Select'

interface TablePaginationProps {
  /** above or below the list: the two control rows get different names for screen readers */
  position: 'top' | 'bottom'
  /** the page the API answered (only its totals are used) */
  list: Pick<PageResponse<unknown>, 'totalElements' | 'totalPages'>
  /** 1-based */
  page: number
  pageSize: number
  /** what the rows are, one and many: ['order', 'orders'] */
  itemWords: [string, string]
  onPageChange: (page: number) => void
  /** without it there is no "Rows per page" choice */
  onPageSizeChange?: (size: number) => void
  className?: string
}

/**
 * The page controls of a list, shown above AND below it: "Showing 21–40 of 132 orders", rows per page and the page
 * buttons. Both rows read the same page and size (from the URL), so they always agree.
 * Nothing for an empty list (its empty state says enough); below a list that fits on one page, nothing either.
 */
export function TablePagination({ position, list, page, pageSize, itemWords, onPageChange, onPageSizeChange, className }: TablePaginationProps) {
  const sizeId = useId()
  const { totalElements, totalPages } = list
  if (totalElements === 0) return null
  const onePage = totalPages <= 1
  // A size choice only helps when the list is longer than the smallest page
  const canChooseSize = onPageSizeChange !== undefined && totalElements > PAGE_SIZES[0]
  if (position === 'bottom' && onePage && !canChooseSize) return null

  const { first, last } = rowRange(page, pageSize, totalElements)
  const words = totalElements === 1 ? itemWords[0] : itemWords[1]
  // A list with its own default size (e.g. 30) offers that one too
  const sizes = PAGE_SIZES.includes(pageSize) ? PAGE_SIZES : [...PAGE_SIZES, pageSize].sort((a, b) => a - b)

  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-x-6 gap-y-3 text-sm', className)}>
      <p className="tabular-nums">
        {onePage ? (
          <>
            {totalElements} {words}
          </>
        ) : (
          <>
            Showing <strong className="text-heading">{first}–{last}</strong> of <strong className="text-heading">{totalElements}</strong> {words}
          </>
        )}
      </p>
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
        {canChooseSize && (
          <div className="flex items-center gap-2">
            <span id={sizeId}>Rows per page</span>
            <Select
              size="sm"
              labelledBy={sizeId}
              value={String(pageSize)}
              onChange={(size) => onPageSizeChange(Number(size))}
              options={sizes.map((size) => ({ value: String(size), label: String(size) }))}
              fitOptions
            />
          </div>
        )}
        <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} label={position === 'top' ? 'Pages, above the list' : 'Pages, below the list'} />
      </div>
    </div>
  )
}

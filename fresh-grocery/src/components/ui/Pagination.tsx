import { ArrowLeft, ArrowRight } from 'lucide-react'
import { cn } from '../../utils/cn'
import { pageItems } from '../../utils/pagination'

interface PaginationProps {
  /** 1-based, like the numbers on the buttons */
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  /** the name of the navigation for screen readers; two bars on one page need two names */
  label?: string
  className?: string
}

/** ( ← ) ( 1 ) ( 2 ) ( 3 ) ... ( 9 ) ( → ) : hidden when there is only one page. */
export function Pagination({ page, totalPages, onPageChange, label = 'Pagination', className }: PaginationProps) {
  if (totalPages <= 1) return null

  const circle = 'flex size-10 items-center justify-center rounded-full font-heading font-bold transition duration-200 active:scale-95'
  const idle =
    'bg-line/60 text-heading hover:-translate-y-0.5 hover:bg-brand hover:text-white disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:translate-y-0 disabled:hover:bg-line/60 disabled:hover:text-heading'

  return (
    <nav aria-label={label} className={className}>
      <ul className="flex flex-wrap items-center gap-2">
        <li>
          <button type="button" className={cn(circle, idle)} onClick={() => onPageChange(page - 1)} disabled={page <= 1} aria-label="Previous page">
            <ArrowLeft className="size-4" aria-hidden="true" />
          </button>
        </li>
        {pageItems(page, totalPages).map((item, index) =>
          item === 'gap' ? (
            <li key={`gap-${index}`} className="px-1 text-heading" aria-hidden="true">
              …
            </li>
          ) : (
            <li key={item}>
              <button
                type="button"
                // The current page glows like the menu's current page
                className={cn(circle, item === page ? 'bg-brand text-white shadow-md shadow-brand/30' : idle)}
                onClick={() => onPageChange(item)}
                aria-label={`Page ${item}`}
                aria-current={item === page ? 'page' : undefined}
              >
                {item}
              </button>
            </li>
          ),
        )}
        <li>
          <button type="button" className={cn(circle, idle)} onClick={() => onPageChange(page + 1)} disabled={page >= totalPages} aria-label="Next page">
            <ArrowRight className="size-4" aria-hidden="true" />
          </button>
        </li>
      </ul>
    </nav>
  )
}

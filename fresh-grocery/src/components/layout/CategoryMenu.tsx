import { ChevronDown, LayoutGrid } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useCategories } from '../../hooks/useCategories'
import { useCategoryCounts } from '../../hooks/useCategoryCounts'
import { useDismiss } from '../../hooks/useDismiss'
import { cn } from '../../utils/cn'
import { categoryEmoji } from '../../utils/emoji'
import { shopLink } from '../../utils/links'
import { ErrorState } from '../ui/ErrorState'
import { Skeleton } from '../ui/Skeleton'

/**
 * The green "Browse All Categories" button and its drop-down list of categories with product counts.
 * It closes on Escape, on a click outside, and when a category is picked.
 */
export function CategoryMenu() {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()

  const { data: categories = [], isPending, isError, error, refetch } = useCategories()
  // The counts are only loaded once the menu has been opened
  const counts = useCategoryCounts(categories, open)

  useDismiss(containerRef, open, (reason) => {
    setOpen(false)
    if (reason === 'escape') buttonRef.current?.focus()
  })

  return (
    // shrink-0: the main menu next to it must not squeeze the button onto two lines
    <div ref={containerRef} className="relative shrink-0">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((isOpen) => !isOpen)}
        aria-expanded={open}
        aria-controls={panelId}
        className="flex h-12 items-center gap-2 rounded-md bg-brand px-5 font-heading font-bold text-white hover:bg-brand-dark"
      >
        <LayoutGrid className="size-5" aria-hidden="true" />
        Browse All Categories
        <ChevronDown className={cn('size-4 transition-transform', open && 'rotate-180')} aria-hidden="true" />
      </button>

      {open && (
        <div id={panelId} className="absolute top-full left-0 z-30 mt-3 w-[36rem] max-w-[90vw] rounded-xl border border-brand-soft bg-card p-5 shadow-lg">
          {isPending && (
            <div className="grid grid-cols-2 gap-3">
              {Array.from({ length: 8 }, (_, index) => (
                <Skeleton key={index} className="h-12" />
              ))}
            </div>
          )}

          {isError && <ErrorState compact title="Could not load the categories" error={error} onRetry={() => void refetch()} />}

          {categories.length > 0 && (
            <ul className="grid grid-cols-2 gap-3">
              {categories.map((category) => {
                const count = counts.get(category.id)
                return (
                  <li key={category.id}>
                    <Link
                      to={shopLink({ category: category.id })}
                      onClick={() => setOpen(false)}
                      aria-label={count === undefined ? category.name : `${category.name}, ${count} products`}
                      className="flex items-center gap-3 rounded-md border border-line px-3 py-2.5 transition hover:border-brand-soft hover:shadow-sm"
                    >
                      <span className="text-2xl" aria-hidden="true">
                        {categoryEmoji(category.slug)}
                      </span>
                      <span className="flex-1 font-heading text-sm font-bold text-heading">{category.name}</span>
                      {count !== undefined && (
                        <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-brand px-1.5 text-[0.65rem] font-bold text-white">{count}</span>
                      )}
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

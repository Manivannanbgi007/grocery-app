import type { Category } from '../../api/types'
import { cn } from '../../utils/cn'

interface CategoryTabsProps {
  categories: Category[]
  /** the chosen category; undefined = "All" */
  selectedId: number | undefined
  onSelect: (categoryId: number | undefined) => void
  /** shows an "All" button first */
  showAll?: boolean
  /** for screen readers: what the buttons filter */
  label: string
}

/**
 * "All · Vegetables · Fruits ..." buttons above a product section, as round pills. The chosen one is filled green
 * (aria-pressed). On a narrow screen the pills wrap onto a second line, so every category stays in view.
 */
export function CategoryTabs({ categories, selectedId, onSelect, showAll = false, label }: CategoryTabsProps) {
  const options: Array<{ id: number | undefined; name: string }> = [...(showAll ? [{ id: undefined, name: 'All' }] : []), ...categories]

  return (
    // A little smaller on phones, so the shop's six fit on two lines
    <div role="group" aria-label={label} className="flex flex-wrap gap-1.5 sm:gap-2">
      {options.map((option) => {
        const selected = option.id === selectedId
        return (
          <button
            key={option.id ?? 'all'}
            type="button"
            onClick={() => onSelect(option.id)}
            aria-pressed={selected}
            className={cn(
              'rounded-full border px-3 py-1 font-heading text-[0.8125rem] font-bold whitespace-nowrap transition-colors sm:px-3.5 sm:py-1.5 sm:text-sm',
              selected ? 'border-brand bg-brand text-white shadow-md shadow-brand/30' : 'border-line bg-card text-heading hover:border-brand hover:text-brand-text',
            )}
          >
            {option.name}
          </button>
        )
      })}
    </div>
  )
}

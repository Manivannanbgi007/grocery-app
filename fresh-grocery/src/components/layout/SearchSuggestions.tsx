import { Search } from 'lucide-react'
import type { Product } from '../../api/types'
import { cn } from '../../utils/cn'
import { formatPrice, formatUnit } from '../../utils/format'
import { ProductImage } from '../product/ProductImage'
import { Spinner } from '../ui/Spinner'

interface SearchSuggestionsProps {
  /** the list's id: the search box points at it (aria-controls); option 2's id is "<id>-2" (aria-activedescendant) */
  id: string
  /** what is typed in the box */
  text: string
  products: Product[]
  /** the products are the answer for `text` (false while the typing has not paused or the answer is loading) */
  upToDate: boolean
  /** the request failed: only "Search for …" is offered (the full search still works) */
  failed: boolean
  /** the option chosen with the arrow keys or the mouse: a product's index, products.length for "Search for …", -1 none */
  active: number
  onChoose: (index: number) => void
  onActivate: (index: number) => void
}

/** The name with the typed text in bold when the name contains it ("toma" in "Cherry Tomato"). */
function Highlighted({ name, typed }: { name: string; typed: string }) {
  const start = typed ? name.toLowerCase().indexOf(typed.toLowerCase()) : -1
  if (start < 0) return <>{name}</>
  const end = start + typed.length
  return (
    <>
      {name.slice(0, start)}
      <strong className="font-bold">{name.slice(start, end)}</strong>
      {name.slice(end)}
    </>
  )
}

/**
 * The dropdown under the search box, like Google's: the suggested products (photo, name, category, unit, price) and
 * last "Search for …", the full search. It is a listbox of the combobox pattern: the focus stays in the search box,
 * which says which option is active (aria-activedescendant).
 */
export function SearchSuggestions({ id, text, products, upToDate, failed, active, onChoose, onActivate }: SearchSuggestionsProps) {
  const typed = text.trim()
  const searchIndex = products.length

  const optionClasses = (index: number) => cn('flex cursor-pointer items-center gap-3 px-4 py-2 transition-colors', active === index && 'bg-brand-light')

  return (
    // preventDefault on mousedown: a click on an option must not take the focus away from the search box (that would close the list first)
    // z-50 like the other header pop-ups (the notifications, the select menus): above the floating buttons
    <div className="absolute inset-x-0 top-full z-50 mt-2 overflow-hidden rounded-xl border border-line bg-card shadow-xl" onMouseDown={(event) => event.preventDefault()}>
      {products.length === 0 && !failed && (
        <p className="flex items-center gap-2 px-4 pt-3 pb-1 text-sm">
          {upToDate ? (
            <>No products match “{typed}”</>
          ) : (
            <>
              <Spinner size="sm" className="text-brand" />
              Looking for products…
            </>
          )}
        </p>
      )}

      <ul id={id} role="listbox" aria-label="Search suggestions" className={cn('max-h-[70vh] overflow-y-auto py-2', !upToDate && products.length > 0 && 'opacity-70')}>
        {products.map((product, index) => (
          <li
            key={product.id}
            id={`${id}-${index}`}
            role="option"
            aria-selected={active === index}
            onClick={() => onChoose(index)}
            onMouseMove={() => onActivate(index)}
            className={optionClasses(index)}
          >
            <ProductImage product={product} className="size-10 shrink-0" emojiClassName="text-xl" />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm text-heading">
                <Highlighted name={product.name} typed={typed} />
              </span>
              <span className="block truncate text-xs text-body">
                {product.categoryName} · {formatUnit(product.unit, product.unitQuantity)}
                {!product.inStock && <span className="font-bold text-hot"> · Out of stock</span>}
              </span>
            </span>
            <span className="shrink-0 font-heading text-sm font-bold text-heading">{formatPrice(product.sellingPrice)}</span>
          </li>
        ))}

        <li
          id={`${id}-${searchIndex}`}
          role="option"
          aria-selected={active === searchIndex}
          onClick={() => onChoose(searchIndex)}
          onMouseMove={() => onActivate(searchIndex)}
          className={cn(optionClasses(searchIndex), products.length > 0 && 'mt-1 border-t border-line pt-3')}
        >
          <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-surface text-brand-text" aria-hidden="true">
            <Search className="size-4" />
          </span>
          <span className="min-w-0 flex-1 truncate text-sm text-heading">
            Search for “<strong>{typed}</strong>”
          </span>
        </li>
      </ul>
    </div>
  )
}

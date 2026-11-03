import { Search } from 'lucide-react'
import { useId, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { useCategories } from '../../hooks/useCategories'
import { SUGGESTIONS_MIN_LENGTH, useProductSuggestions } from '../../hooks/useProductSuggestions'
import { shopLink } from '../../utils/links'
import { Select } from '../ui/Select'
import { SearchSuggestions } from './SearchSuggestions'

/**
 * Category select + search box + Search button. Submitting opens the shop with those filters.
 * On the shop page it starts with the shop's current filters (the Header re-creates it when they change).
 *
 * While the customer types (2 characters or more), a dropdown suggests products (SearchSuggestions); ↓/↑ choose one,
 * Enter opens it (or searches when none is chosen), Escape, Tab or a click elsewhere closes the dropdown.
 */
export function SearchBar() {
  const id = useId()
  const listId = `${id}-suggestions`
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { data: categories = [] } = useCategories()

  const [text, setText] = useState(searchParams.get('q') ?? '')
  const [categoryId, setCategoryId] = useState(searchParams.get('category') ?? '')
  /** the customer is typing in the box: the dropdown may show */
  const [open, setOpen] = useState(false)
  /** the chosen option: a product's index, products.length for "Search for …", -1 for none */
  const [active, setActive] = useState(-1)

  const suggestions = useProductSuggestions(text, categoryId ? Number(categoryId) : undefined)
  const { products } = suggestions
  const showList = open && text.trim().length >= SUGGESTIONS_MIN_LENGTH
  const optionCount = products.length + 1 // the products and "Search for …"
  // The list can get shorter while an option is chosen: then none is
  const activeIndex = showList && active < optionCount ? active : -1

  /** Closes the list; it opens again with nothing chosen. */
  function close() {
    setOpen(false)
    setActive(-1)
  }

  function search() {
    close()
    navigate(shopLink({ q: text, category: categoryId ? Number(categoryId) : undefined }))
  }

  function choose(index: number) {
    if (index < products.length) {
      close()
      navigate(`/products/${products[index].id}`)
    } else {
      search()
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (activeIndex >= 0) choose(activeIndex)
    else search()
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault() // the cursor stays where it is
      if (!showList) {
        setOpen(true)
        return
      }
      const step = event.key === 'ArrowDown' ? 1 : -1
      // From "none" ↓ goes to the first option and ↑ to the last; past either end it goes round
      setActive(activeIndex === -1 ? (step === 1 ? 0 : optionCount - 1) : (activeIndex + step + optionCount) % optionCount)
    } else if (event.key === 'Escape' && showList) {
      event.preventDefault() // closes the list but keeps the text (a search box would clear it)
      close()
    }
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="relative flex h-12 w-full items-center rounded-lg border-2 border-brand-soft bg-card focus-within:border-brand">
      <Select
        variant="plain"
        aria-label="Category"
        value={categoryId}
        onChange={setCategoryId}
        options={[{ value: '', label: 'All Categories' }, ...categories.map((category) => ({ value: String(category.id), label: category.name }))]}
        className="hidden h-full w-44 shrink-0 border-r border-line py-1 pl-1 sm:block"
      />

      <label htmlFor={`${id}-text`} className="sr-only">
        Search for products
      </label>
      <input
        id={`${id}-text`}
        type="search"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={showList ? listId : undefined}
        // The chosen option, by the id SearchSuggestions gives it
        aria-activedescendant={activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
        // The browser's own list of earlier entries would cover the suggestions
        autoComplete="off"
        value={text}
        onChange={(event) => {
          setText(event.target.value)
          setOpen(true)
          setActive(-1)
        }}
        onFocus={() => setOpen(true)}
        // Tab, a click elsewhere or on the category: the list closes (a click on an option keeps the focus here)
        onBlur={close}
        onKeyDown={handleKeyDown}
        placeholder="Search for products..."
        className="h-full min-w-0 flex-1 bg-transparent px-4 text-sm text-heading outline-none placeholder:text-muted"
      />

      <button type="submit" className="mr-1 flex h-9 items-center gap-2 rounded-md bg-brand px-4 font-heading text-sm font-bold text-white hover:bg-brand-dark">
        <Search className="size-4" aria-hidden="true" />
        <span className="sr-only sm:not-sr-only">Search</span>
      </button>

      {showList && (
        <SearchSuggestions
          id={listId}
          text={text}
          products={products}
          upToDate={suggestions.upToDate}
          failed={suggestions.failed}
          active={activeIndex}
          onChoose={choose}
          onActivate={setActive}
        />
      )}
      {/* For screen readers: how many suggestions there are (the list itself is not read out while typing). A live
          region without role="status", so it is not taken for the page's own status messages */}
      <p aria-live="polite" className="sr-only">
        {showList && suggestions.upToDate ? `${products.length} ${products.length === 1 ? 'suggestion' : 'suggestions'}` : ''}
      </p>
    </form>
  )
}

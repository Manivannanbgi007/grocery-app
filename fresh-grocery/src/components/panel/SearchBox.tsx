import { Search } from 'lucide-react'
import { useId, type FormEvent } from 'react'

interface SearchBoxProps {
  /** for screen readers, e.g. "Search stores" */
  label: string
  placeholder: string
  /** the search in the URL now */
  value: string
  onSearch: (value: string) => void
}

/** A search field with a button; the search runs on Enter or the button (not on every key). */
export function SearchBox({ label, placeholder, value, onSearch }: SearchBoxProps) {
  const inputId = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSearch(String(new FormData(event.currentTarget).get('q') ?? '').trim())
  }

  return (
    // A new key empties the box when the search is cleared elsewhere (e.g. "Clear the filters")
    <form key={value} onSubmit={handleSubmit} role="search" className="flex">
      <label htmlFor={inputId} className="sr-only">
        {label}
      </label>
      {/* The same focus ring as the form fields (TextField) */}
      <input
        id={inputId}
        type="search"
        name="q"
        defaultValue={value}
        placeholder={placeholder}
        className="h-10 w-56 rounded-l-xl border border-line bg-card px-3 text-sm text-heading transition-shadow outline-none focus:border-brand focus:ring-4 focus:ring-brand/15"
      />
      <button type="submit" className="flex h-10 items-center rounded-r-xl bg-brand px-3 text-white shadow-md shadow-brand/25 transition hover:bg-brand-dark active:scale-95" aria-label="Search">
        <Search className="size-4" aria-hidden="true" />
      </button>
    </form>
  )
}

import { useId, useState, type FormEvent } from 'react'
import { Button } from '../../../components/ui/Button'
import { useShopFilters } from '../useShopFilters'
import { SidebarWidget } from './SidebarWidget'

/** Search within the current filters (the header search starts a new search instead). */
export function SearchWidget() {
  const { filters, apply } = useShopFilters()
  const [text, setText] = useState(filters.q ?? '')
  const inputId = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    apply({ q: text.trim() || undefined })
  }

  return (
    <SidebarWidget title="Search" plain>
      <form role="search" aria-label="Search in the shop" onSubmit={handleSubmit} className="flex gap-2">
        <label htmlFor={inputId} className="sr-only">
          Search in the shop
        </label>
        <input
          id={inputId}
          type="search"
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="Search for items..."
          className="h-10 min-w-0 flex-1 rounded-lg border border-line bg-card px-3 text-sm text-heading outline-none focus:border-brand"
        />
        <Button type="submit" className="rounded-lg px-4">
          Search
        </Button>
      </form>
    </SidebarWidget>
  )
}

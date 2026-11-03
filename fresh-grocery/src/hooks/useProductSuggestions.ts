import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { suggestProducts } from '../api/products'
import { queryKeys } from '../api/queryKeys'
import { useDebouncedValue } from './useDebouncedValue'

/** Suggestions start at this many characters (one letter would match almost everything). */
export const SUGGESTIONS_MIN_LENGTH = 2
/** How long the typing must pause before the suggestions are asked for. */
export const SUGGESTIONS_DELAY_MS = 250
/** How many products the dropdown shows. */
export const SUGGESTIONS_SHOWN = 6

/**
 * The products to suggest for what the customer is typing into the search box.
 *
 * - One request per pause in the typing (debounce), not one per key press.
 * - Answers are cached per text and category (React Query's default of one minute): typing back to an earlier text
 *   shows its suggestions at once. A request that is overtaken is cancelled through its signal.
 * - keepPreviousData: the last list stays on screen while the next one loads, so the dropdown does not flicker.
 */
export function useProductSuggestions(text: string, categoryId: number | undefined) {
  const wanted = text.trim()
  const typed = useDebouncedValue(wanted, SUGGESTIONS_DELAY_MS)
  const enabled = typed.length >= SUGGESTIONS_MIN_LENGTH

  const query = useQuery({
    queryKey: queryKeys.productSuggestions(typed, categoryId),
    queryFn: ({ signal }) => suggestProducts(typed, categoryId, SUGGESTIONS_SHOWN, signal),
    enabled,
    placeholderData: keepPreviousData,
    retry: false,
    // The dropdown is a hint, not a page: no app-wide loading sign for it
    meta: { quiet: true },
  })

  return {
    /** the products to show: for the text in the box, or for the previous text while the next answer loads */
    products: enabled ? (query.data ?? []) : [],
    /** the products are the answer for the text in the box now (the typing has paused and the answer is in) */
    upToDate: enabled && typed === wanted && !query.isPending && !query.isPlaceholderData,
    failed: query.isError,
  }
}

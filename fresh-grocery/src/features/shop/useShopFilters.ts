import { useNavigate, useSearchParams } from 'react-router'
import { shopLink } from '../../utils/links'
import { parseShopFilters, type ShopFilters } from './shopFilters'

/**
 * The shop filters of the current URL, and two ways to change them:
 * - linkWith(changes): the shop address with some filters changed, for <Link to=...>
 * - apply(changes): goes there straight away (forms, the sort select, checkboxes)
 * Every change except a page change starts again at page 1.
 *
 * The sidebar widgets use this on the cart page too: there the URL has no filters, so their links start a fresh search.
 */
export function useShopFilters() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const filters = parseShopFilters(searchParams)

  function linkWith(changes: Partial<ShopFilters>): string {
    return shopLink({ ...filters, page: 1, ...changes })
  }

  /** keepScroll: stay where you are on the page (filters); a new page of results scrolls back to the top */
  function apply(changes: Partial<ShopFilters>, keepScroll = true) {
    navigate(linkWith(changes), { preventScrollReset: keepScroll })
  }

  return { filters, linkWith, apply }
}

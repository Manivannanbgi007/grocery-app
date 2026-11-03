import { useLocation, useSearchParams } from 'react-router'
import { PAGE_SIZES } from '../utils/pagination'

/**
 * A list's filters in the URL (?status=ACTIVE&q=milk&page=2&size=50), so links, refresh and "Back" show the same list.
 * Changing a filter goes back to page 1 (the page size stays); empty values, page 1 and the list's own page size are
 * left out of the URL.
 *
 * @param defaultPageSize the list's rows per page when the URL does not choose one of PAGE_SIZES
 */
export function useUrlFilters(defaultPageSize = 20) {
  const [searchParams, setSearchParams] = useSearchParams()
  const { pathname } = useLocation()
  /** 1-based */
  const page = Math.max(1, Number.parseInt(searchParams.get('page') ?? '1', 10) || 1)
  const sizeInUrl = Number(searchParams.get('size'))
  /** Rows per page: ?size= when it is one of PAGE_SIZES, else the list's default. */
  const pageSize = PAGE_SIZES.includes(sizeInUrl) ? sizeInUrl : defaultPageSize

  /** One filter, '' when it is not set. */
  function filter(name: string): string {
    return searchParams.get(name)?.trim() ?? ''
  }

  /** A filter that is a positive whole number (an id), or undefined. */
  function idFilter(name: string): number | undefined {
    const value = Number(searchParams.get(name))
    return Number.isInteger(value) && value > 0 ? value : undefined
  }

  /** The filters with one of them changed, back on page 1. */
  function changed(name: string, value: string): URLSearchParams {
    const next = new URLSearchParams(searchParams)
    if (value) next.set(name, value)
    else next.delete(name)
    next.delete('page')
    return next
  }

  function setFilter(name: string, value: string) {
    setSearchParams(changed(name, value))
  }

  /** Several filters at once, e.g. the first and the last day of a period. */
  function setFilters(values: Record<string, string>) {
    const next = new URLSearchParams(searchParams)
    for (const [name, value] of Object.entries(values)) {
      if (value) next.set(name, value)
      else next.delete(name)
    }
    next.delete('page')
    setSearchParams(next)
  }

  /** The address of this list with one filter changed, e.g. for the tab links: "/admin/stores?tab=active&q=fresh". */
  function linkWith(name: string, value: string): string {
    const query = changed(name, value).toString()
    return query ? `${pathname}?${query}` : pathname
  }

  function goToPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    if (nextPage > 1) next.set('page', String(nextPage))
    else next.delete('page')
    setSearchParams(next)
  }

  /** Another number of rows per page, back on page 1 (the rows of the old page would land somewhere else). */
  function setPageSize(size: number) {
    const next = new URLSearchParams(searchParams)
    if (size !== defaultPageSize) next.set('size', String(size))
    else next.delete('size')
    next.delete('page')
    setSearchParams(next)
  }

  return { searchParams, page, pageSize, filter, idFilter, setFilter, setFilters, linkWith, goToPage, setPageSize }
}

/** One button of the pagination bar: a page number, or "..." for skipped pages. */
export type PageItem = number | 'gap'

/** The "rows per page" choices of the page controls above and below a list (TablePagination). */
export const PAGE_SIZES = [10, 20, 50]

/** One page of a list that is already in the browser, with the same totals as the API's pages. */
export interface ListPage<T> {
  rows: T[]
  /** 1-based; the last page when a higher one was asked for */
  page: number
  totalElements: number
  totalPages: number
}

/**
 * One page of a list the browser already has in full (e.g. the admin's categories), shaped like the API's pages so
 * TablePagination works the same. A page after the last one (the list got shorter) shows the last page.
 *   pageOfList(['a', 'b', 'c'], 2, 2) -> { rows: ['c'], page: 2, totalElements: 3, totalPages: 2 }
 */
export function pageOfList<T>(items: T[], page: number, pageSize: number): ListPage<T> {
  const totalElements = items.length
  const totalPages = Math.ceil(totalElements / pageSize)
  const shownPage = Math.min(Math.max(page, 1), Math.max(totalPages, 1))
  return { rows: items.slice((shownPage - 1) * pageSize, shownPage * pageSize), page: shownPage, totalElements, totalPages }
}

/** "Showing 21–40 of 132": the first and the last row of a page (1-based), for a page that is not empty. */
export function rowRange(page: number, pageSize: number, totalElements: number): { first: number; last: number } {
  const first = (page - 1) * pageSize + 1
  return { first, last: Math.min(page * pageSize, totalElements) }
}

/**
 * The page numbers to show (1-based): always the first and last page and the pages next to the current one.
 *   pageItems(1, 3)  -> [1, 2, 3]
 *   pageItems(6, 12) -> [1, 'gap', 5, 6, 7, 'gap', 12]
 */
export function pageItems(currentPage: number, totalPages: number): PageItem[] {
  const pages: number[] = []
  for (let page = 1; page <= totalPages; page++) {
    if (page === 1 || page === totalPages || Math.abs(page - currentPage) <= 1) pages.push(page)
  }

  const items: PageItem[] = []
  pages.forEach((page, index) => {
    const previous = pages[index - 1]
    if (previous !== undefined && page - previous === 2) items.push(previous + 1) // a gap of one page: just show it
    else if (previous !== undefined && page - previous > 2) items.push('gap')
    items.push(page)
  })
  return items
}

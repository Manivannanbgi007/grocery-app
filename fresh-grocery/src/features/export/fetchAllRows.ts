import { ApiError } from '../../api/client'
import type { PageResponse } from '../../api/types'
import { EXPORT_CONCURRENCY, EXPORT_PAGE_SIZE, EXPORT_RETRIES } from './exportLimits'
import type { ExportSpec } from './exportTypes'

export interface AllRows<T> {
  rows: T[]
  /** rows in the list (with its filters) */
  total: number
  /** true when the list has more rows than `maxRows`: the file holds the first `maxRows` */
  truncated: boolean
}

/** Loaded rows so far and the rows expected, for a progress line ("300 of 1,240"). */
export type ExportProgress = (loaded: number, expected: number) => void

function abortError(): DOMException {
  return new DOMException('The export was cancelled', 'AbortError')
}

/** Waits, but stops at once when the export is cancelled. */
function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) return reject(abortError())
    const timer = setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, ms)
    function onAbort() {
      clearTimeout(timer)
      reject(abortError())
    }
    signal.addEventListener('abort', onAbort, { once: true })
  })
}

/**
 * One page, asked again when the gateway says "too many requests" (429) or a service is busy (503): after 0.5, 1, 2,
 * 4 s (plus a little randomness, so several tabs do not retry in step). Other errors end the export at once.
 */
async function loadPage<T>(load: () => Promise<PageResponse<T>>, signal: AbortSignal): Promise<PageResponse<T>> {
  for (let attempt = 0; ; attempt++) {
    if (signal.aborted) throw abortError()
    try {
      return await load()
    } catch (error) {
      const busy = error instanceof ApiError && (error.status === 429 || error.status === 503)
      if (!busy || attempt >= EXPORT_RETRIES) throw error
      await wait(500 * 2 ** attempt + Math.random() * 250, signal)
    }
  }
}

/**
 * Every row of a list, up to `maxRows`: the first page says how many there are, the others load a few at a time
 * (EXPORT_CONCURRENCY) and are put back in their order. A row that moves to the next page while the pages load (a new
 * row arrived) would be seen twice: with the spec's rowKey it is kept once.
 */
export async function fetchAllRows<T>(spec: ExportSpec<T>, maxRows: number, signal: AbortSignal, onProgress?: ExportProgress): Promise<AllRows<T>> {
  if (spec.rows) {
    return { rows: spec.rows.slice(0, maxRows), total: spec.rows.length, truncated: spec.rows.length > maxRows }
  }

  const fetchPage = spec.fetchPage
  const size = spec.pageSize ?? EXPORT_PAGE_SIZE
  const first = await loadPage(() => fetchPage(0, size, signal), signal)
  const total = first.totalElements
  const expected = Math.min(total, maxRows)
  const pageCount = Math.min(first.totalPages, Math.ceil(maxRows / size))
  const pages: T[][] = [first.content]
  let loaded = first.content.length
  onProgress?.(Math.min(loaded, expected), expected)

  let next = 1
  async function worker() {
    while (next < pageCount) {
      const page = next++
      const response = await loadPage(() => fetchPage(page, size, signal), signal)
      pages[page] = response.content
      loaded += response.content.length
      onProgress?.(Math.min(loaded, expected), expected)
    }
  }
  await Promise.all(Array.from({ length: Math.min(EXPORT_CONCURRENCY, pageCount - 1) }, worker))

  let rows = pages.flat()
  if (spec.rowKey) {
    const seen = new Set<string | number>()
    rows = rows.filter((row) => {
      const key = spec.rowKey!(row)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
  }
  return { rows: rows.slice(0, maxRows), total, truncated: total > maxRows }
}

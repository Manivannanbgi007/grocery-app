import type { ExportFormat } from './exportTypes'

/**
 * The most rows one file holds. A spreadsheet takes 10,000 rows easily (more is a job for a report, not a browser);
 * a PDF is for reading and printing, and 2,000 rows are already about 50 pages. A longer list says so before the
 * export starts (ExportMenu), and the file says how many rows the list really had.
 */
export const MAX_EXPORT_ROWS: Record<ExportFormat, number> = { xlsx: 10_000, csv: 10_000, pdf: 2_000 }

/** The biggest page the services answer (max-page-size: 100 in every service), so a full list needs few requests. */
export const EXPORT_PAGE_SIZE = 100

/**
 * Pages loaded at the same time. The gateway allows 20 requests a second per user (40 in a burst); three at a time
 * stays well below, so the export never slows the rest of the page down or gets the user rate-limited.
 */
export const EXPORT_CONCURRENCY = 3

/** A page that gets 429 (too many requests) or 503 (busy) is asked again this often, waiting longer each time. */
export const EXPORT_RETRIES = 4

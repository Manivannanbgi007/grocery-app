import type { PageResponse } from '../../api/types'

/*
 * Export of a list (Excel, CSV, PDF): what a page declares. A page describes its columns once and how to load its
 * rows; features/export does the rest (all pages of the list, the three file formats, the menu). See ExportMenu.
 */

export type ExportFormat = 'xlsx' | 'csv' | 'pdf'

/**
 * What a column holds, so every format shows it at its best:
 *   text      as it is
 *   number    a number (Excel can sum it)
 *   money     rupees: a number with 2 decimals in Excel and CSV ("Total (₹)"), "₹1,039.20" in the PDF
 *   percent   12.5 means 12.5 %: a number in Excel and CSV ("Commission (%)"), "12.5%" in the PDF
 *   date      a day ("2026-11-09" or an ISO time): a real date in Excel, "9 Nov 2026" in the PDF
 *   datetime  a moment (ISO time), in India time like the app: a real date and time in Excel ("Placed (IST)")
 *   boolean   Yes / No
 */
export type ExportValueType = 'text' | 'number' | 'money' | 'percent' | 'date' | 'datetime' | 'boolean'

/** A cell before formatting: ISO strings for dates are fine (the column's type says what they are). */
export type ExportValue = string | number | boolean | Date | null | undefined

export interface ExportColumn<T> {
  /** the column's name, as in the table ("Total"); money and percent columns get " (₹)" / " (%)" in Excel and CSV */
  header: string
  value: (row: T) => ExportValue
  /** default: text */
  type?: ExportValueType
  /** width in Excel, in characters (default: from the header and the type) */
  width?: number
}

interface ExportSpecBase<T> {
  /** what the list is, e.g. "Orders": the PDF's title and the Excel sheet's name */
  title: string
  /** the start of the file's name: "orders" -> orders-2026-11-09-1530.xlsx */
  fileName: string
  /** what one row is and many rows are, for the messages: ['order', 'orders'] */
  itemWords: [string, string]
  columns: ExportColumn<T>[]
  /** the filters in use, said in the PDF and on the Excel file's "About" sheet: ["Status: Delivered", "Store: Fresh Mart"] */
  filters?: string[]
  /** an id per row: a row seen twice while the pages load (a new row pushed the list down) is kept once */
  rowKey?: (row: T) => string | number
}

/**
 * Where the rows come from: the pages of the SAME list the table shows, with the same filters (`fetchPage`, 0-based,
 * at most `pageSize` rows a page), or the rows themselves for a list that is loaded whole (a report, a short list).
 */
export type ExportSpec<T> = ExportSpecBase<T> &
  (
    | { fetchPage: (page: number, size: number, signal: AbortSignal) => Promise<PageResponse<T>>; pageSize?: number; rows?: never }
    | { rows: T[]; fetchPage?: never; pageSize?: never }
  )

/** Who exported and when, written into the PDF and the Excel file (not into the CSV, which stays plain data). */
export interface ExportMeta {
  exportedBy: string
  exportedAt: Date
  /** rows in the list (with its filters), and whether the file holds fewer (the limit below) */
  total: number
  truncated: boolean
}

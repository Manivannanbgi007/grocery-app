import { dataHeader, plainText } from './exportValues'
import type { ExportColumn } from './exportTypes'

/** The byte order mark: without it Excel reads UTF-8 as an old code page and "₹" or "é" turn into garbage. */
const BOM = '﻿'

/**
 * A text that a spreadsheet would run as a formula when the CSV is opened ("=HYPERLINK(...)", "+1+1", "@SUM(...)"):
 * CSV injection. Such a text gets a leading apostrophe, which makes Excel, LibreOffice and Google Sheets show it as
 * text (OWASP's advice). Only text columns: numbers and dates are written by us and cannot hold one.
 */
const FORMULA_START = /^[=+\-@\t\r]/

function escapeField(text: string): string {
  // RFC 4180: a field with a comma, a quote or a line break goes in quotes, its quotes doubled
  return /[",\r\n]/.test(text) || text !== text.trim() ? `"${text.replace(/"/g, '""')}"` : text
}

/**
 * The rows as CSV (RFC 4180): a header line, then one line per row, comma separated, CRLF line ends, UTF-8 with a
 * byte order mark. Numbers and times are plain data (plainText); the columns' names carry the units.
 */
export function toCsv<T>(columns: ExportColumn<T>[], rows: T[]): string {
  const lines = [columns.map((column) => escapeField(dataHeader(column))).join(',')]
  for (const row of rows) {
    const fields = columns.map((column) => {
      const type = column.type ?? 'text'
      let text = plainText(column.value(row), type)
      if (type === 'text' && FORMULA_START.test(text)) text = `'${text}`
      return escapeField(text)
    })
    lines.push(fields.join(','))
  }
  return BOM + lines.join('\r\n') + '\r\n'
}

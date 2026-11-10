import writeXlsxFile, { type Cell, type Row, type Sheet } from 'write-excel-file/browser'
import { dataHeader, excelDate, numberOf, readableText } from './exportValues'
import type { ExportColumn, ExportMeta, ExportSpec, ExportValueType } from './exportTypes'

/*
 * The Excel file (.xlsx). Loaded only when someone exports to Excel (ExportMenu imports it on demand), so the library
 * is not part of the app's first download.
 */

/** Excel's limit for one cell's text. */
const MAX_CELL_TEXT = 32_767

/** Default widths in characters per type; text columns are measured. */
const TYPE_WIDTHS: Partial<Record<ExportValueType, number>> = { money: 14, number: 10, percent: 10, date: 13, datetime: 19, boolean: 8 }

const HEADER_STYLE = {
  fontWeight: 'bold',
  textColor: '#253D4E',
  backgroundColor: '#E7F9ED',
  bottomBorderStyle: 'thin',
  bottomBorderColor: '#09792F',
  alignVertical: 'center',
  height: 22,
} as const

function dataCell<T>(column: ExportColumn<T>, row: T): Cell {
  const value = column.value(row)
  switch (column.type ?? 'text') {
    case 'money': {
      const number = numberOf(value)
      return number === null ? null : { type: Number, value: number, format: '#,##0.00' }
    }
    case 'number':
    case 'percent': {
      const number = numberOf(value)
      return number === null ? null : { type: Number, value: number }
    }
    case 'date': {
      const date = excelDate(value)
      return date ? { type: Date, value: date, format: 'dd mmm yyyy' } : null
    }
    case 'datetime': {
      const date = excelDate(value)
      return date ? { type: Date, value: date, format: 'dd mmm yyyy hh:mm' } : null
    }
    case 'boolean':
      return { type: String, value: readableText(value, 'boolean') }
    default: {
      if (value === null || value === undefined || value === '') return null
      // A text cell is never run as a formula in .xlsx (unlike CSV), so it needs no apostrophe
      return { type: String, value: String(value).slice(0, MAX_CELL_TEXT) }
    }
  }
}

/** A text column's width: its longest value among the first rows (or its name), between 10 and 50 characters. */
function columnWidth<T>(column: ExportColumn<T>, rows: T[]): number {
  if (column.width) return column.width
  const header = dataHeader(column).length + 2
  const typeWidth = TYPE_WIDTHS[column.type ?? 'text']
  if (typeWidth) return Math.max(typeWidth, header)
  const longest = rows.slice(0, 200).reduce((most, row) => Math.max(most, String(column.value(row) ?? '').length), 0)
  return Math.min(50, Math.max(10, header, longest + 2))
}

/** An Excel sheet's name: at most 31 characters and none of : \ / ? * [ ] */
function sheetName(title: string): string {
  return title.replace(/[:\\/?*[\]]/g, ' ').trim().slice(0, 31) || 'Data'
}

/** The "About" sheet: what the file is, who exported it and when, with which filters, and whether rows are missing. */
function aboutRows<T>(spec: ExportSpec<T>, rows: T[], meta: ExportMeta): Row[] {
  const label = (text: string): Cell => ({ type: String, value: text, fontWeight: 'bold', textColor: '#253D4E' })
  const text = (value: string): Cell => ({ type: String, value, wrap: true })
  const count = meta.truncated
    ? `${rows.length.toLocaleString('en-IN')} of ${meta.total.toLocaleString('en-IN')} ${spec.itemWords[1]}: the first ${rows.length.toLocaleString('en-IN')}. Narrow the filters to export the rest.`
    : `${rows.length.toLocaleString('en-IN')} ${rows.length === 1 ? spec.itemWords[0] : spec.itemWords[1]}`
  return [
    [{ type: String, value: `Fresh Grocery: ${spec.title}`, fontWeight: 'bold', fontSize: 14, textColor: '#09792F' }, null],
    [label('Exported by'), text(meta.exportedBy)],
    [label('Exported at'), text(`${readableText(meta.exportedAt, 'datetime')} (India time)`)],
    [label('Rows'), text(count)],
    [label('Filters'), text(spec.filters?.length ? spec.filters.join('; ') : 'None: the whole list')],
    [label('Times'), text('All dates and times are India time (IST).')],
    [label('Amounts'), text('Amounts are in Indian rupees (₹).')],
  ]
}

/** The rows as an .xlsx file: the data on the first sheet (bold header that stays in view), "About" on the second. */
export async function toXlsx<T>(spec: ExportSpec<T>, rows: T[], meta: ExportMeta): Promise<Blob> {
  const header: Row = spec.columns.map((column) => ({ type: String, value: dataHeader(column), ...HEADER_STYLE }))
  const data: Row[] = [header, ...rows.map((row) => spec.columns.map((column) => dataCell(column, row)))]
  const sheets: Sheet<Blob>[] = [
    { data, sheet: sheetName(spec.title), columns: spec.columns.map((column) => ({ width: columnWidth(column, rows) })), stickyRowsCount: 1 },
    { data: aboutRows(spec, rows, meta), sheet: 'About', columns: [{ width: 16 }, { width: 80 }] },
  ]
  return writeXlsxFile(sheets).toBlob()
}

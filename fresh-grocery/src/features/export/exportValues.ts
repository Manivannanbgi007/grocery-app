import type { ExportColumn, ExportValue, ExportValueType } from './exportTypes'

/*
 * The cells of an export, per format. Times are India time, like everywhere in the app (utils/format.ts), whatever the
 * computer's time zone is; a day without a time ("2026-11-09", e.g. a coupon's last day) stays that day.
 */

const DATE_ONLY = /^\d{4}-\d{2}-\d{2}$/
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** The clock of India time, in parts (hourCycle h23: midnight is 00, never 24). */
const indiaParts = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Kolkata',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

/** Rupees with paise always, so a column of amounts lines up: 50 -> "₹50.00", 125000 -> "₹1,25,000.00". */
const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 })
const indianNumber = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 })

/** A date and time as read on a clock in India; month is 1–12. */
export interface WallTime {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  second: number
}

/** The India wall time of a date value (an ISO string or a Date), or null when it is empty or not a date. */
export function wallTimeOf(value: ExportValue): WallTime | null {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null
  if (typeof value === 'string' && DATE_ONLY.test(value)) {
    const [year, month, day] = value.split('-').map(Number)
    return { year, month, day, hour: 0, minute: 0, second: 0 }
  }
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return null
  const parts: Record<string, string> = {}
  for (const part of indiaParts.formatToParts(date)) parts[part.type] = part.value
  return { year: Number(parts.year), month: Number(parts.month), day: Number(parts.day), hour: Number(parts.hour) % 24, minute: Number(parts.minute), second: Number(parts.second) }
}

/**
 * The date for an Excel cell. Excel dates have no time zone, and write-excel-file converts a Date with getTime() (UTC),
 * so the cell would show the UTC clock (10:00 for an order at 15:30 in India). This Date's UTC clock IS the India
 * clock, so the cell shows what the app shows.
 */
export function excelDate(value: ExportValue): Date | null {
  const time = wallTimeOf(value)
  return time ? new Date(Date.UTC(time.year, time.month - 1, time.day, time.hour, time.minute, time.second)) : null
}

/** A number from a cell, or null: an empty or non-numeric cell stays empty instead of becoming 0. */
export function numberOf(value: ExportValue): number | null {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean' || value instanceof Date) return null
  const number = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(number) ? number : null
}

function yesNo(value: ExportValue): string {
  return value === true || value === 'true' ? 'Yes' : 'No'
}

const pad = (number: number) => String(number).padStart(2, '0')

/** The column's name in Excel and CSV, whose cells hold plain numbers and times: the unit goes into the name. */
export function dataHeader(column: Pick<ExportColumn<unknown>, 'header' | 'type'>): string {
  switch (column.type) {
    case 'money':
      return `${column.header} (₹)`
    case 'percent':
      return `${column.header} (%)`
    case 'datetime':
      return `${column.header} (IST)`
    default:
      return column.header
  }
}

/**
 * The cell as plain data, for the CSV: numbers without grouping or symbols, days and times in ISO order
 * ("2026-11-09 15:30"), so any tool reads and sorts them.
 */
export function plainText(value: ExportValue, type: ExportValueType = 'text'): string {
  if (value === null || value === undefined) return ''
  switch (type) {
    case 'money': {
      const number = numberOf(value)
      return number === null ? '' : number.toFixed(2)
    }
    case 'number':
    case 'percent': {
      const number = numberOf(value)
      return number === null ? '' : String(number)
    }
    case 'date': {
      const time = wallTimeOf(value)
      return time ? `${time.year}-${pad(time.month)}-${pad(time.day)}` : ''
    }
    case 'datetime': {
      const time = wallTimeOf(value)
      return time ? `${time.year}-${pad(time.month)}-${pad(time.day)} ${pad(time.hour)}:${pad(time.minute)}` : ''
    }
    case 'boolean':
      return yesNo(value)
    default:
      return value instanceof Date ? value.toISOString() : String(value)
  }
}

/** The cell as people read it, for the PDF: like the app shows it ("₹1,039.20", "9 Nov 2026, 3:30 pm"). */
export function readableText(value: ExportValue, type: ExportValueType = 'text'): string {
  if (value === null || value === undefined || value === '') return ''
  switch (type) {
    case 'money': {
      const number = numberOf(value)
      return number === null ? '' : rupees.format(number)
    }
    case 'number': {
      const number = numberOf(value)
      return number === null ? '' : indianNumber.format(number)
    }
    case 'percent': {
      const number = numberOf(value)
      return number === null ? '' : `${indianNumber.format(number)}%`
    }
    case 'date': {
      const time = wallTimeOf(value)
      return time ? `${time.day} ${MONTHS[time.month - 1]} ${time.year}` : ''
    }
    case 'datetime': {
      const time = wallTimeOf(value)
      if (!time) return ''
      const hour12 = time.hour % 12 === 0 ? 12 : time.hour % 12
      return `${time.day} ${MONTHS[time.month - 1]} ${time.year}, ${hour12}:${pad(time.minute)} ${time.hour < 12 ? 'am' : 'pm'}`
    }
    case 'boolean':
      return yesNo(value)
    default:
      return value instanceof Date ? value.toISOString() : String(value)
  }
}

/**
 * The filters in use, for the export's description: the ones without a value are left out.
 *   filterLines([['Status', 'Delivered'], ['Store', undefined]]) -> ['Status: Delivered']
 */
export function filterLines(filters: [label: string, value: string | number | null | undefined | false][]): string[] {
  return filters.filter(([, value]) => value !== undefined && value !== null && value !== '' && value !== false).map(([label, value]) => `${label}: ${value}`)
}

/** "orders" -> "orders-2026-11-09-1530" + extension, in India time; the name keeps only safe characters. */
export function exportFileName(baseName: string, extension: string, now: Date = new Date()): string {
  const time = wallTimeOf(now)!
  const safe = baseName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'export'
  return `${safe}-${time.year}-${pad(time.month)}-${pad(time.day)}-${pad(time.hour)}${pad(time.minute)}.${extension}`
}

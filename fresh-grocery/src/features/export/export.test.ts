import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { PageResponse } from '../../api/types'
import { fetchAllRows } from './fetchAllRows'
import type { ExportColumn, ExportSpec } from './exportTypes'
import { dataHeader, excelDate, exportFileName, filterLines, plainText, readableText, wallTimeOf } from './exportValues'
import { toCsv } from './toCsv'

interface Row {
  id: number
  name: string
}

const NAME_ONLY: ExportColumn<Row>[] = [{ header: 'Name', value: (row) => row.name }]

/** A list of `count` rows served page by page, like a service does. */
function pagedList(count: number) {
  const all = Array.from({ length: count }, (_, index) => ({ id: index + 1, name: `Row ${index + 1}` }))
  const fetchPage = vi.fn(async (page: number, size: number): Promise<PageResponse<Row>> => ({
    content: all.slice(page * size, (page + 1) * size),
    page,
    size,
    totalElements: all.length,
    totalPages: Math.ceil(all.length / size),
    last: (page + 1) * size >= all.length,
  }))
  const spec: ExportSpec<Row> = { title: 'Rows', fileName: 'rows', itemWords: ['row', 'rows'], columns: NAME_ONLY, fetchPage, pageSize: 100 }
  return { all, fetchPage, spec }
}

describe('export values', () => {
  it('reads times in India time, and keeps a day without a time as that day', () => {
    expect(wallTimeOf('2026-11-09T10:00:00Z')).toEqual({ year: 2026, month: 11, day: 9, hour: 15, minute: 30, second: 0 })
    // 20:00 UTC is already the next day in India
    expect(wallTimeOf('2026-11-09T20:00:00Z')).toMatchObject({ day: 10, hour: 1, minute: 30 })
    expect(wallTimeOf('2026-11-09')).toMatchObject({ year: 2026, month: 11, day: 9, hour: 0 })
    expect(wallTimeOf('not a date')).toBeNull()
    expect(wallTimeOf(null)).toBeNull()
  })

  it('gives Excel a date whose clock shows India time (Excel dates have no time zone)', () => {
    expect(excelDate('2026-11-09T10:00:00Z')!.toISOString()).toBe('2026-11-09T15:30:00.000Z')
    expect(excelDate('2026-11-09')!.toISOString()).toBe('2026-11-09T00:00:00.000Z')
    expect(excelDate('')).toBeNull()
  })

  it('writes plain data for the CSV and readable text for the PDF', () => {
    expect(plainText(1039.2, 'money')).toBe('1039.20')
    expect(readableText(1039.2, 'money')).toBe('₹1,039.20')
    expect(readableText(125000, 'money')).toBe('₹1,25,000.00')
    expect(plainText('2026-11-09T10:00:00Z', 'datetime')).toBe('2026-11-09 15:30')
    expect(readableText('2026-11-09T10:00:00Z', 'datetime')).toBe('9 Nov 2026, 3:30 pm')
    expect(readableText('2026-11-09T18:45:00Z', 'datetime')).toBe('10 Nov 2026, 12:15 am')
    expect(plainText('2026-11-09', 'date')).toBe('2026-11-09')
    expect(readableText('2026-11-09', 'date')).toBe('9 Nov 2026')
    expect(plainText(12.5, 'percent')).toBe('12.5')
    expect(readableText(12.5, 'percent')).toBe('12.5%')
    expect(readableText(1234567, 'number')).toBe('12,34,567')
    expect(plainText(true, 'boolean')).toBe('Yes')
    expect(plainText(false, 'boolean')).toBe('No')
    // Empty and broken values stay empty (never 0 or "Invalid Date")
    expect(plainText(null, 'money')).toBe('')
    expect(plainText('abc', 'number')).toBe('')
    expect(readableText(undefined, 'datetime')).toBe('')
  })

  it('puts the units into the column names of Excel and CSV', () => {
    expect(dataHeader({ header: 'Total', type: 'money' })).toBe('Total (₹)')
    expect(dataHeader({ header: 'Commission', type: 'percent' })).toBe('Commission (%)')
    expect(dataHeader({ header: 'Placed', type: 'datetime' })).toBe('Placed (IST)')
    expect(dataHeader({ header: 'Store' })).toBe('Store')
  })

  it('names the file after the list and the time in India, with safe characters only', () => {
    expect(exportFileName('Delivery partners!', 'xlsx', new Date('2026-11-09T10:00:00Z'))).toBe('delivery-partners-2026-11-09-1530.xlsx')
    expect(exportFileName('***', 'csv', new Date('2026-11-09T10:00:00Z'))).toBe('export-2026-11-09-1530.csv')
  })

  it('describes only the filters in use', () => {
    expect(filterLines([['Status', 'Delivered'], ['Store', undefined], ['Customer', false], ['Search', ''], ['Zone', 0]])).toEqual(['Status: Delivered', 'Zone: 0'])
  })
})

describe('CSV', () => {
  interface Line {
    name: string
    total: number | null
    placed: string
  }
  const columns: ExportColumn<Line>[] = [
    { header: 'Name', value: (line) => line.name },
    { header: 'Total', type: 'money', value: (line) => line.total },
    { header: 'Placed', type: 'datetime', value: (line) => line.placed },
  ]

  it('has a byte order mark (for Excel), a header with units and CRLF line ends', () => {
    const csv = toCsv(columns, [{ name: 'Tomato', total: 36, placed: '2026-11-09T10:00:00Z' }])
    expect(csv.startsWith('﻿')).toBe(true)
    expect(csv.slice(1)).toBe('Name,Total (₹),Placed (IST)\r\nTomato,36.00,2026-11-09 15:30\r\n')
  })

  it('quotes commas, quotes and line breaks (RFC 4180)', () => {
    const csv = toCsv(columns, [{ name: 'Rice, basmati "XL"\nnew', total: null, placed: '' }])
    expect(csv.split('\r\n')[1]).toBe('"Rice, basmati ""XL""\nnew",,')
  })

  it('stops a text from running as a formula in a spreadsheet, but leaves numbers alone', () => {
    const rows = ['=HYPERLINK("http://x")', '+1+1', '-2', '@SUM(A1)'].map((name) => ({ name, total: -5, placed: '' }))
    const lines = toCsv(columns, rows).split('\r\n').slice(1, 5)
    expect(lines).toEqual(['"\'=HYPERLINK(""http://x"")",-5.00,', "'+1+1,-5.00,", "'-2,-5.00,", "'@SUM(A1),-5.00,"])
  })
})

describe('loading every page of a list', () => {
  it('loads all pages, a few at a time, and keeps their order', async () => {
    const { all, fetchPage, spec } = pagedList(250)
    const progress = vi.fn()
    const result = await fetchAllRows(spec, 10_000, new AbortController().signal, progress)

    expect(result).toEqual({ rows: all, total: 250, truncated: false })
    expect(fetchPage.mock.calls.map(([page, size]) => [page, size]).sort()).toEqual([
      [0, 100],
      [1, 100],
      [2, 100],
    ])
    expect(progress).toHaveBeenLastCalledWith(250, 250)
  })

  it('stops at the limit and says the list is longer', async () => {
    const { fetchPage, spec } = pagedList(250)
    const result = await fetchAllRows(spec, 150, new AbortController().signal)

    expect(result.rows.map((row) => row.id)).toEqual(Array.from({ length: 150 }, (_, index) => index + 1))
    expect(result).toMatchObject({ total: 250, truncated: true })
    expect(fetchPage).toHaveBeenCalledTimes(2) // pages 0 and 1, not 2
  })

  it('keeps a row seen twice once, when a new row pushed the list while the pages loaded', async () => {
    const pages: Row[][] = [
      [
        { id: 1, name: 'a' },
        { id: 2, name: 'b' },
      ],
      [
        { id: 2, name: 'b' },
        { id: 3, name: 'c' },
      ],
    ]
    const spec: ExportSpec<Row> = {
      ...pagedList(0).spec,
      rowKey: (row) => row.id,
      pageSize: 2,
      fetchPage: async (page) => ({ content: pages[page], page, size: 2, totalElements: 4, totalPages: 2, last: page === 1 }),
    }
    const { rows } = await fetchAllRows(spec, 100, new AbortController().signal)
    expect(rows.map((row) => row.id)).toEqual([1, 2, 3])
  })

  it('asks again when the gateway says "too many requests", and gives up at once on other errors', async () => {
    const { spec } = pagedList(10)
    const busyOnce = vi.fn().mockRejectedValueOnce(new ApiError(429, 'Too many requests')).mockImplementation(spec.fetchPage)
    const { rows } = await fetchAllRows({ ...spec, fetchPage: busyOnce }, 100, new AbortController().signal)
    expect(rows).toHaveLength(10)
    expect(busyOnce).toHaveBeenCalledTimes(2)

    const forbidden = vi.fn().mockRejectedValue(new ApiError(403, 'You may not see this list'))
    await expect(fetchAllRows({ ...spec, fetchPage: forbidden }, 100, new AbortController().signal)).rejects.toThrow('You may not see this list')
    expect(forbidden).toHaveBeenCalledTimes(1)
  })

  it('stops when the export is cancelled', async () => {
    const { spec } = pagedList(10)
    const controller = new AbortController()
    controller.abort()
    await expect(fetchAllRows(spec, 100, controller.signal)).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('takes the rows of a list that is loaded whole, up to the limit', async () => {
    const rows = Array.from({ length: 5 }, (_, index) => ({ id: index, name: String(index) }))
    const spec: ExportSpec<Row> = { title: 'Rows', fileName: 'rows', itemWords: ['row', 'rows'], columns: NAME_ONLY, rows }
    expect(await fetchAllRows(spec, 3, new AbortController().signal)).toEqual({ rows: rows.slice(0, 3), total: 5, truncated: true })
  })
})

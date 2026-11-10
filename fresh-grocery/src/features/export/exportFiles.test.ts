import { strFromU8, unzipSync } from 'fflate'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import boldFont from '../../assets/fonts/NotoSans-Bold.ttf?inline'
import regularFont from '../../assets/fonts/NotoSans-Regular.ttf?inline'
import type { ExportMeta, ExportSpec } from './exportTypes'
import { loadPdfFonts } from './pdfFonts'
import { toPdf } from './toPdf'
import { toXlsx } from './toXlsx'

// The real font files (the browser fetches them; the tests get them inlined by Vite as "data:…;base64,…")
vi.mock('./pdfFonts', () => ({ loadPdfFonts: vi.fn() }))
const base64Of = (dataUrl: string) => dataUrl.slice(dataUrl.indexOf(',') + 1)

interface Order {
  id: number
  store: string
  total: number
  placedAt: string
}

const SPEC: ExportSpec<Order> = {
  title: 'Orders',
  fileName: 'orders',
  itemWords: ['order', 'orders'],
  filters: ['Status: Delivered'],
  columns: [
    { header: 'Order #', type: 'number', value: (order) => order.id },
    { header: 'Store', value: (order) => order.store },
    { header: 'Placed', type: 'datetime', value: (order) => order.placedAt },
    { header: 'Total', type: 'money', value: (order) => order.total },
    { header: 'Note', value: () => '=1+1' },
  ],
  rows: [],
}

const ROWS: Order[] = [
  { id: 101, store: 'Fresh Mart', total: 1039.2, placedAt: '2026-11-09T10:00:00Z' },
  { id: 102, store: 'Green Basket', total: 50, placedAt: '2026-11-09T18:45:00Z' },
]

const META: ExportMeta = { exportedBy: 'Platform Admin (Admin)', exportedAt: new Date('2026-11-09T11:00:00Z'), total: 2, truncated: false }

async function bytesOf(blob: Blob): Promise<Uint8Array> {
  return new Uint8Array(await blob.arrayBuffer())
}

/** Excel's serial number of a date whose clock reads `iso` (days since 30 Dec 1899). */
function serial(iso: string): number {
  return new Date(iso).getTime() / 86_400_000 + 25_569
}

describe('the Excel file', () => {
  it('has the data on a sheet named after the list, typed cells in India time, and an "About" sheet', async () => {
    const files = unzipSync(await bytesOf(await toXlsx(SPEC, ROWS, META)))
    const workbook = strFromU8(files['xl/workbook.xml'])
    expect(workbook).toContain('name="Orders"')
    expect(workbook).toContain('name="About"')

    const sheet = strFromU8(files['xl/worksheets/sheet1.xml'])
    const strings = strFromU8(files['xl/sharedStrings.xml'])
    // The header stays in view while scrolling, and names the units
    expect(sheet).toContain('state="frozen"')
    expect(strings).toContain('Total (₹)')
    expect(strings).toContain('Placed (IST)')
    // Amounts and ids are numbers Excel can sum, not texts
    expect(sheet).toMatch(/<c [^>]*><v>1039\.2<\/v><\/c>/)
    expect(sheet).toMatch(/<c [^>]*><v>101<\/v><\/c>/)
    // 10:00 UTC is 15:30 in India: the cell's date reads 15:30
    expect(sheet).toContain(`<v>${serial('2026-11-09T15:30:00Z')}</v>`)
    // A formula-looking text stays a text in .xlsx (written as a string, never as a formula)
    expect(sheet).not.toContain('<f>')
    expect(strings).toContain('=1+1')

    const about = strFromU8(files['xl/worksheets/sheet2.xml']) + strings
    expect(about).toContain('Platform Admin (Admin)')
    expect(about).toContain('Status: Delivered')
  })

  it('says on the "About" sheet when the list was longer than the file', async () => {
    const files = unzipSync(await bytesOf(await toXlsx(SPEC, ROWS, { ...META, total: 25_310, truncated: true })))
    expect(strFromU8(files['xl/sharedStrings.xml'])).toContain('2 of 25,310 orders: the first 2. Narrow the filters to export the rest.')
  })
})

describe('the PDF', () => {
  beforeEach(() => {
    vi.mocked(loadPdfFonts).mockResolvedValue({ regular: base64Of(regularFont), bold: base64Of(boldFont) })
  })

  /** The PDF's own structure (objects are not compressed, only the page contents are). */
  async function pdfText(blob: Blob): Promise<string> {
    return new TextDecoder('latin1').decode(await bytesOf(blob))
  }
  const pageCount = (pdf: string) => pdf.match(/\/Type \/Page\b(?!s)/g)?.length ?? 0

  it('is a PDF with the title, in Noto Sans (which has the rupee sign)', async () => {
    const pdf = await pdfText(await toPdf(SPEC, ROWS, META))
    expect(pdf.startsWith('%PDF-')).toBe(true)
    expect(pdf).toContain('/Title (Orders - Fresh Grocery)')
    expect(pdf).toContain('NotoSans')
    expect(pageCount(pdf)).toBe(1)
  })

  it('goes on over several pages for a long list', async () => {
    const many = Array.from({ length: 300 }, (_, index) => ({ ...ROWS[0], id: index + 1 }))
    const pdf = await pdfText(await toPdf(SPEC, many, { ...META, total: 300 }))
    expect(pageCount(pdf)).toBeGreaterThan(5)
  })

  it('still works without the font (offline), with the built-in Helvetica', async () => {
    vi.mocked(loadPdfFonts).mockRejectedValue(new Error('offline'))
    const pdf = await pdfText(await toPdf(SPEC, ROWS, META))
    expect(pdf.startsWith('%PDF-')).toBe(true)
    expect(pdf).toContain('/BaseFont /Helvetica')
    expect(pdf).not.toContain('NotoSans')
  })
})

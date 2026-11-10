import { jsPDF } from 'jspdf'
import { autoTable, type Styles } from 'jspdf-autotable'
import { readableText } from './exportValues'
import type { ExportMeta, ExportSpec, ExportValueType } from './exportTypes'
import { MAX_EXPORT_ROWS } from './exportLimits'
import { loadPdfFonts } from './pdfFonts'

/*
 * The PDF (a report to read, share or print). Loaded only when someone exports a PDF (ExportMenu imports it on demand),
 * so jsPDF is not part of the app's first download. The colours are the app's: brand green header, heading text.
 */

type Rgb = [number, number, number]
const BRAND: Rgb = [9, 121, 47]
const HEADING: Rgb = [37, 61, 78]
const BODY: Rgb = [90, 90, 90]
const SURFACE: Rgb = [244, 246, 250]
const LINE: Rgb = [205, 216, 210]
const WARN: Rgb = [180, 83, 9]
const WHITE: Rgb = [255, 255, 255]

const MARGIN = 36
const RIGHT_ALIGNED: ExportValueType[] = ['money', 'number', 'percent']

interface PdfFont {
  family: string
  /** makes a text safe for the font: unchanged with Noto Sans */
  clean: (text: string) => string
}

/** Noto Sans when it loads; otherwise the built-in Helvetica with "Rs." for "₹" and "?" for letters it does not have. */
async function setUpFont(doc: jsPDF): Promise<PdfFont> {
  try {
    const fonts = await loadPdfFonts()
    doc.addFileToVFS('NotoSans-Regular.ttf', fonts.regular)
    doc.addFont('NotoSans-Regular.ttf', 'NotoSans', 'normal')
    doc.addFileToVFS('NotoSans-Bold.ttf', fonts.bold)
    doc.addFont('NotoSans-Bold.ttf', 'NotoSans', 'bold')
    return { family: 'NotoSans', clean: (text) => text }
  } catch {
    return { family: 'helvetica', clean: (text) => text.replace(/₹/g, 'Rs. ').replace(/[^\n -ÿ]/g, '?') }
  }
}

function countLine<T>(spec: ExportSpec<T>, rows: T[], meta: ExportMeta): string {
  const shown = rows.length.toLocaleString('en-IN')
  if (meta.truncated) return `The first ${shown} of ${meta.total.toLocaleString('en-IN')} ${spec.itemWords[1]}`
  return `${shown} ${rows.length === 1 ? spec.itemWords[0] : spec.itemWords[1]}`
}

/** The rows as a PDF on A4 (landscape for wide tables): title, who and when, filters, the table, page numbers. */
export async function toPdf<T>(spec: ExportSpec<T>, rows: T[], meta: ExportMeta): Promise<Blob> {
  const doc = new jsPDF({ orientation: spec.columns.length > 4 ? 'landscape' : 'portrait', unit: 'pt', format: 'a4', compress: true })
  doc.setProperties({ title: `${spec.title} - Fresh Grocery`, author: meta.exportedBy, creator: 'Fresh Grocery' })
  const { family, clean } = await setUpFont(doc)
  const pageWidth = doc.internal.pageSize.getWidth()
  const pageHeight = doc.internal.pageSize.getHeight()
  const exportedAt = readableText(meta.exportedAt, 'datetime')

  // The title block, on the first page
  let y = MARGIN + 10
  doc.setFont(family, 'bold')
  doc.setFontSize(16)
  doc.setTextColor(...HEADING)
  doc.text(clean(spec.title), MARGIN, y)
  y += 18
  doc.setFont(family, 'normal')
  doc.setFontSize(9)
  doc.setTextColor(...BODY)
  const lines = [`${countLine(spec, rows, meta)} · Exported by ${meta.exportedBy} on ${exportedAt} (India time)`]
  if (spec.filters?.length) lines.push(`Filters: ${spec.filters.join(' · ')}`)
  for (const line of lines) {
    const wrapped: string[] = doc.splitTextToSize(clean(line), pageWidth - 2 * MARGIN)
    doc.text(wrapped, MARGIN, y)
    y += wrapped.length * 12
  }
  if (meta.truncated) {
    doc.setTextColor(...WARN)
    const note = `This list is longer than one PDF holds (${MAX_EXPORT_ROWS.pdf.toLocaleString('en-IN')} rows): narrow the filters, or export to Excel or CSV (up to ${MAX_EXPORT_ROWS.xlsx.toLocaleString('en-IN')} rows).`
    const wrapped: string[] = doc.splitTextToSize(clean(note), pageWidth - 2 * MARGIN)
    doc.text(wrapped, MARGIN, y)
    y += wrapped.length * 12
  }

  const columnStyles: Record<number, Partial<Styles>> = {}
  spec.columns.forEach((column, index) => {
    if (RIGHT_ALIGNED.includes(column.type ?? 'text')) columnStyles[index] = { halign: 'right' }
  })

  autoTable(doc, {
    head: [spec.columns.map((column) => clean(column.header))],
    body: rows.map((row) => spec.columns.map((column) => clean(readableText(column.value(row), column.type)))),
    startY: y + 6,
    // Later pages start below their small title line and end above the footer
    margin: { top: MARGIN + 14, right: MARGIN, bottom: MARGIN + 10, left: MARGIN },
    theme: 'striped',
    showHead: 'everyPage',
    styles: { font: family, fontSize: 8, cellPadding: 4, textColor: HEADING, lineColor: LINE, lineWidth: 0.4, overflow: 'linebreak', valign: 'top' },
    headStyles: { font: family, fontStyle: 'bold', fillColor: BRAND, textColor: WHITE },
    alternateRowStyles: { fillColor: SURFACE },
    columnStyles,
    // A number column's name sits on the right too, above its numbers
    didParseCell: (cell) => {
      if (cell.section === 'head' && columnStyles[cell.column.index]) cell.cell.styles.halign = 'right'
    },
  })

  // Every page's title line and footer, now that the number of pages is known
  const pageCount = doc.getNumberOfPages()
  for (let page = 1; page <= pageCount; page++) {
    doc.setPage(page)
    doc.setFont(family, 'normal')
    doc.setFontSize(8)
    doc.setTextColor(...BODY)
    if (page > 1) doc.text(clean(`Fresh Grocery · ${spec.title}`), MARGIN, MARGIN)
    doc.setDrawColor(...LINE)
    doc.line(MARGIN, pageHeight - MARGIN, pageWidth - MARGIN, pageHeight - MARGIN)
    doc.text(clean(`Fresh Grocery · ${spec.title} · exported ${exportedAt} (India time)`), MARGIN, pageHeight - MARGIN + 12)
    doc.text(`Page ${page} of ${pageCount}`, pageWidth - MARGIN, pageHeight - MARGIN + 12, { align: 'right' })
  }

  return doc.output('blob')
}

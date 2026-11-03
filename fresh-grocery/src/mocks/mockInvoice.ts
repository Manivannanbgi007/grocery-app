import type { Order } from '../api/types'

/*
 * A small but real PDF for the mock invoice download, so "Download invoice" opens a file in mock mode too.
 * The real invoice (order-service, OpenPDF) has the store, GST and line details; this one only lists the basics.
 * Plain ASCII only: the built-in PDF font has no ₹ sign, so amounts are written as "Rs.".
 */

function pdfText(text: string): string {
  return text.replace(/[\\()]/g, (character) => `\\${character}`)
}

/** A one-page PDF with one line of text per entry. */
function simplePdf(lines: string[]): string {
  const content = lines.map((line, index) => `BT /F1 12 Tf 50 ${790 - index * 20} Td (${pdfText(line)}) Tj ET`).join('\n')
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${content.length} >>\nstream\n${content}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ]
  let pdf = '%PDF-1.4\n'
  const offsets = objects.map((body, index) => {
    const offset = pdf.length
    pdf += `${index + 1} 0 obj\n${body}\nendobj\n`
    return offset
  })
  const xrefStart = pdf.length
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}`
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`
  return pdf
}

const rupees = (amount: number) => `Rs. ${amount.toFixed(2)}`

/** The file name the backend uses, e.g. "invoice-INV-000901.pdf". */
export function mockInvoiceFileName(order: Order): string {
  return `invoice-INV-${String(order.id).padStart(6, '0')}.pdf`
}

export function mockInvoicePdf(order: Order): string {
  return simplePdf([
    'TAX INVOICE (mock)',
    `Order #${order.id}  |  ${order.storeName}`,
    `Deliver to: ${order.deliveryAddress.contactName}, ${order.deliveryAddress.city} ${order.deliveryAddress.pincode}`,
    '',
    ...order.items.map((item) => `${item.quantity} x ${item.productName}  ${rupees(item.lineTotal)}`),
    '',
    `Items total: ${rupees(order.amounts.itemsTotal)}`,
    `Coupon: -${rupees(order.amounts.couponDiscount)}`,
    `Delivery: ${rupees(order.amounts.deliveryFee)}`,
    `Total: ${rupees(order.amounts.totalAmount)}`,
  ])
}

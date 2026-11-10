import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { csvLines, exportList } from '../../test/exportFile'
import { logInAs, renderApp } from '../../test/render'
import { saveFile } from '../../utils/download'

vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))

/** "Showing 1–20 of 46 stores" or "6 stores": the line above a list, as the page says it. */
function totalLine(words: string): Promise<HTMLElement> {
  return screen.findByText((_, element) => element?.tagName === 'P' && new RegExp(`^(Showing \\d+–\\d+ of )?\\d+ ${words}$`).test(element.textContent ?? ''))
}

/** The list's total as the page says it: "Showing 1–20 of 46 stores" -> 46. */
async function totalOnPage(words: string): Promise<number> {
  const line = await totalLine(words)
  return Number(/(\d+) \S+$/.exec(line.textContent ?? '')![1])
}

/** A file's name: the start, then the day and time it was made. */
const fileNameOf = (start: string) => new RegExp(`^${start}-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.csv$`)

describe('the exports of the admin lists', () => {
  it('exports every delivery of the tab', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/deliveries?tab=all&size=10')
    // The seed has one delivery: "1 delivery"
    const total = await totalOnPage('deliver(y|ies)')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNameOf('deliveries'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Order #,Created (IST),Status,Issue,Issue note,Store,Store address,Customer,Customer address,Partner,Partner earns (₹),Cash (₹),Paid online')
    expect(rows).toHaveLength(total)
  })

  it('exports every payment with the filters, and the lines of the money summary', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/payments?size=10')
    const line = await totalLine('payments')
    const total = Number(/(\d+) \S+$/.exec(line.textContent ?? '')![1])

    // The list's Export is the one in its row of page controls (the money summary has its own)
    const list = await exportList(user, vi.mocked(saveFile), 'CSV', line.parentElement!)

    expect(list.fileName).toMatch(fileNameOf('payments'))
    const [header, ...rows] = csvLines(list.text)
    expect(header).toBe('Payment #,Payment method,Status,Amount (₹),Order #,Store,Created (IST),Paid (IST),Pay before (IST),Failure reason,Refund reference,Refunded (IST)')
    expect(rows).toHaveLength(total)

    // The lines of "Every method and status": one row per line, and their payments add up to the table's total
    const card = screen.getByRole('region', { name: 'Every method and status' })
    const lines = await exportList(user, vi.mocked(saveFile), 'CSV', card)

    expect(lines.fileName).toMatch(fileNameOf('payment-totals'))
    const [linesHeader, ...lineRows] = csvLines(lines.text)
    expect(linesHeader).toBe('Method,Status,Payments,Amount (₹)')
    expect(lineRows.length).toBeGreaterThan(0)
    const allMethods = within(card).getByRole('row', { name: /^All methods/ })
    const payments = lineRows.reduce((sum, row) => sum + Number(row.split(',')[2]), 0)
    expect(String(payments)).toBe(within(allMethods).getAllByRole('cell')[0].textContent)
  })

  it('exports every store of the tab and search', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/stores?tab=all&size=10')
    const total = await totalOnPage('stores')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNameOf('stores'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Store,Store #,Status,City,PIN code,Phone,Email,Registered,Commission (%)')
    expect(rows).toHaveLength(total)
  })

  it('exports every delivery partner of the tab', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/partners?tab=all&size=10')
    const total = await totalOnPage('partners')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNameOf('delivery-partners'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Partner,Account #,Phone,Now,KYC,KYC note,Vehicle,Vehicle number,Driving licence,Zone,Rating,Ratings')
    expect(rows).toHaveLength(total)
  })

  it('exports every account of the role', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/accounts?size=10')
    const total = await totalOnPage('accounts')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNameOf('accounts'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Account #,Name,Email,Role,Status,Phone,Joined,Last login (IST)')
    expect(rows).toHaveLength(total)
  })

  it('exports every admin account (super admin)', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/admins')
    const total = await totalOnPage('admins?')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNameOf('admins'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Account #,Name,Email,Status,Phone,Added,Last login (IST)')
    expect(rows).toHaveLength(total)
  })

  it('exports every notification with the filters', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/notifications?size=10')
    const total = await totalOnPage('notifications')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNameOf('notifications'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Type,Title,Message,To account #,Sent (IST),Seen,Email')
    expect(rows).toHaveLength(total)
  })
})

import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { csvLines, exportList } from '../../test/exportFile'
import { logInAs, renderApp } from '../../test/render'
import { saveFile } from '../../utils/download'

vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))

/*
 * The export of the catalog, marketing and owner lists of the admin panel, and of "Sales per day" (mock API): each
 * exports its whole list (every page) with the filters in use, with the same columns as on screen.
 */

/** "Showing 1–20 of 46 products" or "6 products" -> 46 / 6: the list's total as the page says it (above and below the list). */
async function totalOnPage(words: string): Promise<number> {
  const [line] = await screen.findAllByText(
    (_, element) => element?.tagName === 'P' && new RegExp(`(of )?\\d+ ${words}$`).test(element.textContent ?? ''),
    {},
    { timeout: 10_000 },
  )
  return Number(/(\d+) \S+$/.exec(line.textContent ?? '')![1])
}

const fileNamePattern = (start: string) => new RegExp(`^${start}-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.csv$`)

describe('the export of the catalog lists', () => {
  it("exports every store's products with the search and filters", async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/products')
    const total = await totalOnPage('products')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNamePattern('products'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Product,SKU,Store,Category,Price (₹),MRP (₹),Can buy,Low stock,Status')
    expect(rows).toHaveLength(total)
    expect(total).toBeGreaterThan(20) // more than the page on screen
  })

  it('exports every category that matches the search, not only the page on screen', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/categories')
    expect(await totalOnPage('categories')).toBe(21)

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNamePattern('categories'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Category,Slug,Description,Position,Status')
    expect(rows).toHaveLength(21)
  })

  it('exports every review with the filters', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/reviews')
    const total = await totalOnPage('reviews')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNamePattern('reviews'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Product #,Rating,Status,Comment,Hidden because,Reviewer,Date,Order #,Store')
    expect(rows).toHaveLength(total)
    expect(rows[0]).toMatch(/^\d+,[1-5],(Visible|Hidden),/)
  })

  it('exports every coupon of the tab', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/coupons?tab=all')
    const total = await totalOnPage('coupons')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNamePattern('coupons'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Code,Where,Offer,Minimum order (₹),Valid from,Valid until,Used,Usage limit,Status,On')
    expect(rows).toHaveLength(total)
  })
})

describe("the export of the super admin's lists", () => {
  it('exports every audit entry with the filters', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/audit-log')
    const total = await totalOnPage('entries')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNamePattern('audit-log'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Event,Time (IST),Source,Reference,Order #,Account #,Store #,Details')
    expect(rows).toHaveLength(total)
  })

  it('exports the commission per store of the period', async () => {
    logInAs('superadmin@grocery.com')
    const { user } = renderApp('/admin/commission')
    const table = await screen.findByRole('table', { name: /Commission per store/ }, { timeout: 10_000 })
    const stores = within(table).getAllByRole('row').length - 2 // without the header and "All stores"

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(fileNamePattern('commission'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Store,Delivered orders,Items (₹),Commission (₹),Share (%),Store gets (₹)')
    expect(rows).toHaveLength(stores)
    expect(stores).toBeGreaterThan(0)
  })
})

describe('the export of "Sales per day"', () => {
  it('exports one line per day of the period', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/reports')
    const card = await screen.findByRole('region', { name: 'Sales per day' }, { timeout: 10_000 })
    // The chart's days (a list for screen readers)
    const days = within(within(card).getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem').length

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV', card)

    expect(fileName).toMatch(fileNamePattern('sales-per-day'))
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Day,Best day,Orders,Cancelled,Sales (₹),Change (₹)')
    expect(rows).toHaveLength(days)
    expect(days).toBe(30) // the last 30 days by default
    expect(rows[0]).toMatch(/^\d{4}-\d{2}-\d{2},(Yes|No),\d+,\d+,\d+\.\d{2},$/) // no change on the first day
  })
})

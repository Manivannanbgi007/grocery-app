import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { csvLines, exportList } from '../../test/exportFile'
import { logInAs, renderApp } from '../../test/render'
import { saveFile } from '../../utils/download'

vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))

/*
 * The exports of the vendor panel on the mock API: vendor1 = Ravi Kumar, Fresh Mart (store 1). Each list exports every
 * row with its filters, not only the page on screen.
 */

/** "Showing 1–20 of 64 products" or "2 coupons" -> 64 / 2: the list's total as its page controls (above and below) say it. */
async function totalOnPage(words: string): Promise<number> {
  const [line] = await screen.findAllByText((_, element) => element?.tagName === 'P' && new RegExp(`^(Showing \\S+ of )?\\d+ ${words}$`).test(element.textContent ?? ''))
  return Number(/(\d+) \S+$/.exec(line.textContent ?? '')![1])
}

describe('the exports of the vendor panel', () => {
  it("exports the store's orders of the tab", async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/orders?tab=all')
    const list = await screen.findByRole('list', { name: /^All orders, newest first/ })
    const total = Number(/\((\d+)\)$/.exec(list.getAttribute('aria-label') ?? '')![1])

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(/^store-orders-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Order #,Status,Placed (IST),Items,Payment method,Payment,Total (₹)')
    expect(rows).toHaveLength(total)
    expect(total).toBeGreaterThan(1)
    // The store's words for the statuses, as on the cards
    expect(rows.find((row) => row.startsWith('903,'))).toMatch(/^903,New,/)
  })

  it('exports every product with the filters, not only the page on screen', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/products?size=10')
    const total = await totalOnPage('products')
    expect(total).toBeGreaterThan(10)

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(/^products-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Product,SKU,Category,Price (₹),MRP (₹),Can buy,Sold out,Low stock,Status')
    expect(rows).toHaveLength(total)
  })

  it("exports the store's coupons", async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/coupons')
    const total = await totalOnPage('coupons')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(/^coupons-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Code,Offer,Minimum order (₹),Valid from,Valid until,Used,Usage limit,Status,On')
    expect(rows).toHaveLength(total)
    expect(rows.find((row) => row.startsWith('MANGO15,'))).toMatch(/,Switched off,No$/)
  })

  it('exports the reviews of the stars chosen, with the names of their products', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor/reviews?rating=5')
    const list = await screen.findByRole('list', { name: 'Reviews' })
    const total = await totalOnPage('reviews?')
    // The list has looked up the names of its products
    await waitFor(() => expect(within(list).queryAllByRole('link', { name: /^Product #/ })).toHaveLength(0))

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(/^reviews-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Product,Rating,Comment,Customer,Date,Order #')
    expect(rows).toHaveLength(total)
    expect(rows.every((row) => !row.startsWith('Product #'))).toBe(true)
    expect(rows.every((row) => /^[^,]+,5,/.test(row) || /^"[^"]+",5,/.test(row))).toBe(true)
  })
})

import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { csvLines, exportList } from '../../test/exportFile'
import { logInAs, renderApp } from '../../test/render'
import { saveFile } from '../../utils/download'

vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))

/** "Showing 1–20 of 46 orders" or "6 orders" -> 46 / 6: the list's total as the page says it. */
async function totalOnPage(words: string): Promise<number> {
  const line = await screen.findByText((_, element) => element?.tagName === 'P' && new RegExp(`(of )?\\d+ ${words}$`).test(element.textContent ?? ''))
  return Number(/(\d+) \S+$/.exec(line.textContent ?? '')![1])
}

describe('the export of a list (admin orders, the reference page)', () => {
  it('exports every order with the filters, not only the page on screen, as CSV', async () => {
    logInAs('admin@grocery.com')
    const { user } = renderApp('/admin/orders?size=10')
    const total = await totalOnPage('orders')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(/^orders-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Order #,Status,Store,Customer account #,Placed (IST),Items,Payment method,Payment,Total (₹)')
    expect(rows).toHaveLength(total)
    expect(rows[0]).toMatch(/^\d+,/)
    expect(await screen.findByText(`Exported ${total} orders to CSV.`)).toBeInTheDocument()
  })
})

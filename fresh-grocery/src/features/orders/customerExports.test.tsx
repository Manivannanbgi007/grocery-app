import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { csvLines, exportList } from '../../test/exportFile'
import { logInAs, renderApp } from '../../test/render'
import { saveFile } from '../../utils/download'

vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))

/*
 * The exports of the customer's pages on the mock API: customer1 = Priya Sharma, with order 901 delivered (₹177) and
 * 902 cancelled, and four notifications (two of them unread).
 */

/**
 * The page controls above a list ("Showing 1–10 of 46 orders" or "2 orders"): the list's total as they say it, and the
 * controls themselves, which hold the list's Export button.
 */
async function pageControls(words: string): Promise<{ total: number; controls: HTMLElement }> {
  const [line] = await screen.findAllByText((_, element) => element?.tagName === 'P' && new RegExp(`^(Showing \\S+ of )?\\d+ ${words}$`).test(element.textContent ?? ''))
  return { total: Number(/(\d+) \S+$/.exec(line.textContent ?? '')![1]), controls: line.parentElement! }
}

describe("the exports of the customer's pages", () => {
  it('exports every order of My orders with the filter', async () => {
    logInAs('customer1@grocery.com')
    const { user } = renderApp('/orders?status=DELIVERED')
    await screen.findByRole('list', { name: 'Your orders' })
    // The list's own Export button: the spending card above has one too
    const { total, controls } = await pageControls('orders?')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV', controls)

    expect(fileName).toMatch(/^my-orders-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Order #,Status,Placed (IST),Store,Items,Payment method,Total (₹)')
    expect(rows).toHaveLength(total)
    // Only the delivered orders, like the list
    expect(rows).toEqual([expect.stringMatching(/^901,Delivered,\d{4}-\d{2}-\d{2} \d{2}:\d{2},Fresh Mart,\d+,Cash on delivery,177\.00$/)])
  })

  it('exports the spending per month', async () => {
    logInAs('customer1@grocery.com')
    const { user } = renderApp('/orders')
    const card = await screen.findByRole('region', { name: 'Your spending' })

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV', card)

    expect(fileName).toMatch(/^spending-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Month,Orders,Spent (₹)')
    expect(rows).toHaveLength(6)
    expect(rows.some((row) => row.endsWith(',1,177.00'))).toBe(true)
  })

  it('exports the notifications of the tab', async () => {
    logInAs('customer1@grocery.com')
    const { user } = renderApp('/account/notifications?show=unread')
    await screen.findByRole('list', { name: 'Notifications' })
    const { total } = await pageControls('notifications?')

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(/^notifications-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Title,Message,Received (IST),Read')
    expect(rows).toHaveLength(total)
    expect(total).toBeGreaterThan(0)
    expect(rows.every((row) => row.endsWith(',No'))).toBe(true)
  })
})

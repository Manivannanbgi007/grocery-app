import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { csvLines, exportList } from '../../test/exportFile'
import { logInAs, renderApp } from '../../test/render'
import { saveFile } from '../../utils/download'

vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))

/*
 * The exports of the delivery panel on the mock API: delivery1 = Arjun Singh (user 5), who delivered order 901 three
 * days ago (₹40 earned, ₹177 cash collected).
 */

describe('the exports of the delivery panel', () => {
  it('exports every finished delivery', async () => {
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery/history')
    const list = await screen.findByRole('list', { name: 'Finished deliveries' }, { timeout: 10_000 })
    // "1 delivery" or "Showing 1–20 of 46 deliveries" above (and below) the list
    const [controls] = within(list.parentElement!).getAllByText((_, element) => element?.tagName === 'P' && /^(Showing \S+ of )?\d+ deliver(y|ies)$/.test(element.textContent ?? ''))
    const total = Number(/(\d+) \S+$/.exec(controls.textContent ?? '')![1])

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV')

    expect(fileName).toMatch(/^delivery-history-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe(
      'Order #,Status,Started (IST),Delivered (IST),Earned (₹),Store,Customer,Payment method,Cash collected (₹),On the road (min),Proof photo,Problem,Customer rating,Customer comment',
    )
    expect(rows).toHaveLength(total)
    expect(rows.find((row) => row.startsWith('901,'))).toMatch(/^901,Delivered,.*,40\.00,/)
  })

  it('exports the earnings of every day of the period', async () => {
    logInAs('delivery1@grocery.com')
    const { user } = renderApp('/delivery/earnings?days=7')
    const section = (await screen.findByRole('heading', { name: 'Earned per day' }, { timeout: 10_000 })).closest('section')!

    const { fileName, text } = await exportList(user, vi.mocked(saveFile), 'CSV', section)

    expect(fileName).toMatch(/^earnings-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    const [header, ...rows] = csvLines(text)
    expect(header).toBe('Day,Deliveries,Earned (₹)')
    expect(rows).toHaveLength(7)
    expect(rows[0]).toMatch(/^\d{4}-\d{2}-\d{2},\d+,\d+\.\d{2}$/)
  })
})

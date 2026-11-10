import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { PageResponse } from '../../api/types'
import { renderWithProviders } from '../../test/render'
import { saveFile } from '../../utils/download'
import { ExportMenu } from './ExportMenu'
import type { ExportSpec } from './exportTypes'

vi.mock('../../utils/download', () => ({ saveFile: vi.fn() }))

interface Row {
  id: number
  name: string
}

const ROWS: Row[] = [
  { id: 1, name: 'Tomato' },
  { id: 2, name: 'Onion' },
  { id: 3, name: 'Milk' },
]

function specWith(fetchPage: (page: number, size: number, signal: AbortSignal) => Promise<PageResponse<Row>>): ExportSpec<Row> {
  return { title: 'Orders', fileName: 'orders', itemWords: ['order', 'orders'], columns: [{ header: 'Name', value: (row) => row.name }], fetchPage }
}

const onePage = async (): Promise<PageResponse<Row>> => ({ content: ROWS, page: 0, size: 100, totalElements: 3, totalPages: 1, last: true })

describe('ExportMenu', () => {
  it('opens a menu of Excel, CSV and PDF that works with the keyboard', async () => {
    const { user } = renderWithProviders(<ExportMenu spec={specWith(onePage)} total={3} />)
    const button = screen.getByRole('button', { name: 'Export' })
    expect(button).toHaveAttribute('aria-haspopup', 'menu')

    button.focus()
    await user.keyboard('{Enter}')
    expect(button).toHaveAttribute('aria-expanded', 'true')
    const items = screen.getAllByRole('menuitem')
    expect(items.map((item) => item.textContent)).toEqual([expect.stringMatching(/^Excel/), expect.stringMatching(/^CSV/), expect.stringMatching(/^PDF/)])
    expect(items[0]).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(items[1]).toHaveFocus()
    await user.keyboard('{End}')
    expect(items[2]).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(items[0]).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(button).toHaveFocus()
  })

  it('says beforehand that the whole list is exported, and which formats hold only part of a long one', async () => {
    const { user } = renderWithProviders(<ExportMenu spec={specWith(onePage)} total={2500} />)
    await user.click(screen.getByRole('button', { name: 'Export' }))

    expect(screen.getByText(/All 2,500 orders with the filters in use, not only this page/)).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /^PDF/ })).toHaveTextContent('The first 2,000 of 2,500')
    expect(screen.getByRole('menuitem', { name: /^Excel/ })).not.toHaveTextContent('The first')
  })

  it('shows the progress while it exports, then saves the file and says how many rows it holds', async () => {
    let answer!: () => void
    const slow = () => new Promise<PageResponse<Row>>((resolve) => (answer = () => void onePage().then(resolve)))
    const { user } = renderWithProviders(<ExportMenu spec={specWith(slow)} total={3} />)
    await user.click(screen.getByRole('button', { name: 'Export' }))
    await user.click(screen.getByRole('menuitem', { name: /^CSV/ }))

    expect(screen.getByRole('status')).toHaveTextContent('Exporting to CSV… 0%')
    expect(screen.queryByRole('button', { name: 'Export' })).not.toBeInTheDocument()
    answer()

    await waitFor(() => expect(saveFile).toHaveBeenCalledTimes(1))
    const [blob, fileName] = vi.mocked(saveFile).mock.calls[0]
    expect(fileName).toMatch(/^orders-\d{4}-\d{2}-\d{2}-\d{4}\.csv$/)
    // The file starts with the UTF-8 byte order mark (EF BB BF), for Excel; Blob.text() drops it when decoding
    expect([...new Uint8Array(await blob.arrayBuffer()).slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf])
    expect(await blob.text()).toBe('Name\r\nTomato\r\nOnion\r\nMilk\r\n')
    expect(await screen.findByText('Exported 3 orders to CSV.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Export' })).toBeInTheDocument()
  })

  it('can be cancelled while it loads, and then saves nothing', async () => {
    vi.mocked(saveFile).mockClear()
    const neverEnding = (_page: number, _size: number, signal: AbortSignal) =>
      new Promise<PageResponse<Row>>((_, reject) => signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError'))))
    const { user } = renderWithProviders(<ExportMenu spec={specWith(neverEnding)} total={3} />)
    await user.click(screen.getByRole('button', { name: 'Export' }))
    await user.click(screen.getByRole('menuitem', { name: /^Excel/ }))

    await user.click(screen.getByRole('button', { name: 'Cancel the export' }))
    expect(await screen.findByText('Export cancelled.')).toBeInTheDocument()
    expect(saveFile).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Export' })).toBeInTheDocument()
  })

  it("says what went wrong when the list can't be loaded", async () => {
    const refused = () => Promise.reject(new ApiError(403, 'You may not see these orders.'))
    const { user } = renderWithProviders(<ExportMenu spec={specWith(refused)} total={3} />)
    await user.click(screen.getByRole('button', { name: 'Export' }))
    await user.click(screen.getByRole('menuitem', { name: /^PDF/ }))

    expect(await screen.findByText('Could not export the orders. You may not see these orders.')).toBeInTheDocument()
  })

  it('tells when the file holds only the first rows of a longer list', async () => {
    vi.mocked(saveFile).mockClear()
    const many: Row[] = Array.from({ length: 10_005 }, (_, index) => ({ id: index, name: `Row ${index}` }))
    const spec: ExportSpec<Row> = { title: 'Orders', fileName: 'orders', itemWords: ['order', 'orders'], columns: [{ header: 'Name', value: (row) => row.name }], rows: many }
    const { user } = renderWithProviders(<ExportMenu spec={spec} total={many.length} />)
    await user.click(screen.getByRole('button', { name: 'Export' }))
    await user.click(screen.getByRole('menuitem', { name: /^CSV/ }))

    expect(await screen.findByText('Exported the first 10,000 orders of 10,005 to CSV. Narrow the filters to export the rest.')).toBeInTheDocument()
  })
})

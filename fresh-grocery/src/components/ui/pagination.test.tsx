import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { chooseOption } from '../../test/pickers'
import { logInAs, renderApp } from '../../test/render'
import { TablePagination } from './TablePagination'

describe('TablePagination', () => {
  it('names the rows of the page and offers the list\'s own page size too', async () => {
    const onPageSizeChange = vi.fn()
    render(
      <TablePagination position="top" list={{ totalElements: 95, totalPages: 4 }} page={2} pageSize={30} itemWords={['entry', 'entries']} onPageChange={() => {}} onPageSizeChange={onPageSizeChange} />,
    )

    expect(screen.getByText(/Showing/)).toHaveTextContent('Showing 31–60 of 95 entries')
    const user = userEvent.setup()
    await user.click(screen.getByLabelText('Rows per page'))
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['10', '20', '30', '50'])
    await user.click(screen.getByRole('option', { name: '50' }))
    expect(onPageSizeChange).toHaveBeenCalledWith(50)
  })

  it('shows only the count above a short list and nothing below it', () => {
    const props = { list: { totalElements: 4, totalPages: 1 }, page: 1, pageSize: 20, itemWords: ['order', 'orders'] as [string, string], onPageChange: () => {}, onPageSizeChange: () => {} }
    const { container } = render(<TablePagination position="bottom" {...props} />)
    expect(container).toBeEmptyDOMElement()

    render(<TablePagination position="top" {...props} />)
    expect(screen.getByText('4 orders')).toBeInTheDocument()
    expect(screen.queryByLabelText('Rows per page')).not.toBeInTheDocument()
  })
})

describe('Page controls of a list', () => {
  it('above and below the table work together: the page size and the page live in the URL', async () => {
    logInAs('admin@grocery.com')
    const { user, router } = renderApp('/admin/products')

    const top = await screen.findByRole('navigation', { name: 'Pages, above the list' }, { timeout: 10_000 })
    const bottom = screen.getByRole('navigation', { name: 'Pages, below the list' })
    // "Showing <strong>1–20</strong> of ...": the whole sentence of both rows
    const summaries = () => screen.getAllByText(/^Showing/).map((summary) => summary.textContent)
    expect(summaries()).toEqual(['Showing 1–20 of 110 products', 'Showing 1–20 of 110 products'])

    // A new page size goes back to page 1 and changes both rows
    await chooseOption(user, screen.getAllByLabelText('Rows per page')[0], '50')
    expect(router.state.location.search).toBe('?size=50')
    await waitFor(() => expect(summaries()).toEqual(['Showing 1–50 of 110 products', 'Showing 1–50 of 110 products']))

    // The page buttons below the table move the rows above it too
    await user.click(within(bottom).getByRole('button', { name: 'Page 2' }))
    expect(router.state.location.search).toBe('?size=50&page=2')
    await waitFor(() => expect(summaries()).toEqual(['Showing 51–100 of 110 products', 'Showing 51–100 of 110 products']))
    expect(within(top).getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page')
  })
})

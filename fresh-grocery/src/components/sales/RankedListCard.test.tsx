import { screen, within } from '@testing-library/react'
import { ShoppingBasket } from 'lucide-react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { RankedListCard, type RankedRow } from './RankedListCard'

const BEST_SELLERS: RankedRow[] = [
  { id: 1, label: 'Tomato', to: '/products/1', detail: '13 sold', sales: 468, rankedBy: 13 },
  { id: 2, label: 'Amul Cheese Slices', to: '/products/2', detail: '10 sold', sales: 1330, rankedBy: 10 },
]

function renderCard(rows: RankedRow[]) {
  renderWithProviders(<RankedListCard title="Best sellers" subtitle="By units sold" icon={ShoppingBasket} tone="green" rows={rows} error={null} onRetry={vi.fn()} />)
}

describe('RankedListCard', () => {
  it('links every line, compares the bars by what the list is ranked by, and adds up the sales', () => {
    renderCard(BEST_SELLERS)
    const lines = within(screen.getByRole('list', { name: 'Best sellers' })).getAllByRole('listitem')
    expect(lines[0]).toHaveTextContent('1Tomato13 sold₹468')
    expect(within(lines[1]).getByRole('link', { name: 'Amul Cheese Slices' })).toHaveAttribute('href', '/products/2')

    // Ranked by units: 13 and 10 sold, although the second one sold for more
    const bar = (line: HTMLElement) => line.querySelector('div[aria-hidden="true"] > div')
    expect(bar(lines[0])).toHaveStyle({ width: '100%' })
    expect(bar(lines[1])).toHaveStyle({ width: `${(10 / 13) * 100}%` })

    expect(screen.getByText('All 2 together').nextElementSibling).toHaveTextContent('₹1,798')
  })

  it('says when there are no sales yet', () => {
    renderCard([])
    expect(screen.getByText('No sales in this period yet.')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Best sellers' })).not.toBeInTheDocument()
  })
})

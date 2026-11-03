import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { salesDay } from '../../test/sales'
import { SalesPerDayCard } from './SalesPerDayCard'

const DAYS = [salesDay('2026-10-26', 0), salesDay('2026-10-27', 300, 2), salesDay('2026-10-28', 500, 3), salesDay('2026-10-29', 120)]

describe('SalesPerDayCard', () => {
  it('names the period and gives the quick facts above the chart', () => {
    render(<SalesPerDayCard days={DAYS} />)
    expect(screen.getByText('26 Oct – 29 Oct 2026 · 4 days')).toBeInTheDocument()

    const fact = (label: string) => screen.getByText(label, { selector: 'p' }).parentElement!
    expect(fact('Daily average')).toHaveTextContent('₹230over 4 days')
    expect(fact('Best day')).toHaveTextContent('₹500Wed, 28 Oct')
    expect(fact('Days with sales')).toHaveTextContent('3 of 4')
    expect(fact('Last day')).toHaveTextContent('₹120Thu, 29 Oct · down ₹380')
    expect(within(screen.getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem')).toHaveLength(4)
  })

  it('shows the numbers as a table with the best day, the changes and the totals', async () => {
    const user = userEvent.setup()
    render(<SalesPerDayCard days={DAYS} />)
    const toggle = screen.getByRole('button', { name: 'Show the numbers' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()

    await user.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(toggle).toHaveAccessibleName('Hide the numbers')
    const rows = within(screen.getByRole('table', { name: 'Sales per day' })).getAllByRole('row')
    expect(rows).toHaveLength(6) // header, 4 days, total
    expect(rows[1]).toHaveTextContent('Mon, 26 Oct00₹0–no day before') // "–" for the eyes, "no day before" for screen readers
    expect(rows[2]).toHaveTextContent('Tue, 27 Oct20₹300up ₹300')
    expect(rows[3]).toHaveTextContent('Wed, 28 OctBest30₹500up ₹200')
    expect(rows[5]).toHaveTextContent('Total60₹920')

    await user.click(toggle)
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('says why there is no chart when nothing was sold', () => {
    render(<SalesPerDayCard days={[{ ...salesDay('2026-10-26', 0), orders: 2, cancelledOrders: 2 }, salesDay('2026-10-27', 0)]} />)
    expect(screen.getByText('No sales in this period')).toBeInTheDocument()
    expect(screen.getByText('The orders of these days were cancelled or are not confirmed yet.')).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Sales per day' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show the numbers' })).not.toBeInTheDocument()
  })

  it('leaves out the quick facts for one day, and says when another period is loading', () => {
    render(<SalesPerDayCard days={[salesDay('2026-10-29', 120)]} updating />)
    expect(screen.getByText('29 Oct 2026 · 1 day')).toBeInTheDocument()
    expect(screen.queryByText('Daily average')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Updating…')
  })
})

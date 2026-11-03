import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { SalesDay } from '../../api/types'
import { logInAs, renderApp } from '../../test/render'
import { OrderOutcomeCard } from '../sales/OrderOutcomeCard'
import { AreaChart } from './AreaChart'
import { labelStep } from './chartScale'
import { DonutChart } from './DonutChart'

const day = (date: string, orders: number, salesOrders: number, cancelledOrders: number, sales: number): SalesDay => ({
  date,
  orders,
  salesOrders,
  cancelledOrders,
  itemsTotal: sales,
  couponDiscounts: 0,
  deliveryFees: 0,
  sales,
})

describe('AreaChart', () => {
  it('gives every day a focusable point that says its amount and count', () => {
    render(
      <AreaChart
        label="Sales per day"
        valueName="Sales"
        countWords={['order', 'orders']}
        days={[
          { date: '2026-10-25', value: 0, count: 0 },
          { date: '2026-10-26', value: 300.5, count: 3 },
          { date: '2026-10-27', value: 196, count: 1 },
        ]}
      />,
    )

    const points = within(screen.getByRole('list', { name: 'Sales per day' })).getAllByRole('listitem')
    expect(points.map((point) => point.getAttribute('aria-label'))).toEqual([
      '25 Oct: ₹0 from 0 orders',
      '26 Oct: ₹300.50 from 3 orders',
      '27 Oct: ₹196 from 1 order',
    ])
    expect(points[1]).toHaveAttribute('tabindex', '0')
  })

  it('draws one day as a flat line across the chart', () => {
    const { container } = render(<AreaChart label="Sales per day" valueName="Sales" countWords={['order', 'orders']} days={[{ date: '2026-10-27', value: 196, count: 1 }]} />)
    // ₹196 on a ₹200 scale: 2% from the top
    expect(container.querySelector('polyline')).toHaveAttribute('points', '0,2 100,2')
  })

  it('labels every day of a week, and about 8 days of a long period', () => {
    expect(labelStep(7)).toBe(1)
    expect(labelStep(30)).toBe(4)
    expect(labelStep(90)).toBe(12)
    expect(labelStep(366)).toBe(46)
  })
})

describe('DonutChart', () => {
  it('names every part with its number and share, and shows a focused part in the middle', async () => {
    const user = userEvent.setup()
    render(
      <DonutChart
        label="Payments by status"
        countWords={['payment', 'payments']}
        parts={[
          { key: 'paid', label: 'Paid', value: 3, tone: 'good', detail: '₹500' },
          { key: 'open', label: 'Not paid yet', value: 1, tone: 'wait' },
          { key: 'refunded', label: 'Refunded', value: 0, tone: 'info' },
        ]}
      />,
    )

    const legend = screen.getByRole('list', { name: 'Payments by status' })
    const [paid, open, refunded] = within(legend).getAllByRole('listitem')
    expect(paid).toHaveTextContent('Paid375%₹500')
    expect(open).toHaveTextContent('Not paid yet125%')
    expect(refunded).toHaveTextContent('Refunded00%')
    expect(screen.getByText('payments')).toBeInTheDocument()

    await user.tab()
    expect(paid).toHaveFocus()
    expect(screen.getByText('75% · Paid')).toBeInTheDocument()
  })
})

describe('OrderOutcomeCard', () => {
  it('splits the period\'s orders into sales, not confirmed yet and stopped ones', () => {
    render(<OrderOutcomeCard days={[day('2026-10-26', 5, 3, 1, 300), day('2026-10-27', 2, 1, 0, 100)]} />)

    const legend = screen.getByRole('list', { name: 'Orders in this period by outcome' })
    expect(within(legend).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      'Counted as sales457%',
      'Not confirmed yet229%',
      'Cancelled, rejected or not delivered114%',
    ])
  })

  it('says so when the period has no orders', () => {
    render(<OrderOutcomeCard days={[day('2026-10-27', 0, 0, 0, 0)]} />)
    expect(screen.getByText('No orders in this period.')).toBeInTheDocument()
  })
})

describe('Your spending', () => {
  it('shows the last 6 months on My orders', async () => {
    logInAs()
    renderApp('/orders')
    expect(await screen.findByRole('heading', { name: 'Your spending' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Spent per month' })).getAllByRole('listitem')).toHaveLength(6)
  })
})

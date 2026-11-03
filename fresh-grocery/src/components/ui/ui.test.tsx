import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { buttonClasses, dangerIconButtonClasses } from './buttonClasses'
import { ConfirmDialog } from './ConfirmDialog'
import { Pagination } from './Pagination'
import { Price } from './Price'
import { QuantityStepper } from './QuantityStepper'
import { StarRating } from './StarRating'

describe('Price', () => {
  it('crosses out the original price only when there is a discount', () => {
    const { rerender } = render(<Price sellingPrice={36} price={40} />)
    expect(screen.getByText('₹36')).toBeInTheDocument()
    expect(screen.getByText('₹40')).toHaveClass('line-through')

    rerender(<Price sellingPrice={35} price={35} />)
    expect(screen.queryByText('instead of', { exact: false })).not.toBeInTheDocument()
  })
})

describe('StarRating', () => {
  it('describes the rating for screen readers', () => {
    render(<StarRating value={4.5} count={2} />)
    expect(screen.getByRole('img', { name: 'Rated 4.5 out of 5 from 2 reviews' })).toBeInTheDocument()
  })

  it('says when there are no reviews', () => {
    render(<StarRating value={null} count={0} />)
    expect(screen.getByRole('img', { name: 'No reviews yet' })).toBeInTheDocument()
  })
})

function StepperWithState({ max }: { max: number }) {
  const [value, setValue] = useState(1)
  return <QuantityStepper value={value} onChange={setValue} max={max} label="Quantity of Tomato" />
}

describe('QuantityStepper', () => {
  it('counts up and down between min and max', async () => {
    const user = userEvent.setup()
    render(<StepperWithState max={2} />)
    const decrease = screen.getByRole('button', { name: 'Decrease quantity' })
    const increase = screen.getByRole('button', { name: 'Increase quantity' })

    expect(decrease).toBeDisabled() // already at the minimum (1)
    await user.click(increase)
    expect(screen.getByRole('group', { name: 'Quantity of Tomato' })).toHaveTextContent('2')
    expect(increase).toBeDisabled() // at the maximum (2)

    await user.click(decrease)
    expect(screen.getByRole('group', { name: 'Quantity of Tomato' })).toHaveTextContent('1')
  })
})

describe('Pagination', () => {
  it('marks the current page and reports clicks', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination page={1} totalPages={3} onPageChange={onPageChange} />)

    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Page 3' }))
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(onPageChange.mock.calls).toEqual([[3], [2]])
  })

  it('is hidden when everything fits on one page', () => {
    const { container } = render(<Pagination page={1} totalPages={1} onPageChange={() => {}} />)
    expect(container).toBeEmptyDOMElement()
  })
})

describe('ConfirmDialog', () => {
  function renderDialog() {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    render(<ConfirmDialog open title="Empty your cart?" message="All products will be removed." confirmLabel="Empty cart" onConfirm={onConfirm} onCancel={onCancel} />)
    return { onConfirm, onCancel }
  }

  it('focuses Cancel, and confirms only on the confirm button', async () => {
    const user = userEvent.setup()
    const { onConfirm, onCancel } = renderDialog()

    expect(screen.getByRole('alertdialog', { name: 'Empty your cart?' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Empty cart' }))
    expect(onConfirm).toHaveBeenCalledOnce()
    expect(onCancel).not.toHaveBeenCalled()
  })

  it('cancels on Escape', async () => {
    const user = userEvent.setup()
    const { onCancel } = renderDialog()
    await user.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('makes the confirm button red for a destructive action; Cancel only closes, so it stays neutral', () => {
    render(<ConfirmDialog open danger title="Delete this address?" message="It is removed for good." confirmLabel="Delete" onConfirm={() => {}} onCancel={() => {}} />)

    expect(screen.getByRole('button', { name: 'Delete' })).toHaveClass('bg-danger', 'text-white')
    expect(screen.getByRole('button', { name: 'Cancel' })).not.toHaveClass('bg-danger')
    expect(screen.getByRole('button', { name: 'Cancel' }).className).not.toContain('danger')
  })

  it('keeps the green confirm button for an action that is not destructive', () => {
    render(<ConfirmDialog open title="Approve this store?" message="The store can sell." confirmLabel="Approve" onConfirm={() => {}} onCancel={() => {}} />)

    expect(screen.getByRole('button', { name: 'Approve' })).toHaveClass('bg-brand')
  })
})

describe('danger styles', () => {
  it('has one red for every destructive button, with hover, active and a red focus ring', () => {
    expect(buttonClasses('danger')).toContain('bg-danger')
    expect(buttonClasses('danger')).toContain('hover:bg-danger-hover')
    expect(buttonClasses('danger')).toContain('focus-visible:outline-danger')
    expect(buttonClasses('danger-outline')).toContain('text-danger-text')
    expect(buttonClasses('danger-outline')).toContain('hover:bg-danger-light')
    expect(buttonClasses('danger-outline')).toContain('focus-visible:outline-danger')
    expect(dangerIconButtonClasses('size-9')).toContain('text-danger-text')
    expect(dangerIconButtonClasses('size-9')).toContain('size-9')
  })
})

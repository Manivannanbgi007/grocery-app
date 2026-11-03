import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { PaymentMethod } from '../../api/types'
import { placeOrder } from '../../mocks/mockOrders'
import { writeReview } from '../../mocks/mockRatings'
import { logInAs, renderApp } from '../../test/render'

/** customer1 (Priya, user 4) orders her seed cart (₹231) to her home address; the mock gives it id 1001. */
function priyaOrders(paymentMethod: PaymentMethod) {
  return placeOrder(4, { addressId: 1, paymentMethod, note: null })
}

describe('Order page', () => {
  it('shows a cash-on-delivery order with its progress, address, store and history', async () => {
    priyaOrders('COD')
    logInAs()
    renderApp('/orders/1001')

    expect(await screen.findByRole('heading', { level: 1, name: 'Order #1001' })).toBeInTheDocument()
    // The summary at the top: status, payment and total at a glance
    const summary = screen.getByRole('region', { name: 'Order #1001' })
    expect(within(summary).getByText('Confirmed')).toBeInTheDocument()
    expect(within(summary).getByText('Pay on delivery')).toBeInTheDocument()
    expect(within(summary).getByText('₹231')).toBeInTheDocument()

    expect(screen.getByRole('heading', { name: 'Order confirmed' })).toBeInTheDocument()
    // The steps so far have their time; the steps still to come have none
    const progress = screen.getByRole('list', { name: 'Order progress' })
    const current = within(progress).getByRole('listitem', { current: 'step' })
    expect(current).toHaveTextContent('Confirmed')
    expect(within(current).getByRole('time')).toBeInTheDocument()
    expect(within(within(progress).getAllByRole('listitem').at(-1)!).queryByRole('time')).not.toBeInTheDocument()

    expect(screen.getByText('Cash on delivery: pay ₹231 at the door')).toBeInTheDocument()
    expect(screen.getByText('Flat 302, Lakeview Apartments')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Call the store: 9000000003' })).toHaveAttribute('href', 'tel:9000000003')
    expect(screen.getAllByRole('listitem').filter((item) => item.closest('ul')?.getAttribute('aria-label') === 'Products in this order')).toHaveLength(3)

    // The full history is folded under the progress
    const history = screen.getByText('Full history').closest('details')!
    expect(history).not.toHaveAttribute('open')
    expect(within(history).getByText('Order placed')).toBeInTheDocument()
    expect(within(history).getByText('Confirmed - pay cash on delivery')).toBeInTheDocument()
  })

  it('lets the customer pay an online order with the test gateway', async () => {
    priyaOrders('ONLINE')
    logInAs()
    const { user } = renderApp('/orders/1001')

    expect(await screen.findByText(/Your items are reserved/)).toBeInTheDocument()
    expect(screen.getByRole('timer')).toHaveTextContent(/^1[45]:\d\d$/)
    await user.click(screen.getByRole('button', { name: 'Pay ₹231' }))

    expect(await screen.findByText('Payment of ₹231 received')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Order confirmed' })).toBeInTheDocument()
    expect(screen.getByText('Paid ₹231 online')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pay ₹231' })).not.toBeInTheDocument()
  })

  it('cancels the order when the bank declines the payment', async () => {
    priyaOrders('ONLINE')
    logInAs()
    const { user } = renderApp('/orders/1001')

    await user.click(await screen.findByRole('button', { name: 'Decline (test)' }))
    expect(await screen.findByRole('heading', { name: 'Order cancelled' })).toBeInTheDocument()
    expect(screen.getByText('Payment failed: The bank declined the payment (mock)', { selector: 'section > p' })).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Order progress' })).not.toBeInTheDocument()
    // A stopped order: the history is open, it says what happened
    expect(screen.getByText('Full history').closest('details')).toHaveAttribute('open')
  })

  it("does not show another customer's order", async () => {
    priyaOrders('COD')
    logInAs('customer2@grocery.com')
    renderApp('/orders/1001')
    expect(await screen.findByRole('heading', { name: 'We could not find this order' })).toBeInTheDocument()
  })

  it('treats an address that is not an order number as not found', async () => {
    logInAs()
    renderApp('/orders/abc')
    expect(await screen.findByRole('heading', { name: 'We could not find this order' })).toBeInTheDocument()
  })
})

/* Order 901 is customer1's delivered mock order (Tomato ×2, milk, eggs; delivered by Arjun Singh). */
describe('After placing an order', () => {
  it('cancels a confirmed order, with a reason', async () => {
    priyaOrders('COD')
    logInAs()
    const { user } = renderApp('/orders/1001')

    await user.click(await screen.findByRole('button', { name: 'Cancel order' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Cancel order #1001?' })
    await user.type(within(dialog).getByLabelText('Reason (optional)'), 'Ordered by mistake')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel order' }))

    expect(await screen.findByText('Order #1001 is cancelled.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Order cancelled' })).toBeInTheDocument()
    expect(screen.getByText('Ordered by mistake', { selector: 'section > p' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
    expect(screen.getByText('Nothing to pay')).toBeInTheDocument()
  })

  it('refunds a paid online order that is cancelled', async () => {
    priyaOrders('ONLINE')
    logInAs()
    const { user } = renderApp('/orders/1001')
    await user.click(await screen.findByRole('button', { name: 'Pay ₹231' }))
    await screen.findByRole('heading', { name: 'Order confirmed' })

    await user.click(screen.getByRole('button', { name: 'Cancel order' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Cancel order #1001?' })
    expect(dialog).toHaveTextContent('the money you paid goes back to you')
    await user.click(within(dialog).getByRole('button', { name: 'Cancel order' }))

    expect(await screen.findByText('Order #1001 is cancelled. Your refund is on its way.')).toBeInTheDocument()
    expect(screen.getByText('₹231 refunded')).toBeInTheDocument()
  })

  it('downloads the invoice of a delivered order', async () => {
    let savedAs = ''
    // jsdom has no object URLs and does not download files: catch the click on the download link instead
    URL.createObjectURL = vi.fn(() => 'blob:invoice')
    URL.revokeObjectURL = vi.fn()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      savedAs = this.download
    })
    logInAs()
    const { user } = renderApp('/orders/901')

    await user.click(await screen.findByRole('button', { name: 'Download invoice' }))
    await waitFor(() => expect(savedAs).toBe('invoice-INV-000901.pdf'))
    // A delivered order can no longer be cancelled
    expect(screen.queryByRole('button', { name: 'Cancel order' })).not.toBeInTheDocument()
  })

  it('has no invoice before the store accepted the order', async () => {
    priyaOrders('COD')
    logInAs()
    renderApp('/orders/1001')
    await screen.findByRole('heading', { name: 'Order confirmed' })
    expect(screen.queryByRole('button', { name: 'Download invoice' })).not.toBeInTheDocument()
  })

  it('lets the customer rate the delivery once', async () => {
    logInAs()
    const { user } = renderApp('/orders/901')

    await user.click(await screen.findByRole('button', { name: 'Send rating' }))
    expect(screen.getByText('Please choose 1 to 5 stars.')).toBeInTheDocument()

    const ratingGroup = screen.getByRole('group', { name: 'How was the delivery by Arjun Singh?' })
    await user.click(within(ratingGroup).getByRole('radio', { name: '4 stars' }))
    await user.type(screen.getByLabelText('Anything to add? (optional)'), 'On time')
    await user.click(screen.getByRole('button', { name: 'Send rating' }))

    expect(await screen.findByText('You rated the delivery by Arjun Singh: 4 out of 5.')).toBeInTheDocument()
    expect(screen.getByText('“On time”')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Send rating' })).not.toBeInTheDocument()
  })

  it('lets the customer review a product of the delivered order', async () => {
    logInAs()
    const { user, router } = renderApp('/orders/901')

    await user.click(await screen.findByRole('button', { name: 'Review Tomato' }))
    const ratingGroup = screen.getByRole('group', { name: 'Your rating for Tomato' })
    await user.click(within(ratingGroup).getByRole('radio', { name: '5 stars' }))
    await user.type(screen.getByLabelText('Your review (optional)'), 'Very fresh')
    await user.click(screen.getByRole('button', { name: 'Publish review' }))
    expect(await screen.findByText('Thank you! Your review of Tomato is published.')).toBeInTheDocument()

    // The product page shows the new review (in its Reviews tab)
    await router.navigate('/products/1')
    await user.click(await screen.findByRole('tab', { name: /^Reviews/ }))
    expect(await screen.findByText('Very fresh')).toBeInTheDocument()
  })

  it('says when the product was reviewed for this order already', async () => {
    writeReview(4, 1, { orderId: 901, rating: 4, comment: null })
    logInAs()
    const { user } = renderApp('/orders/901')

    await user.click(await screen.findByRole('button', { name: 'Review Tomato' }))
    await user.click(within(screen.getByRole('group', { name: 'Your rating for Tomato' })).getByRole('radio', { name: '3 stars' }))
    await user.click(screen.getByRole('button', { name: 'Publish review' }))
    expect(await screen.findByText('You have already reviewed Tomato for this order.')).toBeInTheDocument()
  })
})

import { screen, within } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { fail, ok } from '../../mocks/handlers/respond'
import { server } from '../../mocks/server'
import { logInAs, renderApp } from '../../test/render'

/** Opens the checkout logged in as customer1 (Priya): seed cart Tomato ×2, Milk ×3, Eggs ×1 (₹201 + ₹30 delivery). */
async function openCheckout() {
  logInAs()
  const rendered = renderApp('/checkout')
  await screen.findByRole('list', { name: 'Products in your order' })
  return rendered
}

const addressRadio = (name: RegExp) => screen.getByRole('radio', { name })

describe('Checkout page', () => {
  it('sends a guest to the login page first', async () => {
    const { router } = renderApp('/checkout')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?next=%2Fcheckout')
  })

  it('shows the addresses (default chosen), the payment methods and the order', async () => {
    await openCheckout()
    expect(addressRadio(/^Home/)).toBeChecked()
    expect(addressRadio(/^Work/)).not.toBeChecked()
    expect(screen.getByRole('radio', { name: /Cash on delivery/ })).toBeChecked()
    expect(screen.getAllByRole('listitem').filter((item) => item.closest('ul')?.getAttribute('aria-label') === 'Products in your order')).toHaveLength(3)
    const totals = screen.getByRole('heading', { name: 'Order total' }).parentElement!
    expect(within(totals).getByText('Total to pay').nextSibling).toHaveTextContent('₹231')
  })

  it('places a cash-on-delivery order and shows it', async () => {
    const { user, router } = await openCheckout()
    await user.type(screen.getByLabelText('3. Note for the store (optional)'), 'Please ring the bell twice')
    await user.click(screen.getByRole('button', { name: 'Place order' }))

    expect(await screen.findByRole('heading', { name: 'Thank you for your order!' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders/1001')
    expect(screen.getByText(/Please keep ₹231 ready in cash/)).toBeInTheDocument()
    // The mock confirms a COD order as soon as it is read again, like the backend does a moment later
    expect(screen.getByRole('heading', { name: 'Order confirmed' })).toBeInTheDocument()
    expect(screen.getByText('Your note: “Please ring the bell twice”')).toBeInTheDocument()
    // The cart was emptied by the order
    expect(await screen.findByRole('link', { name: 'Cart, 0 items' })).toBeInTheDocument()
  })

  it('says when the store does not deliver to the chosen address', async () => {
    const { user } = await openCheckout()
    await user.click(addressRadio(/^Work/))
    await user.click(screen.getByRole('button', { name: 'Place order' }))
    expect(await screen.findByText('Fresh Mart does not deliver to PIN code 560034. Please choose another address.')).toBeInTheDocument()
  })

  it('adds a new address and uses it', async () => {
    const { user } = await openCheckout()
    await user.click(screen.getByRole('button', { name: 'Add a new address' }))

    // Name and mobile number are filled in from the account
    expect(screen.getByLabelText('Full name')).toHaveValue('Priya Sharma')
    await user.click(screen.getByRole('button', { name: 'Save and use this address' }))
    expect(screen.getByText('Please enter the flat, house or building.')).toBeInTheDocument()
    expect(screen.getByText('Please enter the 6-digit PIN code.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Flat, house no., building'), '12, Rose Villa')
    await user.type(screen.getByLabelText('City'), 'Bengaluru')
    await user.type(screen.getByLabelText('State'), 'Karnataka')
    await user.type(screen.getByLabelText('PIN code'), '560071')
    await user.click(screen.getByRole('button', { name: 'Save and use this address' }))

    expect(await screen.findByRole('radio', { name: /12, Rose Villa/ })).toBeChecked()
    expect(screen.queryByRole('button', { name: 'Save and use this address' })).not.toBeInTheDocument()
  })

  it('lists what must be fixed when the cart cannot be ordered', async () => {
    server.use(
      http.get('*/api/v1/cart/checkout', () =>
        ok(
          {
            customerUserId: 4,
            storeId: 1,
            items: [{ productId: 1, productName: 'Tomato', unit: 'KG', unitQuantity: 1, imageUrl: null, price: 40, sellingPrice: 36, taxPercentage: 0, quantity: 2, lineTotal: 72 }],
            couponCode: null,
            totals: { itemsTotal: 72, productSavings: 8, couponDiscount: 0, deliveryFee: 30, payableAmount: 102, taxIncluded: 0, minimumOrderValue: 99, minimumOrderMet: false },
            readyForCheckout: false,
            problems: ['The minimum order value is 99.00 (add 27.00 more)'],
          },
          'Checkout summary prepared',
        ),
      ),
    )
    logInAs()
    renderApp('/checkout')
    expect(await screen.findByText('Your cart cannot be ordered yet:')).toBeInTheDocument()
    expect(screen.getByText('The minimum order value is 99.00 (add 27.00 more)')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to cart' })).toHaveAttribute('href', '/cart')
  })

  it('shows the problems the backend finds while placing the order', async () => {
    const { user } = await openCheckout()
    server.use(
      http.post('*/api/v1/orders', ({ request }) =>
        fail(409, 'Your cart cannot be ordered yet. Please fix the problems listed and try again.', request, [{ field: null, message: 'Farm Eggs is out of stock' }]),
      ),
    )
    await user.click(screen.getByRole('button', { name: 'Place order' }))
    expect(await screen.findByText('Farm Eggs is out of stock')).toBeInTheDocument()
  })

  it('tells a customer with an empty cart to shop first', async () => {
    logInAs('customer2@grocery.com') // Rahul: no cart
    renderApp('/checkout')
    expect(await screen.findByRole('heading', { name: 'Your cart is empty' })).toBeInTheDocument()
  })
})

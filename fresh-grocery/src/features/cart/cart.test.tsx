import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { logInAs, renderApp } from '../../test/render'

/** Opens the cart page logged in as customer1 (Priya), whose seed cart has Tomato ×2, Milk ×3 and Eggs ×1. */
async function openCart() {
  logInAs()
  const rendered = renderApp('/cart')
  await screen.findByRole('list', { name: 'Products in your cart' })
  return rendered
}

const totals = () => screen.getByRole('heading', { name: 'Cart totals' }).parentElement!
const row = (name: string) => screen.getByRole('link', { name }).closest('li')!

describe('Cart page', () => {
  it('sends a guest to the login page first', async () => {
    const { router } = renderApp('/cart')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(router.state.location.search).toBe('?next=%2Fcart')
  })

  it('shows the cart with the totals from the backend', async () => {
    await openCart()
    expect(screen.getAllByRole('listitem').filter((item) => item.closest('ul')?.getAttribute('aria-label') === 'Products in your cart')).toHaveLength(3)
    expect(screen.getByText(/in your cart/)).toHaveTextContent('There are 3 products in your cart from Fresh Mart.')
    expect(within(totals()).getByText('Items total').nextSibling).toHaveTextContent('₹201')
    expect(within(totals()).getByText('Delivery').nextSibling).toHaveTextContent('₹30')
    expect(within(totals()).getByText('Add ₹98 more for free delivery')).toBeInTheDocument()
    expect(within(totals()).getByText('Total to pay').nextSibling).toHaveTextContent('₹231')
    // The header counter shows the number of different products
    expect(screen.getByRole('link', { name: 'Cart, 3 items' })).toBeInTheDocument()
  })

  it('changes a quantity and removes a product', async () => {
    const { user } = await openCart()

    await user.click(within(row('Farm Eggs')).getByRole('button', { name: 'Increase quantity' }))
    await waitFor(() => expect(within(row('Farm Eggs')).getByText('₹96')).toBeInTheDocument())
    expect(within(totals()).getByText('Items total').nextSibling).toHaveTextContent('₹249')

    await user.click(screen.getByRole('button', { name: 'Remove Farm Eggs from cart' }))
    await waitFor(() => expect(screen.queryByRole('link', { name: 'Farm Eggs' })).not.toBeInTheDocument())
    expect(screen.getByRole('link', { name: 'Cart, 2 items' })).toBeInTheDocument()
  })

  it('applies a coupon from the suggestions and shows the saving', async () => {
    const { user } = await openCart()
    await user.click(await screen.findByRole('button', { name: /Apply coupon SAVE10/ }))

    expect(await within(totals()).findByText('Coupon SAVE10')).toBeInTheDocument()
    expect(within(totals()).getByText('Coupon SAVE10').nextSibling).toHaveTextContent('−₹20.10')
    expect(within(totals()).getByText('Total to pay').nextSibling).toHaveTextContent('₹210.90')
  })

  it('shows why a typed coupon cannot be used', async () => {
    const { user } = await openCart()
    await user.type(screen.getByLabelText('Coupon code'), 'welcome50')
    await user.click(screen.getByRole('button', { name: 'Apply coupon' }))
    expect(await screen.findByText('Coupon WELCOME50 needs an items total of at least 299.00 (add 98.00 more)')).toBeInTheDocument()
  })

  it('leads to the checkout page', async () => {
    const { user, router } = await openCart()
    await user.click(screen.getByRole('link', { name: 'Proceed to checkout' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Checkout' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/checkout')
  })

  it('leaves out the shop banner and the feature cards on the cart and the checkout, not on other pages', async () => {
    const promotion = () => [screen.queryByRole('heading', { name: /Stay home/ }), screen.queryByRole('region', { name: 'Why shop with us' })]
    const { user } = await openCart()
    expect(promotion()).toEqual([null, null])

    await user.click(screen.getByRole('link', { name: 'Proceed to checkout' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Checkout' })).toBeInTheDocument()
    expect(promotion()).toEqual([null, null])

    await user.click(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link', { name: 'Home' }))
    expect(await screen.findByRole('heading', { name: /Stay home/ })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Why shop with us' })).toBeInTheDocument()
  })

  it('empties the cart after asking', async () => {
    const { user } = await openCart()
    await user.click(screen.getByRole('button', { name: 'Empty cart' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Empty your cart?' })
    await user.click(within(dialog).getByRole('button', { name: 'Empty cart' }))

    expect(await screen.findByRole('heading', { name: 'Your cart is empty' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cart, 0 items' })).toBeInTheDocument()
  })

  it('shares the cart as a WhatsApp message', async () => {
    await openCart()
    const link = screen.getByRole('link', { name: 'Share on WhatsApp' })
    const text = new URL(link.getAttribute('href')!).searchParams.get('text')
    expect(text).toContain('My Fresh Grocery cart (Fresh Mart):')
    expect(text).toContain('- 2 × Tomato (1 kg): ₹72')
    expect(text).toContain('Total to pay: ₹231')
  })
})

describe('Add to cart', () => {
  it('adds a product for a logged-in customer and updates the header', async () => {
    logInAs()
    const { user } = renderApp('/products/2') // Onion, Fresh Mart: same store as the seed cart
    await user.click(await screen.findByRole('button', { name: 'Add Onion to cart' }))
    expect(await screen.findByText('Onion added to your cart')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cart, 4 items' })).toBeInTheDocument()
  })

  it('asks before starting a new cart for a product of another store', async () => {
    logInAs()
    const { user } = renderApp('/products/11') // Organic Spinach, Green Basket
    await user.click(await screen.findByRole('button', { name: 'Add Organic Spinach to cart' }))

    const dialog = await screen.findByRole('alertdialog', { name: 'Start a new cart?' })
    expect(dialog).toHaveTextContent('Your cart has products from Fresh Mart.')
    await user.click(within(dialog).getByRole('button', { name: 'Empty cart and add' }))

    expect(await screen.findByText('Organic Spinach added to your cart')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cart, 1 item' })).toBeInTheDocument()
  })

  it('adds the chosen quantity from the product page', async () => {
    logInAs('customer2@grocery.com') // Rahul: no cart yet
    const { user } = renderApp('/products/5')
    await user.click(await screen.findByRole('button', { name: 'Increase quantity' }))
    await user.click(screen.getByRole('button', { name: 'Add Apple Shimla to cart' }))
    expect(await screen.findByText('2 × Apple Shimla added to your cart')).toBeInTheDocument()
  })
})

import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { SaleToast } from '../../components/layout/SaleToast'
import { renderApp, renderWithProviders } from '../../test/render'

/** The <section> that a heading belongs to. */
async function section(name: string): Promise<HTMLElement> {
  const heading = await screen.findByRole('heading', { name, level: 2 })
  return heading.closest('section')!
}

describe('Home page', () => {
  it('shows the live banners as slides, with dots to switch', async () => {
    const { user } = renderApp('/')

    expect(await screen.findByRole('heading', { level: 1, name: 'Fresh vegetables, delivered today' })).toBeInTheDocument()
    const carousel = screen.getByRole('region', { name: 'Offers and news' })
    // The banner links to "/categories/1" in the backend; here that is the shop filtered on category 1
    expect(within(carousel).getByRole('link', { name: 'Shop now' })).toHaveAttribute('href', '/shop?category=1')

    await user.click(within(carousel).getByRole('button', { name: /Show slide 2/ }))
    expect(within(carousel).getByRole('heading', { level: 1, name: 'WELCOME50: 50 off your first order' })).toBeInTheDocument()
    expect(within(carousel).getByRole('link', { name: 'Shop now' })).toHaveAttribute('href', '/#offers')
  })

  it('shows the featured categories with their product counts', async () => {
    renderApp('/')
    const categories = await section('Featured Categories')
    const fruits = await within(categories).findByRole('link', { name: /^Fruits/ })
    expect(fruits).toHaveAttribute('href', '/shop?category=2')
    await waitFor(() => expect(fruits).toHaveTextContent('8 items'))
  })

  it('shows twelve popular products, so every row of small cards is full', async () => {
    renderApp('/')
    const popular = await section('Popular Products')
    expect(await within(popular).findAllByRole('article')).toHaveLength(12)
  })

  it('narrows the popular products to one category', async () => {
    const { user } = renderApp('/')
    const popular = await section('Popular Products')
    await within(popular).findAllByRole('article')

    await user.click(within(popular).getByRole('button', { name: 'Fruits' }))
    expect(within(popular).getByRole('button', { name: 'Fruits' })).toHaveAttribute('aria-pressed', 'true')
    await waitFor(() => {
      const names = within(popular)
        .getAllByRole('article')
        .map((card) => within(card).getByRole('heading').textContent)
      expect(names).toContain('Strawberry')
      expect(names).not.toContain('Tomato')
    })
  })

  it('shows the four biggest discounts in stock as deals', async () => {
    renderApp('/')
    const deals = await section('Deals Of The Day')
    const cards = await within(deals).findAllByRole('article')
    expect(cards.map((card) => within(card).getByRole('heading').textContent)).toEqual([
      'Pampers Baby Dry Pants Medium', // 20%, saves ₹259.80
      'Dabur Honey', // 20%, saves ₹45; the Pomegranate (also 20%) is out of stock
      'Drools Puppy Chicken & Egg', // 15%, saves ₹117
      'Happilo California Almonds', // 15%, saves ₹44.85
    ])
    expect(within(cards[1]).getByText('You save ₹45')).toBeInTheDocument()
  })

  it('lists the coupons of the platform and of each store, with a countdown and a copy button', async () => {
    const { user } = renderApp('/')
    const offers = await section('Offers For You')
    await waitFor(() => expect(within(offers).getAllByRole('article')).toHaveLength(4))

    expect(within(offers).getAllByText('All stores')).toHaveLength(2) // WELCOME50, SAVE10
    expect(await within(offers).findByText('Only at Fresh Mart')).toBeInTheDocument() // FRESH20
    expect(within(offers).getByText('Only at Green Basket')).toBeInTheDocument() // GREEN30
    expect(within(offers).getAllByRole('timer')[0]).toHaveAccessibleName(/^Ends in \d+ days/)

    await user.click(within(offers).getByRole('button', { name: 'Copy coupon code SAVE10' }))
    expect(await navigator.clipboard.readText()).toBe('SAVE10')
  })

  it('fills the four product columns', async () => {
    renderApp('/')
    const recent = await section('Recently added')
    const budget = await section('Budget picks')
    // Watermelon was added 1 day ago (and has the lower id of the two newest)
    expect(await within(recent).findByRole('link', { name: 'Watermelon' })).toBeInTheDocument()
    // The cheapest products in stock cost ₹20
    expect(await within(budget).findByRole('link', { name: "Lay's Classic Salted" })).toBeInTheDocument()
    expect(await section('Top rated')).toBeInTheDocument()
    expect(await section('More deals')).toBeInTheDocument()
  })
})

describe('SaleToast', () => {
  it('shows the biggest deal once per visit and can be closed', async () => {
    const { user } = renderWithProviders(<SaleToast delayMs={10} />)

    const toast = await screen.findByRole('complementary', { name: 'On sale now' })
    expect(within(toast).getByRole('link', { name: 'Pampers Baby Dry Pants Medium' })).toHaveAttribute('href', '/products/231')
    expect(toast).toHaveTextContent('You save ₹259.80')

    await user.click(within(toast).getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('complementary', { name: 'On sale now' })).not.toBeInTheDocument()
    expect(sessionStorage.getItem('fresh-grocery.sale-toast-seen')).toBe('true')
  })

  it('stays away when it was already shown in this visit', async () => {
    sessionStorage.setItem('fresh-grocery.sale-toast-seen', 'true')
    renderWithProviders(<SaleToast delayMs={10} />)
    await new Promise((resolve) => setTimeout(resolve, 100))
    expect(screen.queryByRole('complementary', { name: 'On sale now' })).not.toBeInTheDocument()
  })
})

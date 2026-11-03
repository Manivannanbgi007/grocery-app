import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Product } from '../../api/types'
import { demoProductRows, toProduct } from '../../mocks/data/products'
import { renderWithProviders } from '../../test/render'
import { Header } from '../layout/Header'
import { ProductCard } from './ProductCard'
import { productBadge } from './productBadge'

const product = (id: number): Product => toProduct(demoProductRows.find((row) => row.id === id)!)

describe('productBadge', () => {
  it('picks the most important real fact', () => {
    expect(productBadge(product(13))).toEqual({ label: 'Sold out', tone: 'neutral' }) // Pomegranate, discounted but out of stock
    expect(productBadge(product(1))).toEqual({ label: '-10%', tone: 'brand' }) // Tomato
    expect(productBadge(product(7))).toEqual({ label: 'Only 3 left', tone: 'new' }) // Amul Butter, stock 3
    expect(productBadge(product(6))).toEqual({ label: 'Top rated', tone: 'hot' }) // Milk: two 5-star reviews
    expect(productBadge(product(2))).toBeNull() // Onion: nothing special
  })
})

describe('ProductCard', () => {
  it('shows the product with its store, pack size, rating and prices', async () => {
    renderWithProviders(<ProductCard product={product(1)} />)
    const card = screen.getByRole('article')

    expect(within(card).getByRole('link', { name: 'Tomato' })).toHaveAttribute('href', '/products/1')
    expect(within(card).getByText('1 kg')).toBeInTheDocument()
    expect(within(card).getByText('-10%')).toBeInTheDocument()
    expect(within(card).getByRole('img', { name: 'Rated 4.5 out of 5 from 2 reviews' })).toBeInTheDocument()
    expect(within(card).getByText('₹36')).toBeInTheDocument()
    expect(within(card).getByText('₹40')).toHaveClass('line-through')
    // The store name comes from the stores API
    expect(await within(card).findByText('Fresh Mart')).toBeInTheDocument()
  })

  it('keeps everything but the category line in its small size', async () => {
    renderWithProviders(<ProductCard product={product(1)} size="small" />)
    const card = screen.getByRole('article')

    expect(within(card).getByRole('link', { name: 'Tomato' })).toHaveAttribute('href', '/products/1')
    expect(within(card).getByText('1 kg')).toBeInTheDocument()
    expect(within(card).getByText('-10%')).toBeInTheDocument()
    expect(within(card).getByRole('img', { name: 'Rated 4.5 out of 5 from 2 reviews' })).toBeInTheDocument()
    expect(within(card).getByText('₹36')).toBeInTheDocument()
    expect(within(card).getByText('₹40')).toHaveClass('line-through')
    expect(within(card).getByRole('button', { name: 'Add Tomato to cart' })).toBeInTheDocument()
    expect(within(card).getByRole('button', { name: 'Save Tomato to wishlist' })).toBeInTheDocument()
    expect(within(card).getByRole('button', { name: 'Compare Tomato' })).toBeInTheDocument()
    expect(await within(card).findByText('Fresh Mart')).toBeInTheDocument()
    // The category tabs above the small cards already say it
    expect(within(card).queryByText('Vegetables')).not.toBeInTheDocument()
  })

  it('sends a guest who clicks Add to the login page, and back here afterwards', async () => {
    const { user, router } = renderWithProviders(<ProductCard product={product(1)} />, '/shop?q=tomato')
    await user.click(screen.getByRole('button', { name: 'Add Tomato to cart' }))

    expect(router.state.location.pathname).toBe('/login')
    expect(new URLSearchParams(router.state.location.search).get('next')).toBe('/shop?q=tomato')
  })

  it('cannot add a product that is out of stock', () => {
    renderWithProviders(<ProductCard product={product(13)} />)
    expect(screen.getByRole('button', { name: 'Sold out' })).toBeDisabled()
  })

  it('saves the product in the wishlist, and the header counter follows', async () => {
    const { user } = renderWithProviders(
      <>
        <Header />
        <ProductCard product={product(5)} />
      </>,
    )
    const heart = screen.getByRole('button', { name: 'Save Apple Shimla to wishlist' })
    expect(heart).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('link', { name: 'Wishlist, 0 items' })).toBeInTheDocument()

    await user.click(heart)
    expect(heart).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('link', { name: 'Wishlist, 1 item' })).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem('fresh-grocery.wishlist') ?? '[]')[0]).toMatchObject({ id: 5, name: 'Apple Shimla' })

    await user.click(heart)
    expect(screen.getByRole('link', { name: 'Wishlist, 0 items' })).toBeInTheDocument()
  })
})

import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { demoProductRows, toProduct } from '../../mocks/data/products'
import { renderApp } from '../../test/render'
import { toSavedProduct } from '../wishlist/useWishlist'

/** Chooses products for the comparison in the browser storage, as the compare buttons would (the list keeps the newest first). */
function choose(ids: number[]) {
  const products = ids.map((id) => {
    const row = demoProductRows.find((product) => product.id === id)
    return row ? toSavedProduct(toProduct(row)) : { ...toSavedProduct(toProduct(demoProductRows[0])), id, name: 'Old product' }
  })
  localStorage.setItem('fresh-grocery.compare', JSON.stringify(products.reverse()))
}

describe('Product comparison', () => {
  it('chooses products on their cards; the header and the bar at the bottom count them', async () => {
    const { user, router } = renderApp('/shop?q=tomato')
    const compare = await screen.findByRole('button', { name: 'Compare Tomato' })
    await user.click(compare)

    expect(compare).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByText('Tomato added to the comparison')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Compare, 1 item' })).toBeInTheDocument()
    const bar = screen.getByRole('complementary', { name: 'Products to compare' })
    expect(within(bar).getByRole('list', { name: 'Chosen products' })).toHaveTextContent('Tomato')

    await user.click(within(bar).getByRole('link', { name: 'Compare (1)' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/compare'))
    expect(await screen.findByRole('heading', { level: 1, name: 'Compare products' })).toBeInTheDocument()
    // Not on the comparison page itself
    expect(screen.queryByRole('complementary', { name: 'Products to compare' })).not.toBeInTheDocument()
  })

  it('takes at most 4 products', async () => {
    choose([2, 3, 4, 5])
    const { user } = renderApp('/shop?q=tomato')
    await user.click(await screen.findByRole('button', { name: 'Compare Tomato' }))

    expect(await screen.findByText('You can compare up to 4 products. Remove one first.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Compare Tomato' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('link', { name: 'Compare, 4 items' })).toBeInTheDocument()
  })

  it('shows the products side by side with today’s facts, in the order they were chosen', async () => {
    choose([1, 13, 999])
    renderApp('/compare')

    const table = await screen.findByRole('table', { name: 'The chosen products side by side' })
    const names = within(table).getAllByRole('columnheader').map((header) => within(header).getAllByRole('link').at(-1)?.textContent)
    expect(names).toEqual(['Tomato', 'Pomegranate', 'Old product'])

    const stock = within(table).getByRole('row', { name: /^Stock/ })
    await waitFor(() => expect(stock).toHaveTextContent('120 in stock'))
    expect(stock).toHaveTextContent('Out of stock')
    await waitFor(() => expect(stock).toHaveTextContent('No longer available'))
    expect(within(table).getByRole('row', { name: /^Price/ })).toHaveTextContent('₹36')
    expect(within(table).getByRole('row', { name: /^Description/ })).toHaveTextContent('Farm fresh red tomatoes')
    expect(within(table).getByRole('button', { name: 'Add Tomato to cart' })).toBeInTheDocument()
  })

  it('removes one product, then all of them', async () => {
    choose([1, 5])
    const { user } = renderApp('/compare')
    await user.click(await screen.findByRole('button', { name: 'Remove Tomato from the comparison' }))
    expect(screen.getAllByRole('columnheader')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Remove all' }))
    expect(screen.getByRole('heading', { name: 'No products to compare yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Compare, 0 items' })).toBeInTheDocument()
  })

  it('empties the comparison from the bar', async () => {
    choose([1])
    const { user } = renderApp('/')
    const bar = await screen.findByRole('complementary', { name: 'Products to compare' })
    await user.click(within(bar).getByRole('button', { name: 'Empty the comparison' }))
    expect(screen.queryByRole('complementary', { name: 'Products to compare' })).not.toBeInTheDocument()
  })
})

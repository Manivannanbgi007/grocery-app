import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { demoProductRows, toProduct } from '../../mocks/data/products'
import { renderApp } from '../../test/render'
import { toSavedProduct } from './useWishlist'

/** Saves products in the browser storage, as the heart button and the product page would. */
function save(key: string, ids: number[], changes: Record<number, Partial<ReturnType<typeof toSavedProduct>>> = {}) {
  const products = ids.map((id) => {
    const row = demoProductRows.find((product) => product.id === id)
    return row ? { ...toSavedProduct(toProduct(row)), ...changes[id] } : { ...toSavedProduct(toProduct(demoProductRows[0])), id, name: 'Old product', ...changes[id] }
  })
  localStorage.setItem(key, JSON.stringify(products))
}

describe('Wishlist page', () => {
  it('says so when the wishlist is empty', async () => {
    renderApp('/wishlist')
    expect(await screen.findByRole('heading', { name: 'There are no products on the Wishlist!' })).toBeInTheDocument()
  })

  it('shows the saved products with today’s price and stock', async () => {
    // Saved when the tomato cost ₹30; today it is ₹36. Product 999 is no longer sold.
    save('fresh-grocery.wishlist', [1, 13, 999], { 1: { sellingPrice: 30 } })
    renderApp('/wishlist')

    const list = await screen.findByRole('list', { name: 'Products in your wishlist' })
    const tomato = within(list).getByRole('link', { name: 'Tomato' }).closest('li')!
    await waitFor(() => expect(within(tomato).getByText('₹36')).toBeInTheDocument())
    expect(within(tomato).getByText('120 in stock')).toBeInTheDocument()

    const pomegranate = within(list).getByRole('link', { name: 'Pomegranate' }).closest('li')!
    await waitFor(() => expect(within(pomegranate).getByText('Out of stock')).toBeInTheDocument())
    expect(within(pomegranate).getByRole('button', { name: 'Sold out' })).toBeDisabled()

    const old = within(list).getByRole('link', { name: 'Old product' }).closest('li')!
    await waitFor(() => expect(within(old).getByText('No longer available')).toBeInTheDocument())
  })

  it('shows the day a product was added (lists saved before the date was kept have none)', async () => {
    save('fresh-grocery.wishlist', [1, 5], { 1: { addedAt: '2026-10-20T08:00:00Z' }, 5: { addedAt: undefined } })
    renderApp('/wishlist')
    const list = await screen.findByRole('list', { name: 'Products in your wishlist' })
    expect(within(list).getByRole('link', { name: 'Tomato' }).closest('li')).toHaveTextContent('Added 20 Oct 2026')
    expect(within(list).getByRole('link', { name: 'Apple Shimla' }).closest('li')).not.toHaveTextContent('Added')
  })

  it('copies a link to the wishlist, which shows the products to someone else', async () => {
    save('fresh-grocery.wishlist', [1, 5])
    const { user } = renderApp('/wishlist')
    const link = await screen.findByRole('textbox', { name: 'Wishlist link' })
    expect(link).toHaveValue(`${window.location.origin}/wishlist?shared=1,5`)
    await user.click(screen.getByRole('button', { name: 'Copy' }))
    expect(await navigator.clipboard.readText()).toBe(`${window.location.origin}/wishlist?shared=1,5`)
  })

  it('shows a shared wishlist and saves its products to your own', async () => {
    save('fresh-grocery.wishlist', [1])
    const { user } = renderApp('/wishlist?shared=1,5,abc,999')

    expect(await screen.findByRole('heading', { level: 1, name: 'A shared wishlist' })).toBeInTheDocument()
    const list = await screen.findByRole('list', { name: 'Products in the shared wishlist' })
    // Product 999 is no longer sold and "abc" is not a product
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    expect(within(list).queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Save all to my wishlist' }))
    expect(screen.getByRole('link', { name: 'Wishlist, 2 items' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'All saved to your wishlist' })).toBeDisabled()
  })

  it('removes a product', async () => {
    save('fresh-grocery.wishlist', [1, 5])
    const { user } = renderApp('/wishlist')
    await user.click(await screen.findByRole('button', { name: 'Remove Tomato from wishlist' }))
    expect(screen.queryByRole('link', { name: 'Tomato' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Wishlist, 1 item' })).toBeInTheDocument()
  })

  it('shows the recently viewed products, newest first', async () => {
    save('fresh-grocery.recently-viewed', [5, 1])
    const { user } = renderApp('/wishlist')

    const recent = (await screen.findByRole('heading', { name: 'Recently viewed products' })).closest('section')!
    expect(within(recent).getAllByRole('link', { name: /Apple Shimla|Tomato/ }).map((link) => link.textContent)).toEqual(['Apple Shimla', 'Tomato'])

    await user.click(within(recent).getByRole('button', { name: 'Clear list' }))
    expect(screen.queryByRole('heading', { name: 'Recently viewed products' })).not.toBeInTheDocument()
  })
})

describe('Wishlist pop-up', () => {
  it('opens when a heart saves a product, with the stock and the day added; Escape closes it', async () => {
    const { user } = renderApp('/products/1')
    const heart = await screen.findByRole('button', { name: 'Add to wishlist' })
    await user.click(heart)

    const popup = await screen.findByRole('dialog', { name: 'Wishlist (1)' })
    expect(within(popup).getByRole('button', { name: 'Close the wishlist' })).toHaveFocus()
    const tomato = within(within(popup).getByRole('list', { name: 'Products in your wishlist' })).getByRole('listitem')
    expect(tomato).toHaveTextContent('Tomato')
    expect(tomato).toHaveTextContent(/Added \d+ \w+ \d{4}/)
    await waitFor(() => expect(tomato).toHaveTextContent('120 in stock'))
    expect(within(tomato).getByRole('button', { name: 'Add Tomato to cart' })).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(heart).toHaveFocus()
  })

  it('keeps the focus inside while it is open', async () => {
    const { user } = renderApp('/products/1')
    await user.click(await screen.findByRole('button', { name: 'Add to wishlist' }))
    const popup = await screen.findByRole('dialog', { name: 'Wishlist (1)' })

    // Shift + Tab from the first control goes to the last one, Tab from the last back to the first
    await user.tab({ shift: true })
    expect(within(popup).getByRole('button', { name: 'Continue shopping' })).toHaveFocus()
    await user.tab()
    expect(within(popup).getByRole('button', { name: 'Close the wishlist' })).toHaveFocus()
  })

  it('removes a product, and its links lead on', async () => {
    const { user, router } = renderApp('/products/1')
    await user.click(await screen.findByRole('button', { name: 'Add to wishlist' }))
    let popup = await screen.findByRole('dialog', { name: 'Wishlist (1)' })

    await user.click(within(popup).getByRole('button', { name: 'Remove Tomato from wishlist' }))
    expect(popup).toHaveTextContent('Your wishlist is empty')
    expect(screen.getByRole('button', { name: 'Add to wishlist' })).toHaveAttribute('aria-pressed', 'false')
    await user.click(within(popup).getByRole('button', { name: 'Continue shopping' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add to wishlist' }))
    popup = await screen.findByRole('dialog', { name: 'Wishlist (1)' })
    await user.click(within(popup).getByRole('link', { name: 'Open wishlist page' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/wishlist'))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('does not open when the heart takes a product out again', async () => {
    save('fresh-grocery.wishlist', [1])
    const { user } = renderApp('/products/1')
    await user.click(await screen.findByRole('button', { name: 'Add to wishlist', pressed: true }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Wishlist, 0 items' })).toBeInTheDocument()
  })
})

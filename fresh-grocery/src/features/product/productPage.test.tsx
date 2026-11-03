import { screen, waitFor, within } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { ok } from '../../mocks/handlers/respond'
import { catalogProduct } from '../../mocks/mockCatalog'
import { server } from '../../mocks/server'
import { renderApp } from '../../test/render'

describe('Product page', () => {
  it('shows the product, its store and its reviews (newest first)', async () => {
    const { user } = renderApp('/products/1')

    expect(await screen.findByRole('heading', { level: 1, name: 'Tomato' })).toBeInTheDocument()
    expect(screen.getByText('Save ₹4 (10% off)')).toBeInTheDocument()
    expect(screen.getByText('In stock')).toBeInTheDocument()
    expect(await screen.findByText(/Sold by/)).toHaveTextContent('Sold by Fresh Mart · Open now')

    await user.click(screen.getByRole('tab', { name: 'Reviews (2)' }))
    const reviews = screen.getByRole('tabpanel', { name: 'Reviews (2)' })
    await waitFor(() => expect(within(reviews).getAllByRole('listitem')).toHaveLength(2))
    const [newest, older] = within(reviews).getAllByRole('listitem')
    expect(newest).toHaveTextContent('Rahul Verma')
    expect(older).toHaveTextContent('Fresh and firm, perfect for curries.')
  })

  it('has tabs for the description, the details and the reviews (arrow keys move between them)', async () => {
    const { user } = renderApp('/products/1')

    const description = await screen.findByRole('tabpanel', { name: 'Description' })
    expect(within(description).getByRole('heading', { name: 'Tomato, 1 kg' })).toBeInTheDocument()
    expect(description).toHaveTextContent('Farm fresh red tomatoes')

    await user.click(screen.getByRole('tab', { name: 'Description' }))
    await user.keyboard('{ArrowRight}')
    const details = screen.getByRole('tab', { name: 'Additional information' })
    expect(details).toHaveAttribute('aria-selected', 'true')
    expect(details).toHaveFocus()
    const table = within(screen.getByRole('tabpanel', { name: 'Additional information' })).getByRole('table')
    expect(within(table).getByRole('row', { name: 'SKU FM-VEG-001' })).toBeInTheDocument()
    expect(within(table).getByRole('row', { name: /^Stock 120 in stock$/ })).toBeInTheDocument()

    await user.keyboard('{End}')
    expect(screen.getByRole('tab', { name: 'Reviews (2)' })).toHaveAttribute('aria-selected', 'true')
  })

  it('opens the reviews from the stars at the top', async () => {
    const { user } = renderApp('/products/1')
    await user.click(await screen.findByRole('button', { name: /read the reviews/ }))
    expect(screen.getByRole('tab', { name: 'Reviews (2)' })).toHaveAttribute('aria-selected', 'true')
  })

  it('shows the photos as thumbnails and enlarges one', async () => {
    const tomato = catalogProduct(1)!
    server.use(http.get('*/api/v1/products/1', () => ok({ ...tomato, imageUrls: ['/photos/a.jpg', '/photos/b.jpg', '/photos/c.jpg'] }, 'Product fetched successfully')))
    const { user } = renderApp('/products/1')

    const photos = await screen.findByRole('list', { name: 'Photos' })
    await user.click(within(photos).getByRole('button', { name: 'Show photo 2 of 3' }))
    expect(within(photos).getByRole('button', { name: 'Show photo 2 of 3' })).toHaveAttribute('aria-pressed', 'true')

    const zoom = screen.getByRole('button', { name: 'Enlarge the photo' })
    await user.click(zoom)
    expect(screen.getByRole('dialog', { name: 'Tomato, photo 2 of 3' })).toBeInTheDocument()
    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('dialog', { name: 'Tomato, photo 3 of 3' })).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(zoom).toHaveFocus()
  })

  it('has no zoom and no thumbnails without photos', async () => {
    renderApp('/products/1')
    await screen.findByRole('heading', { level: 1, name: 'Tomato' })
    expect(screen.queryByRole('button', { name: 'Enlarge the photo' })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Photos' })).not.toBeInTheDocument()
  })

  it('adds the product to the comparison and the wishlist (the wishlist pop-up opens)', async () => {
    const { user } = renderApp('/products/1')
    const compare = await screen.findByRole('button', { name: 'Compare', pressed: false })
    await user.click(compare)
    expect(compare).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('link', { name: 'Compare, 1 item' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Add to wishlist', pressed: false }))
    expect(await screen.findByRole('dialog', { name: 'Wishlist (1)' })).toBeInTheDocument()
  })

  it('shows the shop’s categories without asking for their counts (one request each would hit the rate limit)', async () => {
    const countRequests: string[] = []
    const listen = ({ request }: { request: Request }) => {
      const url = new URL(request.url)
      if (url.pathname === '/api/v1/products' && url.searchParams.get('size') === '1') countRequests.push(url.search)
    }
    server.events.on('request:start', listen)
    renderApp('/products/1')

    const sidebar = await screen.findByRole('complementary', { name: 'Browse the shop' })
    expect(await within(sidebar).findByRole('link', { name: 'Vegetables' })).toBeInTheDocument()
    expect(countRequests).toEqual([])
    server.events.removeListener('request:start', listen)
  })

  it('remembers the product in "recently viewed"', async () => {
    renderApp('/products/5')
    await screen.findByRole('heading', { level: 1, name: 'Apple Shimla' })
    await waitFor(() => {
      const saved = JSON.parse(localStorage.getItem('fresh-grocery.recently-viewed') ?? '[]')
      expect(saved[0]).toMatchObject({ id: 5, name: 'Apple Shimla' })
    })
  })

  it('never lets you choose more than the store has', async () => {
    const { user } = renderApp('/products/7') // Amul Butter, 3 in stock
    expect(await screen.findByText('Only 3 left in stock')).toBeInTheDocument()

    const increase = screen.getByRole('button', { name: 'Increase quantity' })
    await user.click(increase)
    await user.click(increase)
    expect(screen.getByRole('group', { name: 'Quantity of Amul Butter' })).toHaveTextContent('3')
    expect(increase).toBeDisabled()
  })

  it('shows an out-of-stock product without a quantity', async () => {
    renderApp('/products/13') // Pomegranate
    expect(await screen.findByText('Out of stock')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sold out' })).toBeDisabled()
    expect(screen.queryByRole('group', { name: /Quantity/ })).not.toBeInTheDocument()
  })

  it.each(['/products/17', '/products/abc'])('explains when %s is not available', async (path) => {
    renderApp(path)
    expect(await screen.findByRole('heading', { name: 'This product is not available' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the shop' })).toHaveAttribute('href', '/shop')
  })
})

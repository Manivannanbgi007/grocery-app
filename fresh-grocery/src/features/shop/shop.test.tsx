import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { chooseOption } from '../../test/pickers'
import { renderApp } from '../../test/render'

/** The names on the product cards, in order. */
function cardNames(): string[] {
  return screen.getAllByRole('article').map((card) => within(card).getByRole('heading').textContent ?? '')
}

const sidebar = () => screen.getByRole('complementary', { name: 'Browse the shop' })
const filterBar = () => screen.getByRole('region', { name: 'Filters' })

/** The shop shows many products at once; while the whole test suite runs, a new list can take longer than 3 s. */
const SLOW = { timeout: 10_000 }

/** Opens the shop and waits for its code (a separate file, see `lazy` in routes.tsx) to be loaded. */
async function renderShop(path: string) {
  const rendered = renderApp(path)
  await screen.findByRole('heading', { level: 1 })
  return rendered
}

describe('Shop page', () => {
  it('shows the first 12 of all products, and pages through them (Back works too)', async () => {
    const { user, router } = await renderShop('/shop')

    expect(screen.getByRole('heading', { level: 1, name: 'All products' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(12), SLOW)
    expect(screen.getByText(/We found/)).toHaveTextContent('We found 109 items for you!')
    // Without a search text, "Best match" lists the products by name
    expect(cardNames()[0]).toBe('Aashirvaad Whole Wheat Atta')

    await user.click(screen.getByRole('button', { name: 'Page 2' }))
    expect(router.state.location.search).toBe('?page=2')
    await waitFor(() => expect(cardNames()[0]).toBe('Bournvita Health Drink'), SLOW)
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page')

    await router.navigate(-1)
    await waitFor(() => expect(cardNames()[0]).toBe('Aashirvaad Whole Wheat Atta'), SLOW)
  })

  it('sorts by price', async () => {
    const { user, router } = await renderShop('/shop')
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(12))

    await chooseOption(user, screen.getByLabelText('Sort by:'), 'Price: low to high')
    expect(router.state.location.search).toBe('?sort=PRICE_LOW_TO_HIGH')
    await waitFor(() => expect(cardNames()[0]).toBe("Lay's Classic Salted")) // ₹20, the lowest id of the ₹20 products
  })

  it('filters by category from the sidebar, then by stock', async () => {
    const { user, router } = await renderShop('/shop')
    await user.click(await within(sidebar()).findByRole('link', { name: 'Fruits, 8 products' }))

    expect(router.state.location.search).toBe('?category=2')
    expect(await screen.findByRole('heading', { level: 1, name: 'Fruits' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(/We found/)).toHaveTextContent('We found 8 items'))
    expect(within(sidebar()).getByRole('link', { name: 'Fruits, 8 products' })).toHaveAttribute('aria-current', 'true')

    const inStockOnly = within(filterBar()).getByRole('checkbox', { name: 'In stock only' })
    await user.click(inStockOnly)
    expect(router.state.location.search).toBe('?category=2&inStock=true')
    // The same checkbox is still there, checked and focused (keyboard users do not lose their place)
    expect(inStockOnly).toBeChecked()
    expect(inStockOnly).toHaveFocus()
    // The pomegranate is out of stock
    await waitFor(() => expect(screen.getByText(/We found/)).toHaveTextContent('We found 7 items'))
    expect(cardNames()).not.toContain('Pomegranate')
  })

  it('filters by brand from the filter bar, shows it as a chip, and removes it again', async () => {
    const { user, router } = await renderShop('/shop')
    const brandButton = within(filterBar()).getByRole('button', { name: 'Brand' })
    await user.click(brandButton)
    expect(brandButton).toHaveAttribute('aria-expanded', 'true')
    await user.click(await within(screen.getByRole('group', { name: 'Choose a brand' })).findByRole('link', { name: 'Amul' }))

    expect(router.state.location.search).toBe('?brand=Amul')
    // The choices close, and the button shows the brand and keeps the keyboard focus
    expect(screen.queryByRole('group', { name: 'Choose a brand' })).not.toBeInTheDocument()
    expect(within(filterBar()).getByRole('button', { name: 'Brand: Amul' })).toHaveFocus()
    await waitFor(() => expect(screen.getByText(/We found/)).toHaveTextContent('We found 6 items'))
    expect(cardNames().every((name) => name.startsWith('Amul'))).toBe(true)

    await user.click(screen.getByRole('link', { name: 'Remove filter: Amul' }))
    expect(router.state.location.search).toBe('')
    expect(within(filterBar()).getByRole('button', { name: 'Brand' })).toBeInTheDocument()
  })

  it('filters by store from the filter bar', async () => {
    const { user, router } = await renderShop('/shop')
    // The button is there once the stores have loaded (with one store there is nothing to choose)
    await user.click(await within(filterBar()).findByRole('button', { name: 'Store' }))
    await user.click(await within(screen.getByRole('group', { name: 'Choose a store' })).findByRole('link', { name: /^Green Basket/ }))

    expect(router.state.location.search).toBe('?store=2')
    expect(await screen.findByRole('heading', { level: 1, name: 'Green Basket' })).toBeInTheDocument()
    expect(within(filterBar()).getByRole('button', { name: 'Store: Green Basket' })).toHaveFocus()
    await waitFor(() => expect(screen.getAllByRole('article').every((card) => card.textContent?.includes('By Green Basket'))).toBe(true), SLOW)

    // Open again: the chosen store is marked, and the same button closes the choices
    await user.click(within(filterBar()).getByRole('button', { name: 'Store: Green Basket' }))
    expect(within(screen.getByRole('group', { name: 'Choose a store' })).getByRole('link', { name: /^Green Basket/ })).toHaveAttribute('aria-current', 'true')
    await user.click(within(filterBar()).getByRole('button', { name: 'Store: Green Basket' }))
    expect(screen.queryByRole('group', { name: 'Choose a store' })).not.toBeInTheDocument()
  })

  it('checks the price range before applying it', async () => {
    const { user, router } = await renderShop('/shop')
    await user.click(within(filterBar()).getByRole('button', { name: 'Price' }))
    const choices = screen.getByRole('group', { name: 'Choose a price range' })
    await user.type(within(choices).getByLabelText('Lowest price'), '100')
    await user.type(within(choices).getByLabelText('Highest price'), '50')
    await user.click(within(choices).getByRole('button', { name: 'Apply price' }))
    expect(within(choices).getByRole('alert')).toHaveTextContent('must not be higher')
    expect(router.state.location.search).toBe('')

    await user.clear(within(choices).getByLabelText('Highest price'))
    await user.type(within(choices).getByLabelText('Highest price'), '150')
    await user.click(within(choices).getByRole('button', { name: 'Apply price' }))
    expect(router.state.location.search).toBe('?minPrice=100&maxPrice=150')
    expect(await screen.findByRole('link', { name: 'Remove filter: ₹100 – ₹150' })).toBeInTheDocument()
    expect(within(filterBar()).getByRole('button', { name: 'Price: ₹100 – ₹150' })).toHaveFocus()
  })

  it('says so when nothing matches, with a way out', async () => {
    await renderShop('/shop?q=zzzz')
    expect(await screen.findByRole('heading', { name: 'No products match your filters' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Clear all filters' })).toHaveAttribute('href', '/shop')
  })

  it('handles a page number that is too high', async () => {
    await renderShop('/shop?page=99')
    expect(await screen.findByRole('heading', { name: 'There is nothing on this page' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the first page' })).toHaveAttribute('href', '/shop')
  })

  it('starts with the new arrivals and the top rated products, on the first page without filters only', async () => {
    const { router } = await renderShop('/shop')
    const newArrivals = await screen.findByRole('region', { name: 'New arrivals' })
    const topRated = screen.getByRole('region', { name: 'Top rated products' })
    // 10 products each, all in stock (every one has an Add button)
    await waitFor(() => expect(within(newArrivals).getAllByRole('button', { name: /^Add .* to cart$/ })).toHaveLength(10), SLOW)
    await waitFor(() => expect(within(topRated).getAllByRole('button', { name: /^Add .* to cart$/ })).toHaveLength(10), SLOW)
    expect(within(newArrivals).getByRole('link', { name: 'See all new arrivals' })).toHaveAttribute('href', '/shop?sort=NEWEST')
    expect(within(topRated).getByRole('link', { name: 'See all top rated products' })).toHaveAttribute('href', '/shop?sort=RATING')
    // The product cards of the list are counted apart from them
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(12), SLOW)

    // A narrowed list, or another page, starts with the list
    await router.navigate('/shop?category=2')
    await waitFor(() => expect(screen.queryByRole('region', { name: 'New arrivals' })).not.toBeInTheDocument(), SLOW)
    expect(screen.queryByRole('region', { name: 'Top rated products' })).not.toBeInTheDocument()
    await router.navigate('/shop?page=2')
    await waitFor(() => expect(cardNames()[0]).toBe('Bournvita Health Drink'), SLOW)
    expect(screen.queryByRole('region', { name: 'New arrivals' })).not.toBeInTheDocument()
  })

  it('lists the first 8 categories, all of them after "Show all", and always the chosen one', async () => {
    const { user } = await renderShop('/shop')
    const categoryLinks = () => within(screen.getByRole('list', { name: 'Product categories' })).getAllByRole('link')
    // "All categories" and the first 8
    await waitFor(() => expect(categoryLinks()).toHaveLength(9))

    await user.click(within(sidebar()).getByRole('button', { name: 'Show all 21 categories' }))
    expect(categoryLinks()).toHaveLength(22)
    await user.click(within(sidebar()).getByRole('button', { name: 'Show fewer' }))
    expect(categoryLinks()).toHaveLength(9)
  })

  it('keeps a chosen category in the short list when it is not among the first 8', async () => {
    await renderShop('/shop?category=36')
    expect(await screen.findByRole('heading', { level: 1, name: 'Pooja Needs' })).toBeInTheDocument()
    const list = screen.getByRole('list', { name: 'Product categories' })
    expect(within(list).getAllByRole('link')).toHaveLength(10)
    expect(within(list).getByRole('link', { name: /^Pooja Needs/ })).toHaveAttribute('aria-current', 'true')
  })

  it('opens the categories with "Categories" on small screens, and "Show 109 products" closes them', async () => {
    const { user } = await renderShop('/shop')
    // The left column comes before the products in the page, so it opens above them; from lg it is always shown
    const column = sidebar().parentElement
    expect(column).toHaveClass('hidden', 'lg:block')

    await user.click(screen.getByRole('button', { name: 'Categories' }))
    expect(screen.getByRole('button', { name: 'Hide categories' })).toHaveAttribute('aria-expanded', 'true')
    expect(column).not.toHaveClass('hidden')

    await user.click(await screen.findByRole('button', { name: 'Show 109 products' }))
    expect(screen.getByRole('button', { name: 'Categories' })).toHaveAttribute('aria-expanded', 'false')
    expect(column).toHaveClass('hidden')
  })
})

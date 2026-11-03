import { act, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { renderApp, renderWithProviders } from '../../test/render'
import { BackToTopButton } from './BackToTopButton'
import { CategoryMenu } from './CategoryMenu'
import { Header } from './Header'

describe('Header search', () => {
  it('opens the shop with the search text and the chosen category', async () => {
    const { user, router } = renderWithProviders(<Header />)

    const search = screen.getByRole('search')
    // The category list comes from the (mock) API
    await user.click(within(search).getByLabelText('Category'))
    await user.click(await screen.findByRole('option', { name: 'Dairy & Eggs' }))
    await user.type(within(search).getByLabelText('Search for products'), 'amul butter')
    await user.click(within(search).getByRole('button', { name: 'Search' }))

    expect(router.state.location.pathname).toBe('/shop')
    expect(router.state.location.search).toBe('?q=amul+butter&category=3')
  })

  it('shows the shop’s current search text on the shop page', async () => {
    renderWithProviders(<Header />, '/shop?q=milk')
    expect(screen.getByLabelText('Search for products')).toHaveValue('milk')
  })

  it('counts the wishlist products saved in the browser', () => {
    localStorage.setItem('fresh-grocery.wishlist', JSON.stringify([{ id: 1 }, { id: 6 }]))
    renderWithProviders(<Header />)
    expect(screen.getByRole('link', { name: 'Wishlist, 2 items' })).toBeInTheDocument()
  })
})

describe('While the page scrolls', () => {
  /** jsdom does not scroll: this sets window.scrollY and tells the page about it. */
  function scrollPageTo(y: number) {
    act(() => {
      Object.defineProperty(window, 'scrollY', { value: y, configurable: true })
      window.dispatchEvent(new Event('scroll'))
    })
  }

  afterEach(() => {
    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true })
  })

  it('keeps the header at the top, with a shadow once the page moves beneath it', () => {
    renderWithProviders(<Header />)
    const header = screen.getByRole('banner')
    expect(header).toHaveClass('sticky', 'top-0')
    expect(header).not.toHaveClass('shadow-md')

    scrollPageTo(300)
    expect(header).toHaveClass('shadow-md')
    scrollPageTo(0)
    expect(header).not.toHaveClass('shadow-md')
  })

  it('shows the back-to-top button after 600 px', () => {
    renderWithProviders(<BackToTopButton />)
    expect(screen.queryByRole('button', { name: 'Back to top' })).not.toBeInTheDocument()
    scrollPageTo(700)
    expect(screen.getByRole('button', { name: 'Back to top' })).toBeInTheDocument()
  })
})

describe('CategoryMenu', () => {
  it('lists the categories with their product counts', async () => {
    const { user } = renderWithProviders(<CategoryMenu />)
    const button = screen.getByRole('button', { name: /browse all categories/i })
    expect(button).toHaveAttribute('aria-expanded', 'false')

    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')
    // Vegetables: 5 seed products + 5 demo products
    expect(await screen.findByRole('link', { name: 'Vegetables, 10 products' })).toHaveAttribute('href', '/shop?category=1')
  })

  it('closes on Escape and gives the focus back to the button', async () => {
    const { user } = renderWithProviders(<CategoryMenu />)
    const button = screen.getByRole('button', { name: /browse all categories/i })
    await user.click(button)
    await screen.findByRole('link', { name: /^Fruits/ })

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('link', { name: /^Fruits/ })).not.toBeInTheDocument()
    expect(button).toHaveFocus()
  })

  it('closes on a click outside, and after picking a category', async () => {
    const { user, router } = renderWithProviders(
      <div>
        <p>Outside</p>
        <CategoryMenu />
      </div>,
    )
    const button = screen.getByRole('button', { name: /browse all categories/i })

    await user.click(button)
    await screen.findByRole('link', { name: /^Fruits/ })
    await user.click(screen.getByText('Outside'))
    expect(screen.queryByRole('link', { name: /^Fruits/ })).not.toBeInTheDocument()

    await user.click(button)
    await user.click(await screen.findByRole('link', { name: /^Fruits/ }))
    expect(router.state.location.search).toBe('?category=2')
    expect(screen.queryByRole('link', { name: /^Fruits/ })).not.toBeInTheDocument()
  })
})

describe('MobileMenu', () => {
  it('opens from the menu button and closes with Escape', async () => {
    const { user } = renderWithProviders(<Header />)
    const openButton = screen.getByRole('button', { name: 'Open menu' })

    await user.click(openButton)
    const menu = screen.getByRole('dialog', { name: 'Menu' })
    expect(within(menu).getByRole('link', { name: 'Shop' })).toHaveAttribute('href', '/shop')
    expect(await within(menu).findByRole('link', { name: /Household/ })).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
    expect(openButton).toHaveFocus()
  })
})

describe('MainLayout', () => {
  it('shows the support phone and free-delivery amount from the platform settings', async () => {
    renderApp('/')
    expect(await screen.findByText('On orders of ₹299 or more')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /1800-123-4567/ })[0]).toHaveAttribute('href', 'tel:18001234567')
  })

  it('has no thin bar above the header: its phone, offer and links are shown below it already', async () => {
    renderApp('/')
    await screen.findByText('On orders of ₹299 or more')
    expect(screen.queryByText(/Need help\? Call us/)).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Quick links' })).not.toBeInTheDocument()
  })
})

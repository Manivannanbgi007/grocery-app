import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { logInAs, renderApp } from './test/render'

describe('routes', () => {
  it('shows the 404 page, inside the normal layout, for an unknown address', async () => {
    renderApp('/no-such-page')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getAllByRole('search')[0]).toBeInTheDocument() // the header is still there
    expect(screen.getByRole('link', { name: 'Back to home' })).toHaveAttribute('href', '/')
  })

  it.each([
    ['/', 'Fresh vegetables, delivered today'],
    ['/shop', 'All products'],
    ['/products/1', 'Tomato'],
    ['/cart', 'Log in'], // a guest is sent to the login page first
    ['/wishlist', 'Wishlist'],
    ['/login', 'Log in'],
    ['/register', 'Create an account'],
    ['/register?role=VENDOR', 'Become a vendor'],
  ])('opens %s', async (path, heading) => {
    renderApp(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })
})

describe("the shop's home page", () => {
  it('stays at / for a guest', async () => {
    const { router } = renderApp('/')
    expect(await screen.findByRole('heading', { level: 2, name: 'Deals Of The Day' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
    // The logo in the header and in the footer
    for (const logo of screen.getAllByRole('link', { name: 'Fresh Grocery, home' })) expect(logo).toHaveAttribute('href', '/')
  })

  it('moves a logged-in customer from / to their own address, /customer/home, keeping the section to jump to', async () => {
    logInAs()
    const { router } = renderApp('/#deals')
    await waitFor(() => expect(router.state.location.pathname).toBe('/customer/home'))
    expect(router.state.location.hash).toBe('#deals')
    expect(await screen.findByRole('heading', { level: 2, name: 'Deals Of The Day' })).toBeInTheDocument()
  })

  it("points a customer's links to the home page and its sections at /customer/home", async () => {
    logInAs()
    renderApp('/products/1')
    await screen.findByRole('button', { name: 'Account of Priya Sharma' })

    for (const logo of screen.getAllByRole('link', { name: 'Fresh Grocery, home' })) expect(logo).toHaveAttribute('href', '/customer/home')
    const mainMenu = screen.getByRole('navigation', { name: 'Main' })
    expect(within(mainMenu).getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/customer/home')
    expect(within(mainMenu).getByRole('link', { name: 'Hot Deals' })).toHaveAttribute('href', '/customer/home#deals')
    const breadcrumb = await screen.findByRole('navigation', { name: 'Breadcrumb' })
    expect(within(breadcrumb).getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/customer/home')
  })
})

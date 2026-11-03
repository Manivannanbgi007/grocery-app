import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from './test/render'

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

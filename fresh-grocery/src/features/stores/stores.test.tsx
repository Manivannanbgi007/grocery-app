import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { switchFlag } from '../../mocks/mockPlatform'
import { logInAs, renderApp } from '../../test/render'

/*
 * Stores in the shop (mock API): Fresh Mart (store 1, vendor1) and Green Basket (store 2, vendor2), both open; the
 * store filter of the shop; and the PRODUCT_REVIEWS flag on the review form of a delivered order (customer1's 901).
 */

describe('Stores', () => {
  it('lists the stores, each with a link to its products and its page', async () => {
    renderApp('/stores')
    const list = await screen.findByRole('list', { name: 'Stores' }, { timeout: 10_000 })
    const freshMart = within(list).getAllByRole('listitem')[0]
    expect(within(freshMart).getByRole('heading', { name: 'Fresh Mart' })).toBeInTheDocument()
    expect(freshMart).toHaveTextContent('Open now')
    expect(freshMart).toHaveTextContent(/Today: \d\d:\d\d–\d\d:\d\d/)
    expect(within(freshMart).getByRole('link', { name: 'Shop at Fresh Mart' })).toHaveAttribute('href', '/shop?store=1')
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
  })

  it("shows a store's details, hours and top rated products", async () => {
    renderApp('/stores/2')
    expect(await screen.findByRole('heading', { level: 1, name: 'Green Basket' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^\d{10}$/ })).toHaveAttribute('href', expect.stringMatching(/^tel:/))
    expect(within(screen.getByRole('region', { name: 'Opening hours' })).getByText(/\(today\)/)).toBeInTheDocument()
    const products = await screen.findAllByRole('article')
    expect(products.length).toBeGreaterThan(0)
    // "By Green Basket" appears once the store list is loaded
    await waitFor(() => {
      for (const product of screen.getAllByRole('article')) expect(product).toHaveTextContent('By Green Basket')
    })
  })

  it('says when a store does not exist', async () => {
    renderApp('/stores/99')
    expect(await screen.findByRole('heading', { name: 'Store not found' }, { timeout: 10_000 })).toBeInTheDocument()
  })

  it("filters the shop by store, and the product page links to the store", async () => {
    const { user, router } = renderApp('/shop')
    // The store choices are in the filter bar above the products, behind its "Store" button
    const filterBar = await screen.findByRole('region', { name: 'Filters' }, { timeout: 10_000 })
    await user.click(await within(filterBar).findByRole('button', { name: 'Store' }, { timeout: 10_000 }))
    await user.click(within(screen.getByRole('group', { name: 'Choose a store' })).getByRole('link', { name: /Green Basket/ }))
    expect(router.state.location.search).toBe('?store=2')
    expect(await screen.findByRole('heading', { level: 1, name: 'Green Basket' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Remove filter: Green Basket' })).toHaveAttribute('href', '/shop')
    await waitFor(() => expect(screen.queryByRole('list', { busy: true })).not.toBeInTheDocument())
    for (const product of screen.getAllByRole('article')) expect(product).toHaveTextContent('Green Basket')

    await router.navigate('/products/1')
    // The product page loads on first use (lazy): with every test file running at once that can take a while
    expect(await screen.findByRole('link', { name: 'Fresh Mart' }, { timeout: 10_000 })).toHaveAttribute('href', '/stores/1')
  })
})

describe('PRODUCT_REVIEWS flag', () => {
  it('hides the review form of a delivered order while the flag is off', async () => {
    switchFlag(1, 'PRODUCT_REVIEWS', false)
    logInAs()
    renderApp('/orders/901')
    expect(await screen.findByRole('heading', { level: 1, name: 'Order #901' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(await screen.findByText('Tomato')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Review Tomato' })).not.toBeInTheDocument())
  })
})

import { cleanup, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { logInAs, renderApp } from '../../test/render'

/* vendor1 = Ravi Kumar of Fresh Mart. */

describe('Collapsible panel menu', () => {
  it('collapses to icons and back, keeps the page names, and remembers the choice', async () => {
    logInAs('vendor1@grocery.com')
    const { user } = renderApp('/vendor')
    const collapse = await screen.findByRole('button', { name: 'Collapse menu' }, { timeout: 10_000 })
    expect(collapse).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Your store')).toBeInTheDocument()

    await user.click(collapse)
    const expand = screen.getByRole('button', { name: 'Expand menu' })
    expect(expand).toHaveAttribute('aria-expanded', 'false')
    // The store box is hidden; every link keeps its name for screen readers
    expect(screen.queryByText('Your store')).not.toBeInTheDocument()
    const menu = screen.getByRole('navigation', { name: 'Vendor panel' })
    const coupons = within(menu).getByRole('link', { name: 'Coupons' })
    expect(coupons).toHaveAttribute('href', '/vendor/coupons')

    // The name shows beside the icon on hover (decoration: the link already has the name)
    await user.hover(coupons)
    expect(screen.getAllByText('Coupons').some((element) => element.getAttribute('aria-hidden') === 'true')).toBe(true)
    expect(localStorage.getItem('fresh-grocery.menu-collapsed')).toBe('true')

    // The next visit starts collapsed
    cleanup()
    const next = renderApp('/vendor')
    await next.user.click(await screen.findByRole('button', { name: 'Expand menu' }, { timeout: 10_000 }))
    expect(screen.getByRole('button', { name: 'Collapse menu' })).toBeInTheDocument()
    expect(localStorage.getItem('fresh-grocery.menu-collapsed')).toBeNull()
  })
})

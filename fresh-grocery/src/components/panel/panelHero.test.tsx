import { render, screen, within } from '@testing-library/react'
import { CircleCheck, Map as MapIcon, Package, ShoppingBag } from 'lucide-react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { KpiTile } from '../sales/KpiTile'
import { EmptyState } from '../ui/EmptyState'
import { StatusBadge } from '../ui/StatusBadge'
import { FilterBar } from './FilterBar'
import { PageHeader } from './PageHeader'
import { PanelHero } from './PanelHero'

/* The shared parts of the design system (O17): the panels' hero, the number tile and the empty state. */

describe('PanelHero', () => {
  it('is the page title with its quick links; the waving hand and the drawing are decoration', () => {
    renderWithProviders(
      <PanelHero
        look="vendor"
        title="Hello, Ravi"
        wave
        subtitle="Fresh Mart: here is what needs your attention."
        links={[
          { to: '/vendor/orders', label: 'Order queue', icon: ShoppingBag },
          { to: '/vendor/products', label: 'Products', icon: Package },
        ]}
      />,
    )
    // The hand is hidden from screen readers, so the heading's name is the greeting alone
    expect(screen.getByRole('heading', { level: 1, name: 'Hello, Ravi' })).toBeInTheDocument()
    const links = screen.getByRole('navigation', { name: 'Quick links' })
    expect(within(links).getByRole('link', { name: 'Order queue' })).toHaveAttribute('href', '/vendor/orders')
    expect(within(links).getByRole('link', { name: 'Products' })).toHaveAttribute('href', '/vendor/products')
    // Without an eyebrow of its own, the chip shows today's date
    expect(screen.getByText(/\d{1,2} [A-Z][a-z]+$/)).toBeInTheDocument()
  })

  it('takes its own chip text, and leaves out the links when there are none', () => {
    renderWithProviders(<PanelHero look="shop" compact title="Addresses" eyebrow="Signed in as Priya Sharma" />)
    expect(screen.getByRole('heading', { level: 1, name: 'Addresses' })).toBeInTheDocument()
    expect(screen.getByText('Signed in as Priya Sharma')).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Quick links' })).not.toBeInTheDocument()
  })
})

describe('KpiTile', () => {
  it('is a link to the details when it has one, with the label right before the value', () => {
    renderWithProviders(<KpiTile label="Earned today" value="₹240" hint="2 deliveries" icon={ShoppingBag} accent="blue" to="/delivery/earnings" />)
    const tile = screen.getByRole('link', { name: /Earned today/ })
    expect(tile).toHaveAttribute('href', '/delivery/earnings')
    expect(screen.getByText('Earned today', { selector: 'p' }).nextElementSibling).toHaveTextContent('₹240')
  })

  it('is a plain tile without a link', () => {
    renderWithProviders(<KpiTile label="Orders" value="12" />)
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('Orders', { selector: 'p' }).nextElementSibling).toHaveTextContent('12')
  })
})

describe('EmptyState', () => {
  it('shows an emoji as a hidden illustration, and an element of its own as it is', () => {
    // No router or data needed: a plain render
    const { rerender } = render(<EmptyState icon="🧺" title="Your cart is empty" />)
    expect(screen.getByRole('heading', { name: 'Your cart is empty' })).toBeInTheDocument()
    expect(screen.getByText('🧺').closest('[aria-hidden="true"]')).not.toBeNull()

    rerender(<EmptyState icon={<span data-testid="own-icon" />} title="Waiting for a delivery" />)
    expect(screen.getByTestId('own-icon')).toBeInTheDocument()
  })
})

describe('PageHeader', () => {
  it('is the page title with its text and actions; the drawing is decoration', () => {
    render(
      <PageHeader
        title="Delivery zones"
        icon={MapIcon}
        accent="green"
        description="A store's deliveries go to the partners of its zone."
        illustration={<svg data-testid="drawing" aria-hidden="true" />}
        actions={<button type="button">New zone</button>}
      />,
    )
    expect(screen.getByRole('heading', { level: 1, name: 'Delivery zones' })).toBeInTheDocument()
    expect(screen.getByText("A store's deliveries go to the partners of its zone.")).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New zone' })).toBeInTheDocument()
    expect(screen.getByTestId('drawing')).toHaveAttribute('aria-hidden', 'true')
  })
})

describe('StatusBadge', () => {
  it('says the status in words; the dot or the icon is decoration', () => {
    const { rerender } = render(<StatusBadge tone="new" pulse>Waiting for a partner</StatusBadge>)
    const badge = screen.getByText('Waiting for a partner')
    expect(badge.querySelectorAll('[aria-hidden="true"]')).toHaveLength(1)

    rerender(
      <StatusBadge tone="brand" icon={CircleCheck}>
        Paid online
      </StatusBadge>,
    )
    expect(screen.getByText('Paid online').querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })
})

describe('FilterBar', () => {
  it('puts the filters in one row after a "Filter" label', () => {
    render(
      <FilterBar>
        <input aria-label="Store name" />
      </FilterBar>,
    )
    expect(screen.getByText('Filter')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Store name' })).toBeInTheDocument()
  })
})

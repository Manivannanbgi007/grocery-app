import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { StatusTabs } from './StatusTabs'

const TABS = [
  { key: 'all', label: 'All', to: '/list', group: 'all' },
  { key: 'waiting', label: 'Waiting', to: '/list?tab=waiting', tone: 'new' as const, count: 3, group: 'open' },
  { key: 'packed', label: 'Packed', to: '/list?tab=packed', tone: 'sale' as const, group: 'open' },
  { key: 'done', label: 'Done', to: '/list?tab=done', tone: 'brand' as const, group: 'finished' },
]

describe('Status tabs', () => {
  it('shows each tab as a link, marks the chosen one, and names the waiting work', () => {
    renderWithProviders(<StatusTabs label="Status" tabs={TABS} active="done" />)
    const bar = screen.getByRole('navigation', { name: 'Status' })

    // The dots and the lines between the groups are decoration: screen readers hear the 4 tabs only
    expect(within(bar).getAllByRole('listitem')).toHaveLength(4)
    expect(within(bar).getAllByRole('link').map((link) => link.textContent)).toEqual(['All', 'Waiting3', 'Packed', 'Done'])
    expect(within(bar).getByRole('link', { name: 'Done' })).toHaveAttribute('aria-current', 'page')
    expect(within(bar).getByRole('link', { name: 'All' })).not.toHaveAttribute('aria-current')
    expect(within(bar).getByRole('link', { name: 'Waiting, 3 waiting' })).toHaveAttribute('href', '/list?tab=waiting')
  })

  it('has no scroll buttons while every tab fits', () => {
    renderWithProviders(<StatusTabs label="Status" tabs={TABS} active="all" />)
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

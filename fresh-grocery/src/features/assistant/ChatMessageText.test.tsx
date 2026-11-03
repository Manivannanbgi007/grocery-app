import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../test/render'
import { ChatLinks, ChatMessageText } from './ChatMessageText'

describe('ChatMessageText', () => {
  it('makes safe links work and leaves unsafe ones as text', async () => {
    const onNavigate = vi.fn()
    const { user, router } = renderWithProviders(
      <p>
        <ChatMessageText
          text="Open [your cart](/cart), read https://example.com/help. Not [this](javascript:alert) or [the admin](/admin)."
          kind="shop"
          onNavigate={onNavigate}
        />
      </p>,
    )

    const external = screen.getByRole('link', { name: 'https://example.com/help (opens in a new tab)' })
    expect(external).toHaveAttribute('href', 'https://example.com/help')
    expect(external).toHaveAttribute('target', '_blank')
    expect(external).toHaveAttribute('rel', 'noopener noreferrer')
    // Unsafe or not allowed: only the label, as text
    expect(screen.queryByRole('link', { name: 'this' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'the admin' })).not.toBeInTheDocument()
    expect(screen.getByText(/Not this or the admin\./)).toBeInTheDocument()

    // A page of the app opens with the router (no page reload)
    await user.click(screen.getByRole('link', { name: 'your cart' }))
    expect(router.state.location.pathname).toBe('/cart')
    expect(onNavigate).toHaveBeenCalledOnce()
  })
})

describe('ChatLinks', () => {
  it("shows only the links this chat may open", () => {
    renderWithProviders(
      <ChatLinks
        links={[
          { label: 'Your wishlist', path: '/wishlist' },
          { label: 'Evil', path: '//evil.example' },
          { label: 'Vendor orders', path: '/vendor/orders' },
        ]}
        kind="shop"
        onNavigate={() => {}}
      />,
    )
    // A labelled group, not a list: the message around it is a list item of the chat
    const group = screen.getByRole('group', { name: 'Pages for this answer' })
    expect(group).toHaveTextContent('Your wishlist')
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    expect(screen.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByRole('link', { name: 'Your wishlist' })).toHaveAttribute('href', '/wishlist')
  })

  it('shows nothing when no link is allowed', () => {
    renderWithProviders(<ChatLinks links={[{ label: 'Admin', path: '/admin' }]} kind="shop" onNavigate={() => {}} />)
    expect(screen.queryByRole('group')).not.toBeInTheDocument()
  })
})

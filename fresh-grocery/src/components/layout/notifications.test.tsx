import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { logInAs, renderApp } from '../../test/render'

/* customer1 (Priya) has 4 mock notifications, 2 unread: "Order #901 delivered" and "Order #902 cancelled". */

describe('Notification bell', () => {
  it('is only there for a logged-in customer', async () => {
    renderApp('/')
    await screen.findByRole('link', { name: 'Account' })
    expect(screen.queryByRole('button', { name: /^Notifications/ })).not.toBeInTheDocument()
  })

  it('shows the unread count and opens the order a notification is about', async () => {
    logInAs()
    const { user, router } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))

    const list = await screen.findByRole('list', { name: 'Latest notifications' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(4)
    expect(within(list).getByRole('button', { name: /^Order #901 confirmed\. Please pay ₹177 in cash/ })).toBeInTheDocument()
    await user.click(within(list).getByRole('button', { name: /^Unread: Order #901 delivered\./ }))

    // The order page's code is loaded on this first visit, which can take a while when all test files run at once
    expect(await screen.findByRole('heading', { level: 1, name: 'Order #901' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/orders/901')
    expect(await screen.findByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()
  })

  it('marks all as read', async () => {
    logInAs()
    const { user } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))
    await user.click(await screen.findByRole('button', { name: 'Mark all as read' }))
    expect(await screen.findByRole('button', { name: 'Notifications, 0 unread' })).toBeInTheDocument()
  })

  it('shows only the unread ones, marks one as read with its own button and leads to all of them', async () => {
    logInAs()
    const { user, router } = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Notifications, 2 unread' }))
    await user.click(screen.getByRole('button', { name: 'Unread' }))

    const list = await screen.findByRole('list', { name: 'Latest notifications' })
    await waitFor(() => expect(within(list).getAllByRole('listitem')).toHaveLength(2))
    // The time is written "25 Oct, 11:04 am" or "Yesterday, ..."; the exact time is in the <time> title
    expect(within(list).getAllByRole('listitem')[0].querySelector('time')).toHaveAttribute('title')

    await user.click(within(list).getByRole('button', { name: 'Mark as read: Order #902 cancelled' }))
    expect(await screen.findByRole('button', { name: 'Notifications, 1 unread' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'View all notifications' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Notifications' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/account/notifications')
  })
})

describe('Notifications page', () => {
  it('lists every notification, filters the unread ones and marks them all as read', async () => {
    logInAs()
    const { user, router } = renderApp('/account/notifications')

    const list = await screen.findByRole('list', { name: 'Notifications' }, { timeout: 10_000 })
    expect(within(list).getAllByRole('listitem')).toHaveLength(4)
    expect(screen.getByText('4 notifications')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: /^Unread/ }))
    expect(router.state.location.search).toBe('?show=unread')
    await waitFor(() => expect(within(screen.getByRole('list', { name: 'Notifications' })).getAllByRole('listitem')).toHaveLength(2))

    await user.click(screen.getByRole('button', { name: 'Mark all as read' }))
    expect(await screen.findByRole('heading', { name: "You're all caught up" })).toBeInTheDocument()
  })

  it('is in the vendor panel too', async () => {
    logInAs('vendor1@grocery.com')
    renderApp('/vendor/notifications')
    expect(await screen.findByRole('heading', { level: 1, name: 'Notifications' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(await screen.findByRole('list', { name: 'Notifications' })).toBeInTheDocument()
  })
})

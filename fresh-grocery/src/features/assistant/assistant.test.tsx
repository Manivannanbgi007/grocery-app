import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { demoProductRows, toProduct } from '../../mocks/data/products'
import { chatConversation } from '../../mocks/mockChat'
import { server } from '../../mocks/server'
import { logInAs, renderApp } from '../../test/render'
import { toSavedProduct } from '../wishlist/useWishlist'

/*
 * The shopping assistant's chat in the shop (mock assistant). A conversation belongs to a guest or one user; its id is
 * kept in sessionStorage per login. customer1 = Priya (user 4): her newest order is 902.
 */

const GUEST_KEY = 'fresh-grocery.chat.guest'

async function openChat() {
  const { user, router } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  return { user, router, panel: screen.getByRole('dialog', { name: 'Shopping assistant' }) }
}

describe('Shopping assistant', () => {
  it('answers a guest, from an example and from a typed question', async () => {
    const { user, panel } = await openChat()
    expect(within(panel).getByText(/Ask me about our products, prices, offers or delivery/)).toBeInTheDocument()
    expect(within(panel).getByLabelText('Your question')).toHaveFocus()

    await user.click(within(panel).getByRole('button', { name: 'Is delivery free?' }))
    const log = within(panel).getByRole('log', { name: 'Chat' })
    expect(log).toHaveTextContent('Is delivery free?')
    expect(await within(log).findByText(/Delivery costs ₹30, and it is free for orders of ₹299 or more/)).toBeInTheDocument()
    expect(within(log).getByText("Checked against the shop's live data")).toBeInTheDocument()

    await user.type(within(panel).getByLabelText('Your question'), 'Do you have tomatoes?{Enter}')
    expect(await within(log).findByText(/Yes, we have Tomato/)).toBeInTheDocument()
    expect(within(panel).getByLabelText('Your question')).toHaveValue('')
    const conversationId = sessionStorage.getItem(GUEST_KEY)!
    expect(chatConversation(null, conversationId).messages).toHaveLength(4)
  })

  it('shows the conversation again after a reload, and starts a new chat', async () => {
    let chat = await openChat()
    await chat.user.click(within(chat.panel).getByRole('button', { name: 'Which offers are running?' }))
    expect(await within(chat.panel).findByText(/Running offers: WELCOME50/)).toBeInTheDocument()
    const conversationId = sessionStorage.getItem(GUEST_KEY)!

    // A reload: the page starts again, the id is still in sessionStorage
    cleanup()
    chat = await openChat()
    expect(await within(chat.panel).findByText(/Running offers: WELCOME50/)).toBeInTheDocument()

    await chat.user.click(within(chat.panel).getByRole('button', { name: 'New chat' }))
    expect(within(chat.panel).getByRole('list', { name: 'Example questions' })).toBeInTheDocument()
    expect(sessionStorage.getItem(GUEST_KEY)).toBeNull()
    await waitFor(() => expect(() => chatConversation(null, conversationId)).toThrow('Conversation not found'))
  })

  it("answers a customer about her orders, in her own conversation", async () => {
    sessionStorage.setItem(GUEST_KEY, '00000000-0000-4000-8000-000000000001')
    logInAs('customer1@grocery.com')
    const { user, panel } = await openChat()
    expect(within(panel).getByText(/Hi, Priya!/)).toBeInTheDocument()
    await user.click(within(panel).getByRole('button', { name: 'Where is my last order?' }))
    expect(await within(panel).findByText(/Your last order was number 902 from Fresh Mart/)).toBeInTheDocument()
    expect(sessionStorage.getItem('fresh-grocery.chat.4')).not.toBeNull()
  })

  it('answers about the wishlist, which the page sends along (it is kept in this browser)', async () => {
    const tomato = demoProductRows.find((row) => row.name === 'Tomato')!
    localStorage.setItem('fresh-grocery.wishlist', JSON.stringify([toSavedProduct(toProduct(tomato))]))
    const sentBodies: unknown[] = []
    const listen = ({ request }: { request: Request }) => {
      if (request.url.endsWith('/api/v1/chat/messages')) void request.clone().json().then((body) => sentBodies.push(body))
    }
    server.events.on('request:start', listen)

    const { user, panel } = await openChat()
    await user.type(within(panel).getByLabelText('Your question'), 'How many items are in my wishlist?{Enter}')
    expect(await within(panel).findByText('You have 1 product on your wishlist: Tomato.')).toBeInTheDocument()
    expect(within(panel).getByText("Checked against the shop's live data")).toBeInTheDocument()
    expect(sentBodies).toEqual([expect.objectContaining({ message: 'How many items are in my wishlist?', wishlist: ['Tomato'] })])
    server.events.removeListener('request:start', listen)
  })

  it('opens the page of a link under the answer; on a small screen the chat closes and keeps the conversation', async () => {
    logInAs('customer1@grocery.com')
    const { user, router, panel } = await openChat()
    await user.click(within(panel).getByRole('button', { name: 'Where is my last order?' }))
    const links = await within(panel).findByRole('group', { name: 'Pages for this answer' })
    expect(within(links).getAllByRole('link').map((link) => link.textContent)).toEqual(['Order #902', 'Your orders'])
    // The question and the answer are the chat's two list items (the e2e flows count messages this way)
    expect(within(within(panel).getByRole('log', { name: 'Chat' })).getAllByRole('listitem')).toHaveLength(2)

    await user.click(within(links).getByRole('link', { name: 'Order #902' }))
    // The order page loads on first use (lazy): the router moves there once it has loaded
    await waitFor(() => expect(router.state.location.pathname).toBe('/orders/902'), { timeout: 10_000 })
    // jsdom has no matchMedia: a small screen, where the chat would cover the page
    expect(screen.queryByRole('dialog', { name: 'Shopping assistant' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Ask our assistant' }))
    expect(await screen.findByText(/Your last order was number 902/)).toBeInTheDocument()
  })

  it('stays open next to the page on a wide screen', async () => {
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: query === '(min-width: 64rem)' }))
    const { user, router, panel } = await openChat()
    await user.click(within(panel).getByRole('button', { name: 'Which offers are running?' }))
    await user.click(await within(panel).findByRole('link', { name: 'Offers for you' }))

    await waitFor(() => expect(router.state.location).toMatchObject({ pathname: '/', hash: '#offers' }))
    expect(screen.getByRole('dialog', { name: 'Shopping assistant' })).toBeInTheDocument()
    vi.unstubAllGlobals()
  })

  it('minimizes and maximizes the chat, keeps the conversation, and remembers the size', async () => {
    const { user, panel } = await openChat()
    await user.click(within(panel).getByRole('button', { name: 'Is delivery free?' }))
    expect(await within(panel).findByText(/Delivery costs ₹30/)).toBeInTheDocument()
    // It starts as high as the screen
    expect(panel).toHaveAttribute('data-size', 'maximized')

    await user.click(within(panel).getByRole('button', { name: 'Minimize the chat' }))
    expect(panel).toHaveAttribute('data-size', 'minimized')
    expect(within(panel).getByText(/Delivery costs ₹30/)).toBeInTheDocument()
    expect(within(panel).getByLabelText('Your question')).toBeInTheDocument()

    // Closed and opened again: still minimized, with the same conversation
    await user.click(within(panel).getByRole('button', { name: 'Close the assistant' }))
    await user.click(screen.getByRole('button', { name: 'Ask our assistant' }))
    const reopened = screen.getByRole('dialog', { name: 'Shopping assistant' })
    expect(reopened).toHaveAttribute('data-size', 'minimized')
    expect(within(reopened).getByText(/Delivery costs ₹30/)).toBeInTheDocument()

    await user.click(within(reopened).getByRole('button', { name: 'Maximize the chat' }))
    expect(reopened).toHaveAttribute('data-size', 'maximized')
    expect(localStorage.getItem('fresh-grocery.chat-size')).toBe('maximized')
  })

  it('shows an error with "Try again", and Escape closes the chat', async () => {
    let refused = true
    server.use(
      http.post('*/api/v1/chat/messages', () => {
        if (!refused) return undefined
        refused = false
        // The gateway answers a full rate limit with 429 and an empty body
        return new HttpResponse(null, { status: 429 })
      }),
    )
    const { user, panel } = await openChat()
    await user.type(within(panel).getByLabelText('Your question'), 'hello{Enter}')
    expect(await within(panel).findByText('Too many requests. Please wait a moment and try again.')).toBeInTheDocument()
    await user.click(within(panel).getByRole('button', { name: 'Try again' }))
    expect(await within(panel).findByText(/Hello! I can help you find products/)).toBeInTheDocument()
    // The question is in the chat once
    expect(within(panel).getAllByText('hello')).toHaveLength(1)

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog', { name: 'Shopping assistant' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Ask our assistant' })).toHaveFocus()
  })

  it('shows a typing bubble while the answer comes, and copies an answer', async () => {
    let answer: () => void = () => {}
    const answered = new Promise<void>((resolve) => {
      answer = resolve
    })
    server.use(
      http.post('*/api/v1/chat/messages', async () => {
        await answered
        return undefined // on to the mock assistant
      }),
    )
    const { user, panel } = await openChat()
    await user.click(within(panel).getByRole('button', { name: 'Is delivery free?' }))

    const typing = within(panel).getByRole('status')
    expect(typing).toHaveTextContent('Looking it up… (this can take up to a minute)')
    answer()
    expect(await within(panel).findByText(/Delivery costs ₹30/)).toBeInTheDocument()
    expect(within(panel).queryByText(/Looking it up/)).not.toBeInTheDocument()

    // The log still has one item per message: the question and the answer
    const log = within(panel).getByRole('log', { name: 'Chat' })
    expect(within(log).getAllByRole('listitem')).toHaveLength(2)
    await user.click(within(log).getByRole('button', { name: 'Copy' }))
    expect(await navigator.clipboard.readText()).toMatch(/^Delivery costs ₹30/)
    expect(within(log).getByRole('button', { name: 'Copied' })).toBeInTheDocument()
  })

  it('is not offered to a vendor in the shop', async () => {
    logInAs('vendor1@grocery.com')
    renderApp('/')
    expect(await screen.findByRole('heading', { level: 1, name: 'Fresh vegetables, delivered today' }, { timeout: 10_000 })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ask our assistant' })).not.toBeInTheDocument()
  })
})

/*
 * The assistant in the panels: the same chat, with the panel's title, greeting and example questions; the (mock)
 * backend answers from that user's own data. vendor1 = Ravi (user 3, Fresh Mart), delivery1 = Arjun (user 5).
 */
async function openPanelChat(email: string, path: string, title: string) {
  logInAs(email)
  const { user } = renderApp(path)
  await user.click(await screen.findByRole('button', { name: 'Ask the assistant' }, { timeout: 10_000 }))
  return { user, panel: screen.getByRole('dialog', { name: title }) }
}

describe('Panel assistants', () => {
  it('answers a vendor about the store, in a conversation of its own', async () => {
    const { user, panel } = await openPanelChat('vendor1@grocery.com', '/vendor', 'Store assistant')
    expect(within(panel).getByRole('heading', { name: 'Hi, Ravi!' })).toBeInTheDocument()
    expect(within(panel).getByText(/^Ask me about your store/)).toBeInTheDocument()

    await user.click(within(panel).getByRole('button', { name: 'Do I have new orders?' }))

    expect(await within(panel).findByText(/Fresh Mart has \d+ new orders waiting for you to accept/)).toBeInTheDocument()
    expect(within(panel).getByText("Checked against your store's live data")).toBeInTheDocument()
    expect(sessionStorage.getItem('fresh-grocery.chat.3')).not.toBeNull()
    expect(screen.queryByRole('button', { name: 'Ask our assistant' })).not.toBeInTheDocument()
  })

  it('answers a delivery partner about the earnings', async () => {
    const { user, panel } = await openPanelChat('delivery1@grocery.com', '/delivery', 'Delivery assistant')

    await user.click(within(panel).getByRole('button', { name: 'How much did I earn this week?' }))

    expect(await within(panel).findByText(/In the last 7 days you made \d+ deliveries and earned ₹/)).toBeInTheDocument()
  })

  it('answers an admin about the waiting work', async () => {
    const { user, panel } = await openPanelChat('admin@grocery.com', '/admin', 'Admin assistant')
    expect(within(panel).getByLabelText('Your question')).toHaveAttribute('placeholder', 'Ask about orders, stores, partners or sales')

    await user.click(within(panel).getByRole('button', { name: 'What needs my attention today?' }))

    expect(await within(panel).findByText(/Waiting for the team: \d+ stores to approve, \d+ KYC to review/)).toBeInTheDocument()
  })
})

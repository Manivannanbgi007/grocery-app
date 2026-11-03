import { cleanup, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { DemoUser } from '../../mocks/data/users'
import { chatConversation } from '../../mocks/mockChat'
import { logInAs, renderApp } from '../../test/render'

/*
 * Human approval of the chat's cards (mock assistant). The assistant only prepares an order; a card waits for the
 * customer ("Pending Human Approval"), Confirm approves it ("Approved", by you, with the time), Cancel rejects it
 * ("Rejected"). The backend keeps the decision, so the chat history shows it again after a reload.
 * customer1 = Priya (user 4): her seed cart costs ₹231; her conversation id is kept under this key.
 */

const PRIYA = { id: 4 } as DemoUser
const PRIYA_CHAT_KEY = 'fresh-grocery.chat.4'

async function openTheChat() {
  logInAs('customer1@grocery.com')
  const { user } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  return { user, panel: screen.getByRole('dialog', { name: 'Shopping assistant' }) }
}

async function askForTheOrder() {
  const { user, panel } = await openTheChat()
  await user.type(within(panel).getByLabelText('Your question'), 'Place my order, cash on delivery{Enter}')
  const card = await within(panel).findByRole('region', { name: 'Order summary' })
  return { user, panel, card }
}

/** The card's approval as the (mock) backend keeps it */
function storedApproval() {
  const conversationId = sessionStorage.getItem(PRIYA_CHAT_KEY)!
  return chatConversation(PRIYA, conversationId).messages.find((message) => message.approval)!.approval!
}

/** A reload: the page starts again (the conversation id stays in sessionStorage), and the chat loads its history */
async function reloadAndOpenTheChat() {
  cleanup()
  const { user, panel } = await openTheChat()
  return { user, reloaded: await within(panel).findByRole('region', { name: 'Order summary' }) }
}

describe('Human approval in the chat', () => {
  it('shows that a new card waits for the customer', async () => {
    const { card } = await askForTheOrder()

    expect(within(card).getByText('Human approval')).toBeInTheDocument()
    expect(within(card).getByText('Pending Human Approval')).toBeInTheDocument()
    expect(storedApproval().status).toBe('PENDING')
  })

  it('shows Approved, by you and when, after Confirm, and still after a reload', async () => {
    const { user, card } = await askForTheOrder()

    await user.click(within(card).getByRole('button', { name: 'Confirm order' }))

    expect(await within(card).findByText('Approved')).toBeInTheDocument()
    expect(within(card).getByText(/^by you ·/)).toBeInTheDocument()
    expect(within(card).queryByText('Pending Human Approval')).not.toBeInTheDocument()
    await waitFor(() => expect(storedApproval().status).toBe('APPROVED'))
    expect(storedApproval().decidedByUserId).toBe(4)

    const { reloaded } = await reloadAndOpenTheChat()
    expect(within(reloaded).getByText('Approved')).toBeInTheDocument()
    expect(within(reloaded).getByText(/^by you ·/)).toBeInTheDocument()
    expect(within(reloaded).getByText('Confirmed: the order is placed.')).toBeInTheDocument()
    expect(within(reloaded).queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument()
  })

  it('shows Rejected after Cancel, and still after a reload', async () => {
    const { user, card } = await askForTheOrder()

    await user.click(within(card).getByRole('button', { name: 'Cancel' }))

    expect(await within(card).findByText('Rejected')).toBeInTheDocument()
    await waitFor(() => expect(storedApproval().status).toBe('REJECTED'))

    const { reloaded } = await reloadAndOpenTheChat()
    expect(within(reloaded).getByText('Rejected')).toBeInTheDocument()
    expect(within(reloaded).getByText('Cancelled: nothing was ordered.')).toBeInTheDocument()
  })

  it('shows a card from the history that nobody decided within 10 minutes as expired', async () => {
    await askForTheOrder()
    // The card was shown 20 minutes ago and nobody clicked
    storedApproval().createdAt = new Date(Date.now() - 20 * 60 * 1000).toISOString()

    const { reloaded } = await reloadAndOpenTheChat()
    expect(within(reloaded).getByText('Expired: not approved within 10 minutes')).toBeInTheDocument()
    expect(within(reloaded).queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument()
  })

  it('a card that still waits can be approved after a reload', async () => {
    await askForTheOrder()

    const { user, reloaded } = await reloadAndOpenTheChat()
    expect(within(reloaded).getByText('Pending Human Approval')).toBeInTheDocument()
    await user.click(within(reloaded).getByRole('button', { name: 'Confirm order' }))

    expect(await within(reloaded).findByText('Approved')).toBeInTheDocument()
    await waitFor(() => expect(storedApproval().status).toBe('APPROVED'))
  })
})

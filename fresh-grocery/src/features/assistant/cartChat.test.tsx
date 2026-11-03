import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import type { ChatAction } from '../../api/types'
import { addItem, clearCart, getCart, removeItem } from '../../mocks/mockCarts'
import { server } from '../../mocks/server'
import { logInAs, renderApp, renderWithProviders } from '../../test/render'
import { CARD_LIFETIME_MS, ChatActionCard } from './ChatActionCard'

/*
 * "Remove the tomatoes from my cart" in the shop's chat (mock assistant): the answer shows the cart's line with a Confirm
 * button, and only the customer's click takes it out. customer1 = Priya (user 4): her seed cart has Tomato x2, Amul
 * Taaza Toned Milk x3 and Farm Eggs x1 from Fresh Mart.
 */

const PRIYA = 4
const TOMATO = 1
/** Organic Spinach: a product of Green Basket, the other store */
const SPINACH = 11

const cartProducts = () => getCart(PRIYA).items.map((item) => item.productName)

async function askToRemoveTheTomatoes() {
  logInAs('customer1@grocery.com')
  const { user } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  const panel = screen.getByRole('dialog', { name: 'Shopping assistant' })
  await user.type(within(panel).getByLabelText('Your question'), 'Remove the tomatoes from my cart{Enter}')
  const card = await within(panel).findByRole('region', { name: 'Product to remove' })
  return { user, panel, card }
}

describe('Removing a product from the cart in the chat', () => {
  it('removes the product only when the customer presses Confirm removal', async () => {
    const { user, panel, card } = await askToRemoveTheTomatoes()
    expect(within(card).getByRole('heading', { name: 'Remove from your cart (Fresh Mart)?' })).toBeInTheDocument()
    expect(within(card).getByText('2 x Tomato (1 kg)')).toBeInTheDocument()
    expect(cartProducts()).toContain('Tomato') // the answer alone removes nothing

    await user.click(within(card).getByRole('button', { name: 'Confirm removal' }))

    expect(await within(panel).findByText('Removed Tomato (1 kg) from your cart. Your cart now has 2 products; the amount to pay is ₹159.')).toBeInTheDocument()
    expect(within(card).getByText('Done: the product is removed.')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: 'Confirm removal' })).not.toBeInTheDocument()
    expect(cartProducts()).toEqual(['Amul Taaza Toned Milk', 'Farm Eggs'])
  })

  it('removes nothing after Keep it', async () => {
    const { user, panel, card } = await askToRemoveTheTomatoes()

    await user.click(within(card).getByRole('button', { name: 'Keep it' }))

    expect(await within(panel).findByText('OK, Tomato (1 kg) stays in your cart.')).toBeInTheDocument()
    expect(within(card).getByText('Kept: your cart is unchanged.')).toBeInTheDocument()
    expect(cartProducts()).toContain('Tomato')
  })

  it('says so when the product is no longer in the cart, and offers no second try', async () => {
    const { user, card } = await askToRemoveTheTomatoes()
    removeItem(PRIYA, TOMATO) // e.g. removed on the cart page in another tab

    await user.click(within(card).getByRole('button', { name: 'Confirm removal' }))

    expect(await within(card).findByText('Tomato (1 kg) is no longer in your cart.')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: 'Confirm removal' })).not.toBeInTheDocument()
  })

  it('shows an error of the cart and lets the customer try again', async () => {
    let failFirst = true
    server.use(
      http.delete('*/api/v1/cart/items/:productId', () => {
        if (failFirst) {
          failFirst = false
          return HttpResponse.json({ success: false, message: 'The cart service is busy. Please try again.', data: null, errors: [] }, { status: 503 })
        }
        return undefined // on to the mock cart service
      }),
    )
    const { user, panel, card } = await askToRemoveTheTomatoes()

    await user.click(within(card).getByRole('button', { name: 'Confirm removal' }))
    expect(await within(card).findByRole('alert')).toHaveTextContent('The cart service is busy. Please try again.')
    expect(cartProducts()).toContain('Tomato')
    await user.click(within(card).getByRole('button', { name: 'Confirm removal' }))

    expect(await within(panel).findByText(/Removed Tomato \(1 kg\) from your cart/)).toBeInTheDocument()
    expect(cartProducts()).not.toContain('Tomato')
  })

  it('asks for a fresh summary after 10 minutes', () => {
    renderWithProviders(
      <ChatActionCard action={REMOVAL} actionKey="chat-card-0002" receivedAt={Date.now() - CARD_LIFETIME_MS} done={undefined} onDone={() => {}} />,
    )

    expect(screen.getByText('This summary is older than 10 minutes. Ask me again for a fresh one.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm removal' })).not.toBeInTheDocument()
  })
})

describe('Removing several products with one card', () => {
  const EGGS = 8
  /** "Remove the tomatoes and the eggs": one card with both of Priya's lines, as the backend sends it */
  const TWO_PRODUCTS: ChatAction = {
    kind: 'REMOVE_FROM_CART',
    store: 'Fresh Mart',
    items: [
      { name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72', productId: TOMATO },
      { name: 'Farm Eggs', packSize: '6 pcs', quantity: 1, lineTotal: '₹60', productId: EGGS },
    ],
  }

  function renderTheCard() {
    logInAs('customer1@grocery.com')
    const onDone = vi.fn()
    const { user } = renderWithProviders(
      <ChatActionCard action={TWO_PRODUCTS} actionKey="chat-card-0003" receivedAt={Date.now()} done={undefined} onDone={onDone} />,
    )
    return { user, onDone, card: screen.getByRole('region', { name: 'Products to remove' }) }
  }

  it('removes every product of the card with one Confirm, and says what the cart has now', async () => {
    const { user, onDone, card } = renderTheCard()
    expect(within(card).getByText('2 x Tomato (1 kg)')).toBeInTheDocument()
    expect(within(card).getByText('1 x Farm Eggs (6 pcs)')).toBeInTheDocument()
    expect(within(card).getByRole('button', { name: 'Keep them' })).toBeInTheDocument()
    expect(cartProducts()).toHaveLength(3) // the card alone removes nothing

    await user.click(within(card).getByRole('button', { name: 'Confirm removal' }))

    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1))
    const [result, line] = onDone.mock.calls[0]
    expect(result).toBe('CONFIRMED')
    expect(line.text).toMatch(/^Removed Tomato \(1 kg\) and Farm Eggs \(6 pcs\) from your cart\. Your cart now has 1 product; the amount to pay is ₹[\d.,]+\.$/)
    expect(cartProducts()).toEqual(['Amul Taaza Toned Milk'])
  })

  it('still removes the others when one product is gone already, and says which one was gone', async () => {
    const { user, onDone, card } = renderTheCard()
    removeItem(PRIYA, TOMATO) // e.g. removed on the cart page in another tab

    await user.click(within(card).getByRole('button', { name: 'Confirm removal' }))

    await waitFor(() => expect(onDone).toHaveBeenCalledTimes(1))
    expect(onDone.mock.calls[0][1].text).toMatch(/^Removed Farm Eggs \(6 pcs\) from your cart\. Tomato \(1 kg\) was no longer in your cart\. Your cart now has 1 product;/)
    expect(cartProducts()).toEqual(['Amul Taaza Toned Milk'])
  })

  it('says so when every product is gone already, and offers no second try', async () => {
    const { user, onDone, card } = renderTheCard()
    removeItem(PRIYA, TOMATO)
    removeItem(PRIYA, EGGS)

    await user.click(within(card).getByRole('button', { name: 'Confirm removal' }))

    expect(await within(card).findByText('Tomato (1 kg) and Farm Eggs (6 pcs) are no longer in your cart.')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: 'Confirm removal' })).not.toBeInTheDocument()
    expect(onDone).not.toHaveBeenCalled()
  })
})

async function ask(question: string, cardName: string) {
  logInAs('customer1@grocery.com')
  const { user } = renderApp('/')
  await user.click(await screen.findByRole('button', { name: 'Ask our assistant' }, { timeout: 10_000 }))
  const panel = screen.getByRole('dialog', { name: 'Shopping assistant' })
  await user.type(within(panel).getByLabelText('Your question'), `${question}{Enter}`)
  const card = await within(panel).findByRole('region', { name: cardName })
  return { user, panel, card }
}

const tomatoes = () => getCart(PRIYA).items.find((item) => item.productId === TOMATO)?.quantity

describe('Adding to the cart in the chat', () => {
  it('adds the product only when the customer presses Confirm and add', async () => {
    const { user, panel, card } = await ask('Add 2 bananas to my cart', 'Product to add')
    expect(within(card).getByRole('heading', { name: 'Add to your cart (Fresh Mart)?' })).toBeInTheDocument()
    expect(within(card).getByText(/^2 x Banana Robusta/)).toBeInTheDocument()
    expect(cartProducts()).not.toContain('Banana Robusta') // the answer alone adds nothing

    await user.click(within(card).getByRole('button', { name: 'Confirm and add' }))

    expect(await within(panel).findByText(/^Added 2 x Banana Robusta .* to your cart\. Your cart now has 4 products; the amount to pay is ₹\d+/)).toBeInTheDocument()
    expect(within(card).getByText('Done: the product is added.')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: 'Confirm and add' })).not.toBeInTheDocument()
    expect(getCart(PRIYA).items.find((item) => item.productName === 'Banana Robusta')?.quantity).toBe(2)
  })

  it('adds a product of the cart up to the total the card showed, so it is never added twice', async () => {
    const { user, card } = await ask('Add 1 tomato to my cart', 'Product to add')
    expect(within(card).getByText('Your cart will then have 3 x Tomato (1 kg).')).toBeInTheDocument()

    await user.click(within(card).getByRole('button', { name: 'Confirm and add' }))
    await within(card).findByText('Done: the product is added.')

    expect(tomatoes()).toBe(3)
  })

  it('adds nothing after Don\'t add', async () => {
    const { user, panel, card } = await ask('Add 2 bananas to my cart', 'Product to add')

    await user.click(within(card).getByRole('button', { name: "Don't add" }))

    expect(await within(panel).findByText('OK, nothing was added to your cart.')).toBeInTheDocument()
    expect(within(card).getByText('Not added: your cart is unchanged.')).toBeInTheDocument()
    expect(cartProducts()).not.toContain('Banana Robusta')
  })

  it("says cart-service's no at the click, and offers no second try", async () => {
    const { user, card } = await ask('Add 2 bananas to my cart', 'Product to add')
    clearCart(PRIYA)
    addItem(PRIYA, SPINACH, 1, false) // meanwhile the cart got another store's product (e.g. in another tab)

    await user.click(within(card).getByRole('button', { name: 'Confirm and add' }))

    expect(await within(card).findByRole('alert')).toHaveTextContent('Your cart has items from another store.')
    expect(within(card).queryByRole('button', { name: 'Confirm and add' })).not.toBeInTheDocument()
    expect(cartProducts()).not.toContain('Banana Robusta')
  })
})

describe('Changing an amount in the chat', () => {
  it('changes the amount only when the customer presses Confirm change', async () => {
    const { user, panel, card } = await ask('Make it 5 tomatoes', 'Change in the cart')
    expect(within(card).getByRole('heading', { name: 'Change the amount of Tomato (1 kg)?' })).toBeInTheDocument()
    expect(within(card).getByText('2 x Tomato (1 kg), ₹72')).toBeInTheDocument()
    expect(within(card).getByText('5 x Tomato (1 kg)')).toBeInTheDocument()
    expect(tomatoes()).toBe(2) // the answer alone changes nothing

    await user.click(within(card).getByRole('button', { name: 'Confirm change' }))

    expect(await within(panel).findByText(/^Changed Tomato \(1 kg\) to 5\. Your cart now has 3 products; the amount to pay is ₹\d+\.$/)).toBeInTheDocument()
    expect(within(card).getByText('Done: the amount is changed.')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: 'Confirm change' })).not.toBeInTheDocument()
    expect(tomatoes()).toBe(5)
  })

  it('changes nothing after Keep it, and says when the product is gone meanwhile', async () => {
    const { user, panel, card } = await ask('Make it 5 tomatoes', 'Change in the cart')
    await user.click(within(card).getByRole('button', { name: 'Keep it' }))
    expect(await within(panel).findByText('OK, Tomato (1 kg) stays at 2.')).toBeInTheDocument()
    expect(tomatoes()).toBe(2)

    await user.type(within(panel).getByLabelText('Your question'), 'Make it 4 tomatoes{Enter}')
    await waitFor(() => expect(within(panel).getAllByRole('region', { name: 'Change in the cart' })).toHaveLength(2))
    const second = within(panel).getAllByRole('region', { name: 'Change in the cart' })[1]
    removeItem(PRIYA, TOMATO) // e.g. removed on the cart page in another tab
    await user.click(within(second).getByRole('button', { name: 'Confirm change' }))
    expect(await within(second).findByText('Tomato (1 kg) is no longer in your cart.')).toBeInTheDocument()
    expect(tomatoes()).toBeUndefined()
  })
})

describe('Emptying the cart in the chat', () => {
  it('empties the cart only when the customer presses Empty cart', async () => {
    const { user, panel, card } = await ask('Empty my cart', 'Cart to empty')
    expect(within(card).getByText('₹231')).toBeInTheDocument()
    expect(cartProducts()).toHaveLength(3) // the answer alone removes nothing

    await user.click(within(card).getByRole('button', { name: 'Empty cart' }))

    expect(await within(panel).findByText('Done. Your cart is now empty.')).toBeInTheDocument()
    expect(within(card).getByText('Done: the cart is empty.')).toBeInTheDocument()
    expect(cartProducts()).toEqual([])
    expect(await screen.findByRole('link', { name: 'Cart, 0 items' })).toBeInTheDocument()
  })

  it('empties nothing after Keep it', async () => {
    const { user, panel, card } = await ask('Empty my cart', 'Cart to empty')

    await user.click(within(card).getByRole('button', { name: 'Keep it' }))

    expect(await within(panel).findByText('OK, your cart stays as it is.')).toBeInTheDocument()
    expect(within(card).getByText('Kept: your cart is unchanged.')).toBeInTheDocument()
    expect(cartProducts()).toHaveLength(3)
  })
})

/** A removal card of Priya's tomatoes, as the backend sends it */
const REMOVAL: ChatAction = {
  kind: 'REMOVE_FROM_CART',
  store: 'Fresh Mart',
  items: [{ name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72', productId: TOMATO }],
}

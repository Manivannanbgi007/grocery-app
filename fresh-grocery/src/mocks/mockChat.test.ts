import { describe, expect, it } from 'vitest'
import { demoUsers } from './data/users'
import { addItem, checkout, getCart } from './mockCarts'
import { chatAnswer, chatConversation } from './mockChat'
import { getOrder, placeOrder } from './mockOrders'

/*
 * The mock assistant changes a customer's cart like the real addToMyCart tool: through the cart, with its rules. Orders
 * and cancellations it only prepares, like the real prepare tools: the chat's Confirm button does them.
 */

const priya = demoUsers.find((user) => user.email === 'customer1@grocery.com')! // a seed cart of Fresh Mart
const rahul = demoUsers.find((user) => user.email === 'customer2@grocery.com')! // no cart

describe('mock chat and the cart', () => {
  it('"add 2 tomatoes to my cart" adds nothing: the answer carries the product for the Confirm button', () => {
    const reply = chatAnswer(rahul, { message: 'Add 2 tomatoes to my cart' })

    expect(reply.answer).toBe(
      'Here is the product to add to your cart: 2 x Tomato (1 kg) for ₹72. Please check it and press Confirm to add it; nothing is added before that.',
    )
    expect(reply.grounded).toBe(true)
    expect(reply.toolsUsed).toEqual(['prepareMyCartAdd'])
    expect(reply.links).toEqual([{ label: 'Your cart', path: '/cart' }])
    expect(reply.action).toEqual({
      kind: 'ADD_TO_CART',
      store: 'Fresh Mart',
      items: [{ name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72', productId: 1, newQuantity: 2 }],
    })
    expect(getCart(rahul.id).items).toEqual([]) // nothing added yet
    // a product in the cart already: the card says how many the cart has after the click
    expect(chatAnswer(priya, { message: 'Add 1 tomato to my cart' }).action?.items[0]).toMatchObject({ quantity: 1, newQuantity: 3 })
  })

  it('says the shop\'s "no" before the click, and the cart stays as it was', () => {
    expect(chatAnswer(rahul, { message: 'Add 1 pomegranate to my cart' })).toMatchObject({
      answer: expect.stringMatching(/^Pomegranate \(.+\) is out of stock\.$/),
      action: null,
    })
    expect(chatAnswer(priya, { message: 'Please add organic spinach to my cart' }).answer).toBe(
      'The cart has products from Fresh Mart, and Organic Spinach is sold by Green Basket. A cart can hold the products of one store only: place the order for this cart first, or empty the cart.',
    )
    expect(getCart(rahul.id).items).toEqual([])
    expect(getCart(priya.id).items.map((item) => item.productName)).not.toContain('Organic Spinach')
  })

  it('"place my order" orders nothing: the answer carries the summary for the Confirm button', () => {
    const reply = chatAnswer(priya, { message: 'Place my order, cash on delivery' })

    expect(reply.toolsUsed).toEqual(['prepareMyOrder'])
    expect(reply.action).toMatchObject({
      kind: 'PLACE_ORDER',
      itemsTotal: '₹201',
      deliveryFee: '₹30',
      payable: '₹231',
      paymentMethod: 'COD',
      payment: 'Cash on delivery',
      addressId: 1,
      checkoutFingerprint: checkout(priya.id).checkoutFingerprint,
    })
    expect(reply.action?.items[0]).toEqual({ name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72' })
    expect(getCart(priya.id).items).toHaveLength(3) // still the whole cart: nothing was ordered

    expect(chatAnswer(priya, { message: 'Place my order' }).answer).toBe(
      'How would you like to pay: cash on delivery, or online (UPI or card) right after confirming?',
    )
    expect(chatAnswer(rahul, { message: 'place my order, cash' }).answer).toBe('Your cart cannot be ordered yet: Your cart is empty.')
  })

  it('"cancel my order" cancels nothing: the answer carries the order for the Confirm button', () => {
    const placed = placeOrder(priya.id, { addressId: 1, paymentMethod: 'COD', note: null })

    const reply = chatAnswer(priya, { message: 'Please cancel my order' })

    expect(reply.answer).toBe('Here is the order you want to cancel. Please check it and press Confirm to cancel it; nothing is cancelled before that.')
    expect(reply.toolsUsed).toEqual(['prepareMyOrderCancellation'])
    expect(reply.action).toMatchObject({ kind: 'CANCEL_ORDER', orderId: placed.id, payable: '₹231', refund: 'Nothing was paid yet (cash on delivery).', reason: null })
    expect(reply.action?.items[0]).toEqual({ name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72' })
    expect(getOrder(priya.id, placed.id).status).not.toBe('CANCELLED')
  })

  it('"remove X from my cart" removes nothing: the answer carries the cart\'s line for the Confirm button', () => {
    const reply = chatAnswer(priya, { message: 'Can you remove the tomatoes from my cart?' })

    expect(reply.answer).toBe(
      'Here is the product you want to take out of your cart. Please check it and press Confirm to remove it; nothing is removed before that.',
    )
    expect(reply.toolsUsed).toEqual(['prepareMyCartRemoval'])
    expect(reply.action).toEqual({
      kind: 'REMOVE_FROM_CART',
      store: 'Fresh Mart',
      items: [{ name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72', productId: 1 }],
    })
    expect(getCart(priya.id).items).toHaveLength(3) // still the whole cart: nothing was removed
  })

  it('says when the product is not in the cart or the cart is empty, and asks which one when several match', () => {
    expect(chatAnswer(priya, { message: 'remove the harpic from my cart' })).toMatchObject({
      answer: 'Your cart has no such product. It has: Tomato (1 kg), Amul Taaza Toned Milk (500 ml), Farm Eggs (6 pcs).',
      action: null,
    })
    expect(chatAnswer(rahul, { message: 'Remove the tomatoes from my cart' }).answer).toBe('Your cart is empty.')
    expect(chatAnswer(null, { message: 'Remove the tomatoes from my cart' }).answer).toBe('Please log in on the website to change your cart.')

    addItem(priya.id, 7, 1, false) // Amul Butter next to Amul Taaza Toned Milk
    expect(chatAnswer(priya, { message: 'Delete the amul from my cart' })).toMatchObject({
      answer: 'Which one do you want to take out of your cart: Amul Taaza Toned Milk (500 ml) or Amul Butter (100 g)?',
      action: null,
    })
  })

  it('"make it 5 tomatoes" and "empty my cart" change nothing: the answers carry their cards for the Confirm button', () => {
    const change = chatAnswer(priya, { message: 'Make it 5 tomatoes' })
    expect(change.toolsUsed).toEqual(['prepareMyCartQuantity'])
    expect(change.action).toEqual({
      kind: 'SET_CART_QUANTITY',
      store: 'Fresh Mart',
      items: [{ name: 'Tomato', packSize: '1 kg', quantity: 2, lineTotal: '₹72', productId: 1 }],
      newQuantity: 5,
    })
    expect(chatAnswer(priya, { message: 'make it 2 tomatoes' })).toMatchObject({
      answer: 'The cart already has 2 x Tomato (1 kg); nothing to change.',
      action: null,
    })

    const empty = chatAnswer(priya, { message: 'Please empty my cart' })
    expect(empty.answer).toBe('Here is your cart. Please check it and press Confirm to empty it; nothing is removed before that.')
    expect(empty.action).toMatchObject({ kind: 'CLEAR_CART', store: 'Fresh Mart', payable: '₹231' })
    expect(empty.action?.items).toHaveLength(3)
    expect(chatAnswer(rahul, { message: 'Empty my cart' }).answer).toBe('Your cart is empty already, so there is nothing to remove.')

    expect(getCart(priya.id).items.map((item) => [item.productName, item.quantity])).toEqual([
      ['Tomato', 2],
      ['Amul Taaza Toned Milk', 3],
      ['Farm Eggs', 1],
    ]) // nothing changed
  })

  it('asks which order when several can be cancelled', () => {
    const first = placeOrder(priya.id, { addressId: 1, paymentMethod: 'COD', note: null })
    addItem(priya.id, 1, 3, false) // 3 x Tomato: ₹108 + ₹30 delivery
    const second = placeOrder(priya.id, { addressId: 1, paymentMethod: 'COD', note: null })

    const reply = chatAnswer(priya, { message: 'cancel my order' })

    expect(reply.answer).toBe(`Several orders can still be cancelled: #${second.id} (₹138), #${first.id} (₹231). Which one should I cancel?`)
    expect(reply.action).toBeNull()
  })

  it("says when an order can no longer be cancelled, and never shows another customer's order", () => {
    expect(chatAnswer(priya, { message: 'cancel my order' }).answer).toBe('None of your recent orders can still be cancelled.')
    expect(chatAnswer(priya, { message: 'cancel order 901' }).answer).toBe('Order #901 can no longer be cancelled: it is delivered.')
    expect(chatAnswer(priya, { message: 'Cancel order #902' }).answer).toBe('Order #902 was cancelled earlier, so there is nothing to cancel.')
    const anothers = chatAnswer(priya, { message: 'cancel order 903' }) // customer3's order
    expect(anothers.answer).toBe("I couldn't find order #903 among your orders.")
    expect(anothers.action).toBeNull()
    expect(chatAnswer(null, { message: 'cancel my order' }).answer).toBe('Please log in on the website to cancel an order.')
  })

  it("writes a card the customer confirmed into the customer's conversation before the question, like the real service", () => {
    const reply = chatAnswer(priya, { message: 'Hi', confirmed: [{ kind: 'PLACE_ORDER', orderId: 1001 }] })

    expect(chatConversation(priya, reply.conversationId).messages.map((line) => line.text)).toEqual([
      'Order #1001 placed with the Confirm button.',
      'Hi',
      reply.answer,
    ])
    const guest = chatAnswer(null, { message: 'Hi', confirmed: [{ kind: 'PLACE_ORDER', orderId: 1001 }] })
    expect(chatConversation(null, guest.conversationId).messages).toHaveLength(2) // the question and the answer only
    expect(() => chatAnswer(priya, { message: 'Hi', confirmed: [{ kind: 'PAY_ORDER' as never, orderId: 1 }] })).toThrow()
  })

  it('asks a guest to log in, and says when it does not know the product', () => {
    expect(chatAnswer(null, { message: 'Add 2 tomatoes to my cart' })).toMatchObject({
      answer: 'Please log in on the website to change your cart.',
      grounded: false,
    })
    expect(chatAnswer(rahul, { message: 'Add a xyzzy to my cart' }).answer).toBe(
      "I couldn't find that product in the shop, so nothing was prepared.",
    )
  })
})

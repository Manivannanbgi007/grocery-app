import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import { apiLogin, CUSTOMER2, emptyCart, GATEWAY, logIn, skipSalePopup } from './helpers'

/*
 * Smoke flow 31: customer2 shops in the chat with the real assistant (mcp-client-service + mcp-server-service + Ollama),
 * and nothing happens without a click:
 * - "Add 3 kg of tomatoes to my cart" shows the card to add; only its Confirm adds, and the cart badge shows it at once;
 * - "Remove the tomatoes from my cart" shows the card to take them out: "Keep it" changes nothing, Confirm removes;
 * - "Place my order, cash on delivery" shows the order card; only its Confirm places the order;
 * - "Cancel my last order" shows the cancellation card (the website told the assistant about the order it placed);
 *   only its Confirm cancels the order;
 * - an online order: the line after Confirm has "Pay Now", which opens the page order-service named for the payment.
 * The language model runs on a CPU: each answer takes 15-60 s, so the flow takes up to 6 minutes. The model's words
 * differ from run to run; the flow checks the cards, the lines the website writes after a click, and the backend's data.
 * The orders the flow placed are cancelled and the cart is emptied at the end, also after a failure.
 */

test.setTimeout(480_000)

/** customer2's newest order before the flow: every order with a higher number was placed by it */
let newestBefore = 0

test.afterEach(async ({ request }) => {
  const headers = await apiLogin(request, CUSTOMER2)
  for (const order of await newestOrders(request)) {
    // Fails with 400 when the flow cancelled it already: that is fine
    if (order.id > newestBefore) await request.post(`${GATEWAY}/api/v1/orders/${order.id}/cancel`, { headers, data: { reason: 'Playwright clean-up' } })
  }
  await emptyCart(request, CUSTOMER2)
})

/** customer2's 5 newest orders, newest first */
async function newestOrders(request: APIRequestContext): Promise<{ id: number; status: string }[]> {
  const page = await request.get(`${GATEWAY}/api/v1/orders?size=5`, { headers: await apiLogin(request, CUSTOMER2) })
  expect(page.ok(), 'the orders can be read (is order-service running?)').toBeTruthy()
  return (await page.json()).data.content
}

/** The card to add under the answer: the answer alone adds nothing, its Confirm adds (and the badge shows it) */
async function confirmTheAddCard(page: Page, chat: Locator, request: APIRequestContext) {
  const addCard = chat.getByRole('region', { name: 'Product to add' }).last()
  await expect(addCard.getByText(/^3 x Tomato/), 'the card to add is under the answer').toBeVisible()
  expect(await cartLines(request), 'nothing added before the click').toEqual([])
  await addCard.getByRole('button', { name: 'Confirm and add' }).click()
  await expect(addCard.getByText('Done: the product is added.')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByRole('link', { name: 'Cart, 1 item' })).toBeVisible()
}

/** customer2's cart as cart-service has it: product and packs */
async function cartLines(request: APIRequestContext): Promise<[string, number][]> {
  const cart = await request.get(`${GATEWAY}/api/v1/cart`, { headers: await apiLogin(request, CUSTOMER2) })
  return (await cart.json()).data.items.map((item: { productName: string; quantity: number }) => [item.productName, item.quantity])
}

/** Asks the chat a question and waits for the answer (or fails on "Try again"). */
async function ask(chat: Locator, question: string) {
  const lines = chat.getByRole('log', { name: 'Chat' }).getByRole('listitem')
  const before = await lines.count()
  await chat.getByLabel('Your question').fill(question)
  await chat.getByLabel('Your question').press('Enter')
  await expect(lines.or(chat.getByRole('button', { name: 'Try again' }))).toHaveCount(before + 2, { timeout: 150_000 })
  await expect(chat.getByRole('button', { name: 'Try again' }), 'the assistant answered (are Ollama and the mcp services running?)').toHaveCount(0)
}

test('customer2 changes the cart, orders and cancels in the chat, and only the clicks order and cancel', async ({ page, request }) => {
  await emptyCart(request, CUSTOMER2)
  newestBefore = (await newestOrders(request))[0]?.id ?? 0
  await skipSalePopup(page)
  await page.goto('/login')
  await logIn(page, CUSTOMER2)
  await expect(page.getByRole('button', { name: `Account of ${CUSTOMER2.firstName} Verma` })).toBeVisible()
  await page.getByRole('button', { name: 'Ask our assistant' }).click()
  const chat = page.getByRole('dialog', { name: 'Shopping assistant' })

  // Adding: the card, and only its Confirm adds
  await ask(chat, 'Add 3 kg of tomatoes to my cart')
  await confirmTheAddCard(page, chat, request)

  // The order card: the answer alone orders nothing
  await ask(chat, 'Place my order, cash on delivery')
  const orderCard = chat.getByRole('region', { name: 'Order summary' })
  await expect(orderCard.getByText('Cash on delivery'), 'the order card is under the answer').toBeVisible()
  expect((await newestOrders(request))[0]?.id ?? 0, 'no order before the click').toBe(newestBefore)

  await orderCard.getByRole('button', { name: 'Confirm order' }).click()
  const placed = chat.getByText(/^Order #\d+ placed\. Please pay ₹[\d,]+ in cash when it is delivered\.$/)
  await expect(placed).toBeVisible({ timeout: 30_000 })
  const orderId = Number(/#(\d+)/.exec((await placed.textContent()) ?? '')![1])
  expect(orderId).toBeGreaterThan(newestBefore)
  await expect(orderCard.getByText('Confirmed: the order is placed.')).toBeVisible()

  // The cancellation card for that order: the answer alone cancels nothing
  await ask(chat, 'Cancel my last order')
  const cancelCard = chat.getByRole('region', { name: 'Order to cancel' })
  await expect(cancelCard.getByRole('heading', { name: new RegExp(`^Cancel order #${orderId}\\b`) }), 'the cancellation card is under the answer').toBeVisible()
  expect((await newestOrders(request)).find((order) => order.id === orderId)?.status).not.toBe('CANCELLED')

  await cancelCard.getByRole('button', { name: 'Confirm cancellation' }).click()
  await expect(chat.getByText(`Order #${orderId} is cancelled.`)).toBeVisible({ timeout: 30_000 })
  await expect(cancelCard.getByText('Done: the order is cancelled.')).toBeVisible()

  // The order page says so too
  await chat.getByRole('link', { name: `Order #${orderId}` }).last().click()
  await expect(page.getByRole('heading', { name: 'Order cancelled' })).toBeVisible()
})

test('customer2 orders online in the chat, and Pay Now opens the payment of that order', async ({ page, request }) => {
  await emptyCart(request, CUSTOMER2)
  newestBefore = (await newestOrders(request))[0]?.id ?? 0
  await skipSalePopup(page)
  await page.goto('/login')
  await logIn(page, CUSTOMER2)
  await expect(page.getByRole('button', { name: `Account of ${CUSTOMER2.firstName} Verma` })).toBeVisible()
  await page.getByRole('button', { name: 'Ask our assistant' }).click()
  const chat = page.getByRole('dialog', { name: 'Shopping assistant' })

  await ask(chat, 'Add 3 kg of tomatoes to my cart')
  await confirmTheAddCard(page, chat, request)
  await ask(chat, 'Place my order, pay online')
  const orderCard = chat.getByRole('region', { name: 'Order summary' })
  await expect(orderCard.getByText('Online (UPI or card), right after confirming'), 'the order card is under the answer').toBeVisible()

  await orderCard.getByRole('button', { name: 'Confirm order' }).click()
  const placed = chat.getByText(/^Order #\d+ placed\. Please pay ₹[\d,.]+ online on the order page within 15 minutes; the order is confirmed after the payment\.$/)
  await expect(placed).toBeVisible({ timeout: 30_000 })
  const orderId = Number(/#(\d+)/.exec((await placed.textContent()) ?? '')![1])
  expect(orderId).toBeGreaterThan(newestBefore)

  // Pay Now goes where order-service said (its paymentUrl), and that page takes the payment
  const payNow = chat.getByRole('link', { name: 'Pay Now' })
  await expect(payNow).toHaveAttribute('href', `/orders/${orderId}`)
  await payNow.click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}$`))
  await expect(page.getByRole('heading', { level: 1, name: `Order #${orderId}` })).toBeVisible()
  const payment = page.getByRole('region', { name: 'Payment' })
  await expect(payment.getByRole('button', { name: /^Pay ₹/ }), 'the payment opens once the items are reserved').toBeVisible({ timeout: 30_000 })
  await expect(payment.getByRole('timer')).toBeVisible() // "Time left to pay"
  // Not paid: the clean-up cancels the order
})

test('customer2 adds and removes in the chat, and only the Confirm clicks change the cart', async ({ page, request }) => {
  await emptyCart(request, CUSTOMER2)
  await skipSalePopup(page)
  await page.goto('/login')
  await logIn(page, CUSTOMER2)
  await expect(page.getByRole('button', { name: `Account of ${CUSTOMER2.firstName} Verma` })).toBeVisible()
  await page.getByRole('button', { name: 'Ask our assistant' }).click()
  const chat = page.getByRole('dialog', { name: 'Shopping assistant' })

  await ask(chat, 'Add 3 kg of tomatoes to my cart')
  await confirmTheAddCard(page, chat, request)
  expect(await cartLines(request)).toEqual([['Tomato', 3]])

  // The card to take them out: "Keep it" changes nothing
  await ask(chat, 'Remove the tomatoes from my cart')
  const keepCard = chat.getByRole('region', { name: 'Product to remove' }).last()
  await expect(keepCard.getByText(/^3 x Tomato/), 'the card to remove is under the answer').toBeVisible()
  await keepCard.getByRole('button', { name: 'Keep it' }).click()
  await expect(keepCard.getByText('Kept: your cart is unchanged.')).toBeVisible()
  expect(await cartLines(request)).toEqual([['Tomato', 3]])

  // Asked again: only Confirm removes
  await ask(chat, 'Remove the tomatoes from my cart')
  const removeCard = chat.getByRole('region', { name: 'Product to remove' }).last()
  await removeCard.getByRole('button', { name: 'Confirm removal' }).click()
  await expect(removeCard.getByText('Done: the product is removed.')).toBeVisible({ timeout: 30_000 })
  await expect(page.getByRole('link', { name: 'Cart, 0 items' })).toBeVisible()
  expect(await cartLines(request)).toEqual([])
})

import type { Address, ChatAction, ChatApproval, ChatConversation, ChatLink, ChatReply, ConfirmedCard, Order, Product } from '../api/types'
import { formatPrice, formatUnit } from '../utils/format'
import type { DemoUser } from './data/users'
import { MockApiError, validationFailed } from './mockApiError'
import { checkout, getCart, MAX_QUANTITY } from './mockCarts'
import { catalogProducts } from './mockCatalog'
import { getAddresses } from './mockCustomers'
import { publicCoupons } from './mockCoupons'
import { getOrder, listOrders } from './mockOrders'
import { panelAnswer } from './mockPanelChat'
import { currentSettings } from './mockPlatform'
import { storeById } from './mockStores'

/*
 * A small rule-based stand-in for the AI shopping assistant (mcp-client-service), answering from the mock data: product
 * prices and stock, the running offers, the delivery rules, the customer's last order, and "add 2 tomatoes to my cart".
 * Same JSON, owner rule (a conversation belongs to its guest or user) and errors as the real service; the answers are
 * simpler, of course.
 */

const MAX_LENGTH = 500
const MAX_WISHLIST_ITEMS = 50
const MAX_CONFIRMED = 5
const KEPT_MESSAGES = 20
const NOT_FOUND = 'Conversation not found. Start a new conversation by sending a message without conversationId.'

interface MockConversation {
  ownerUserId: number | null
  messages: ChatConversation['messages']
}

let conversations = new Map<string, MockConversation>()
/** The cards' human approvals by id, with their conversation (chat_approvals) */
let approvals = new Map<string, { approval: ChatApproval; conversationId: string }>()

function findConversation(conversationId: string, user: DemoUser | null): MockConversation {
  const conversation = conversations.get(conversationId)
  if (!conversation || conversation.ownerUserId !== (user?.id ?? null)) throw new MockApiError(404, NOT_FOUND)
  return conversation
}

/**
 * `links`: the pages that fit the answer, as the real service makes them from its tool results (AnswerLinks);
 * `action`: a summary to confirm with a button (ChatActions), only for "add X to my cart", "place my order", "cancel my
 * order", "remove X from my cart", "make it 3 tomatoes" and "empty my cart".
 */
const reply = (answer: string, grounded: boolean, toolsUsed: string[], links: ChatLink[] = [], action: ChatAction | null = null) => ({
  answer,
  grounded,
  toolsUsed,
  links,
  action,
})

const CART_LINK: ChatLink = { label: 'Your cart', path: '/cart' }
/** Words of "add 2 tomatoes to my cart" that name no product */
const CART_WORDS = new Set(['add', 'put', 'please', 'into', 'cart', 'basket'])

/** The first product with a name word that starts like one of the words ("tomatoes" -> Tomato) */
function productNamedIn(words: string[]): Product | undefined {
  return catalogProducts().find((candidate) =>
    words.some((word) => candidate.name.toLowerCase().split(/\s+/).some((part) => part.startsWith(word.replace(/e?s$/, '')))),
  )
}

/**
 * "Add 2 tomatoes to my cart": the real assistant's prepareMyCartAdd. It adds NOTHING: the answer carries the product,
 * its packs and their price, and the chat's Confirm button adds it. Like the real tool it says the shop's "no" before
 * the click (another store's product, the stock, at most 20 packs). Simpler than the real tool: the number counts packs
 * (the real one also turns "2 kg" into packs, and asks when a product or an amount is not clear).
 */
function cartAnswer(text: string, user: DemoUser | null): Omit<ChatReply, 'conversationId'> {
  if (!user || user.role !== 'CUSTOMER') return reply('Please log in on the website to change your cart.', false, [])
  const tools = ['prepareMyCartAdd']
  const product = productNamedIn((text.match(/[a-z]{3,}/g) ?? []).filter((word) => !CART_WORDS.has(word)))
  if (!product) return reply("I couldn't find that product in the shop, so nothing was prepared.", true, tools)
  const packs = Number(text.match(/\b(\d{1,2})\b/)?.[1] ?? 1)
  const label = `${product.name} (${formatUnit(product.unit, product.unitQuantity)})`
  const cart = getCart(user.id)
  if (cart.items.length > 0 && cart.storeId !== product.storeId) {
    const cartStore = cart.storeId === null ? 'another store' : (storeById(cart.storeId)?.name ?? 'another store')
    return reply(
      `The cart has products from ${cartStore}, and ${product.name} is sold by ${storeById(product.storeId)?.name ?? 'another store'}. A cart can hold the products of one store only: place the order for this cart first, or empty the cart.`,
      true,
      tools,
      [CART_LINK],
    )
  }
  const inCart = cart.items.find((item) => item.productId === product.id)?.quantity ?? 0
  const newQuantity = inCart + packs
  if (newQuantity > MAX_QUANTITY) {
    return reply(`The cart already has ${inCart} x ${label}. A cart holds at most ${MAX_QUANTITY} packs of one product.`, true, tools, [CART_LINK])
  }
  if (product.availableQuantity < newQuantity) {
    const why = product.availableQuantity === 0 ? `${label} is out of stock.` : `Only ${product.availableQuantity} packs of ${label} are left.`
    return reply(why, true, tools, [CART_LINK])
  }
  const price = formatPrice(product.sellingPrice * packs)
  const makes = newQuantity > packs ? ` (that makes ${newQuantity} in your cart)` : ''
  return reply(
    `Here is the product to add to your cart: ${packs} x ${label} for ${price}${makes}. Please check it and press Confirm to add it; nothing is added before that.`,
    true,
    tools,
    [CART_LINK],
    {
      kind: 'ADD_TO_CART',
      store: storeById(product.storeId)?.name,
      items: [
        { name: product.name, packSize: formatUnit(product.unit, product.unitQuantity), quantity: packs, lineTotal: price, productId: product.id, newQuantity },
      ],
    },
  )
}

/** Words of "can you remove the tomatoes from my cart" that name no product */
const REMOVE_WORDS = new Set(['can', 'you', 'please', 'remove', 'delete', 'take', 'out', 'the', 'from', 'cart', 'basket'])

/**
 * "Remove the tomatoes from my cart": the real assistant's prepareMyCartRemoval. It removes NOTHING: the answer carries
 * the cart's line, and the chat's Confirm button removes it. Several matching lines get a question instead.
 */
function removalAnswer(text: string, user: DemoUser | null): Omit<ChatReply, 'conversationId'> {
  if (!user || user.role !== 'CUSTOMER') return reply('Please log in on the website to change your cart.', false, [])
  const tools = ['prepareMyCartRemoval']
  const cart = getCart(user.id)
  if (cart.items.length === 0) return reply('Your cart is empty.', true, tools, [CART_LINK])
  const words = (text.match(/[a-z]{3,}/g) ?? []).filter((word) => !REMOVE_WORDS.has(word))
  const lines = cart.items.filter((item) =>
    words.some((word) => item.productName.toLowerCase().split(/\s+/).some((part) => part.startsWith(word.replace(/e?s$/, '')))),
  )
  const label = (item: (typeof lines)[number]) => `${item.productName} (${formatUnit(item.unit, item.unitQuantity)})`
  if (lines.length === 0) {
    return reply(`Your cart has no such product. It has: ${cart.items.map(label).join(', ')}.`, true, tools, [CART_LINK])
  }
  if (lines.length > 1) return reply(`Which one do you want to take out of your cart: ${lines.map(label).join(' or ')}?`, true, tools, [CART_LINK])
  const [line] = lines
  return reply(
    'Here is the product you want to take out of your cart. Please check it and press Confirm to remove it; nothing is removed before that.',
    true,
    tools,
    [CART_LINK],
    {
      kind: 'REMOVE_FROM_CART',
      store: cart.storeId === null ? undefined : storeById(cart.storeId)?.name,
      items: [
        {
          name: line.productName,
          packSize: formatUnit(line.unit, line.unitQuantity),
          quantity: line.quantity,
          lineTotal: formatPrice(line.lineTotal),
          productId: line.productId,
        },
      ],
    },
  )
}

/** Words of "make it 3 tomatoes" that name no product */
const QUANTITY_WORDS = new Set(['make', 'change', 'set', 'the', 'please', 'packs', 'cart'])

/**
 * "Make it 3 tomatoes": the real assistant's prepareMyCartQuantity. It changes NOTHING: the answer carries the cart's
 * line and its new quantity, and the chat's Confirm button sets it. Simpler than the real tool: the number counts packs.
 */
function quantityAnswer(text: string, user: DemoUser | null): Omit<ChatReply, 'conversationId'> {
  if (!user || user.role !== 'CUSTOMER') return reply('Please log in on the website to change your cart.', false, [])
  const tools = ['prepareMyCartQuantity']
  const cart = getCart(user.id)
  if (cart.items.length === 0) return reply('Your cart is empty.', true, tools, [CART_LINK])
  const words = (text.match(/[a-z]{3,}/g) ?? []).filter((word) => !QUANTITY_WORDS.has(word))
  const line = cart.items.find((item) =>
    words.some((word) => item.productName.toLowerCase().split(/\s+/).some((part) => part.startsWith(word.replace(/e?s$/, '')))),
  )
  if (!line) return reply('Your cart has no such product.', true, tools, [CART_LINK])
  const packs = Number(text.match(/\b(\d{1,2})\b/)?.[1])
  const label = `${line.productName} (${formatUnit(line.unit, line.unitQuantity)})`
  if (packs === line.quantity) return reply(`The cart already has ${packs} x ${label}; nothing to change.`, true, tools, [CART_LINK])
  return reply(
    'Here is the change to your cart. Please check it and press Confirm to make it; nothing is changed before that.',
    true,
    tools,
    [CART_LINK],
    {
      kind: 'SET_CART_QUANTITY',
      store: cart.storeId === null ? undefined : storeById(cart.storeId)?.name,
      items: [
        {
          name: line.productName,
          packSize: formatUnit(line.unit, line.unitQuantity),
          quantity: line.quantity,
          lineTotal: formatPrice(line.lineTotal),
          productId: line.productId,
        },
      ],
      newQuantity: packs,
    },
  )
}

/**
 * "Empty my cart": the real assistant's prepareMyCartClear. It removes NOTHING: the answer carries the whole cart, and
 * the chat's Confirm button empties it.
 */
function clearAnswer(user: DemoUser | null): Omit<ChatReply, 'conversationId'> {
  if (!user || user.role !== 'CUSTOMER') return reply('Please log in on the website to change your cart.', false, [])
  const cart = getCart(user.id)
  if (cart.items.length === 0) return reply('Your cart is empty already, so there is nothing to remove.', true, ['prepareMyCartClear'], [CART_LINK])
  return reply('Here is your cart. Please check it and press Confirm to empty it; nothing is removed before that.', true, ['prepareMyCartClear'], [CART_LINK], {
    kind: 'CLEAR_CART',
    store: cart.storeId === null ? undefined : storeById(cart.storeId)?.name,
    items: cart.items.map((item) => ({
      name: item.productName,
      packSize: formatUnit(item.unit, item.unitQuantity),
      quantity: item.quantity,
      lineTotal: formatPrice(item.lineTotal),
    })),
    payable: formatPrice(cart.totals.payableAmount),
  })
}

/** "Home: Priya Sharma, 12, 5th Cross, Bengaluru 560038" (prepareMyOrder's deliverTo) */
function addressText(address: Address): string {
  const label = address.label.charAt(0) + address.label.slice(1).toLowerCase()
  const lines = [address.contactName, address.addressLine1, address.addressLine2, `${address.city} ${address.pincode}`].filter((part) => part?.trim())
  return `${label}: ${lines.join(', ')}`
}

/**
 * "Place my order, cash on delivery": the real assistant's prepareMyOrder. It orders NOTHING: the answer carries the
 * order summary (the default address, the payment the customer named), and the chat's Confirm button places the order.
 */
function orderAnswer(text: string, user: DemoUser | null): Omit<ChatReply, 'conversationId'> {
  if (!user || user.role !== 'CUSTOMER') return reply('Please log in on the website to order.', false, [])
  const summary = checkout(user.id)
  if (!summary.readyForCheckout) {
    return reply(`Your cart cannot be ordered yet: ${summary.problems.join('; ')}.`, true, ['prepareMyOrder'], [CART_LINK])
  }
  const addresses = getAddresses(user.id)
  const address = addresses.find((candidate) => candidate.defaultAddress) ?? addresses[0]
  if (!address) return reply('Please add a delivery address first.', true, ['prepareMyOrder'], [{ label: 'Your addresses', path: '/account/addresses' }])
  const paymentMethod = /\b(cash|cod)\b/.test(text) ? 'COD' : /\b(online|upi|card)\b/.test(text) ? 'ONLINE' : null
  if (!paymentMethod) {
    return reply('How would you like to pay: cash on delivery, or online (UPI or card) right after confirming?', true, ['prepareMyOrder'])
  }

  const { totals } = summary
  const action: ChatAction = {
    kind: 'PLACE_ORDER',
    items: summary.items.map((item) => ({
      name: item.productName,
      packSize: formatUnit(item.unit, item.unitQuantity),
      quantity: item.quantity,
      lineTotal: formatPrice(item.lineTotal),
    })),
    itemsTotal: formatPrice(totals.itemsTotal),
    couponDiscount: totals.couponDiscount > 0 ? formatPrice(totals.couponDiscount) : undefined,
    deliveryFee: totals.deliveryFee > 0 ? formatPrice(totals.deliveryFee) : 'Free',
    payable: formatPrice(totals.payableAmount),
    deliverTo: addressText(address),
    payment: paymentMethod === 'COD' ? 'Cash on delivery' : 'Online (UPI or card), right after confirming',
    addressId: address.id,
    paymentMethod,
    checkoutFingerprint: summary.checkoutFingerprint!,
  }
  return reply(
    'Here is the summary of your order. Please check it and press Confirm to place the order; nothing is ordered before that.',
    true,
    ['prepareMyOrder'],
    [],
    action,
  )
}

/** How many of the newest orders "cancel my order" looks at (like the real tool) */
const CANCEL_CANDIDATES = 5

/**
 * "Cancel my order" or "cancel order 1001": the real assistant's prepareMyOrderCancellation. It cancels NOTHING: the
 * answer carries the order to cancel, and the chat's Confirm button cancels it. Without a number it takes the one
 * order of the newest five that can still be cancelled, and asks when there are several.
 */
function cancelAnswer(text: string, user: DemoUser | null): Omit<ChatReply, 'conversationId'> {
  if (!user || user.role !== 'CUSTOMER') return reply('Please log in on the website to cancel an order.', false, [])
  const tools = ['prepareMyOrderCancellation']
  const ordersLink = { label: 'Your orders', path: '/orders' }
  const number = text.match(/\b(\d+)\b/)?.[1]
  let order: Order
  if (number) {
    // Another customer's order looks like one that does not exist
    if (!listOrders(user.id, null).some((summary) => summary.id === Number(number))) {
      return reply(`I couldn't find order #${number} among your orders.`, true, tools, [ordersLink])
    }
    order = getOrder(user.id, Number(number))
    if (!order.cancellable) {
      const why = order.status === 'CANCELLED' ? 'was cancelled earlier, so there is nothing to cancel' : `can no longer be cancelled: it is ${statusText(order).toLowerCase()}`
      return reply(`Order #${order.id} ${why}.`, true, tools, [{ label: `Order #${order.id}`, path: `/orders/${order.id}` }])
    }
  } else {
    const cancellable = listOrders(user.id, null)
      .slice(0, CANCEL_CANDIDATES)
      .map((summary) => getOrder(user.id, summary.id))
      .filter((candidate) => candidate.cancellable)
    if (cancellable.length === 0) return reply('None of your recent orders can still be cancelled.', true, tools, [ordersLink])
    if (cancellable.length > 1) {
      const choices = cancellable.map((candidate) => `#${candidate.id} (${formatPrice(candidate.amounts.totalAmount)})`).join(', ')
      return reply(`Several orders can still be cancelled: ${choices}. Which one should I cancel?`, true, tools, [ordersLink])
    }
    order = cancellable[0]
  }

  const total = formatPrice(order.amounts.totalAmount)
  const action: ChatAction = {
    kind: 'CANCEL_ORDER',
    store: order.storeName,
    items: order.items.map((item) => ({
      name: item.productName,
      packSize: formatUnit(item.unit, item.unitQuantity),
      quantity: item.quantity,
      lineTotal: formatPrice(item.lineTotal),
    })),
    payable: total,
    orderId: order.id,
    status: statusText(order),
    refund:
      order.paymentStatus === 'PAID'
        ? `The online payment of ${total} will be refunded.`
        : order.paymentMethod === 'COD'
          ? 'Nothing was paid yet (cash on delivery).'
          : 'Nothing was paid yet.',
    reason: null,
  }
  return reply(
    'Here is the order you want to cancel. Please check it and press Confirm to cancel it; nothing is cancelled before that.',
    true,
    tools,
    [],
    action,
  )
}

/** OUT_FOR_DELIVERY -> "Out for delivery" */
function statusText(order: Order): string {
  const words = order.status.toLowerCase().replaceAll('_', ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/** "You have 2 products on your wishlist: Tomato, Onion." (the real assistant's getWishlist tool) */
function wishlistAnswer(names: string[]): string {
  if (names.length === 0) return 'Your wishlist is empty.'
  const products = names.length === 1 ? '1 product' : `${names.length} products`
  return `You have ${products} on your wishlist: ${names.join(', ')}.`
}

/**
 * What the assistant says to one question (the real one asks a language model that calls the shop's tools).
 * `wishlist`: the product names the shop's page sent (the wishlist is kept in the browser); undefined from the panels.
 */
function answerTo(question: string, user: DemoUser | null, wishlist: string[] | undefined): Omit<ChatReply, 'conversationId'> {
  const text = question.toLowerCase()
  // Vendors, delivery partners and admins chat in their panel: their own work first (mockPanelChat.ts)
  const panelReply = user && user.role !== 'CUSTOMER' ? panelAnswer(text, user) : null
  if (panelReply) return panelReply
  if (/wish\s?list/.test(text) && wishlist) return reply(wishlistAnswer(wishlist), true, ['getWishlist'], [{ label: 'Your wishlist', path: '/wishlist' }])
  if (/\b(empty|clear)\b.*\b(cart|basket)\b|\b(remove|delete)\s+everything\b/.test(text)) return clearAnswer(user)
  if (/\b(remove|delete|take\b.*\bout)\b.*\b(cart|basket)\b/.test(text)) return removalAnswer(text, user)
  if (/\b(make it|change|set)\b.*\b\d{1,2}\b/.test(text)) return quantityAnswer(text, user)
  if (/\b(add|put)\b.*\b(cart|basket)\b/.test(text)) return cartAnswer(text, user)
  if (/\bplace (my |the |an )?order\b|\bcheck ?out\b|\border (my|the) cart\b/.test(text)) return orderAnswer(text, user)
  if (/\bcancel\b.*\border\b/.test(text)) return cancelAnswer(text, user)
  if (/\border/.test(text)) {
    if (!user || user.role !== 'CUSTOMER') return reply('Please log in to see your orders.', false, [])
    const ordersLink = { label: 'Your orders', path: '/orders' }
    const last = listOrders(user.id, null)[0]
    if (!last) return reply('You have not placed an order yet.', true, ['getMyOrders'], [ordersLink])
    return reply(
      `Your last order was number ${last.id} from ${last.storeName}. Its status is ${last.status.toLowerCase().replaceAll('_', ' ')} and its total is ${formatPrice(last.totalAmount)}.`,
      true,
      ['getMyOrders'],
      [{ label: `Order #${last.id}`, path: `/orders/${last.id}` }, ordersLink],
    )
  }
  if (/deliver|shipping/.test(text)) {
    const settings = currentSettings()
    return reply(
      `Delivery costs ${formatPrice(settings.deliveryFee)}, and it is free for orders of ${formatPrice(settings.freeDeliveryFrom)} or more. The minimum order is ${formatPrice(settings.minimumOrderValue)}.`,
      true,
      ['getDeliveryRules'],
    )
  }
  if (/offer|coupon|discount|deal/.test(text)) {
    const coupons = publicCoupons(null)
    return reply(`Running offers: ${coupons.map((coupon) => `${coupon.code} (${coupon.description})`).join('; ')}.`, true, ['getOffers'], [
      { label: 'Offers for you', path: '/#offers' },
    ])
  }
  const product = productNamedIn(text.match(/[a-z]{3,}/g) ?? [])
  if (product) {
    const store = storeById(product.storeId)?.name ?? 'the store'
    const stock = product.inStock ? (product.lowStock ? `Only ${product.availableQuantity} left` : 'It is in stock') : 'It is out of stock right now'
    return reply(`Yes, we have ${product.name} (${formatUnit(product.unit, product.unitQuantity)}) for ${formatPrice(product.sellingPrice)} at ${store}. ${stock}.`, true, ['searchProducts'], [
      { label: product.name, path: `/products/${product.id}` },
    ])
  }
  if (/^\s*(hi|hello|hey)\b/.test(text)) return reply('Hello! I can help you find products, prices, offers and the delivery rules.', false, [])
  return reply("I'm sorry, I can't give you a reliable answer to that right now.", false, [])
}

/** One question: the ChatMessageRequest checks, then the answer, kept in the conversation (the latest 20 messages). */
export function chatAnswer(
  user: DemoUser | null,
  body: { conversationId?: string | null; message?: string; wishlist?: string[]; confirmed?: ConfirmedCard[] },
): ChatReply {
  const message = body.message?.trim() ?? ''
  if (!message) throw validationFailed([{ field: 'message', message: 'Please type a message' }])
  if (message.length > MAX_LENGTH) throw validationFailed([{ field: 'message', message: `A message can have at most ${MAX_LENGTH} characters` }])
  if (body.wishlist && body.wishlist.length > MAX_WISHLIST_ITEMS) {
    throw validationFailed([{ field: 'wishlist', message: `A wishlist can have at most ${MAX_WISHLIST_ITEMS} products` }])
  }
  const confirmed = body.confirmed ?? []
  if (confirmed.length > MAX_CONFIRMED) {
    throw validationFailed([{ field: 'confirmed', message: `At most ${MAX_CONFIRMED} confirmed cards can be sent` }])
  }
  if (confirmed.some((card) => card.kind !== 'PLACE_ORDER' && card.kind !== 'CANCEL_ORDER')) {
    throw validationFailed([{ field: 'confirmed[].kind', message: 'kind must be PLACE_ORDER or CANCEL_ORDER' }])
  }

  const conversationId = body.conversationId ?? crypto.randomUUID()
  const conversation = body.conversationId ? findConversation(body.conversationId, user) : { ownerUserId: user?.id ?? null, messages: [] }
  conversations.set(conversationId, conversation)
  // Like ChatServiceImpl: the cards the customer confirmed become lines of the conversation before the question
  if (user?.role === 'CUSTOMER') {
    for (const card of confirmed) {
      conversation.messages.push({ role: 'ASSISTANT', text: `Order #${card.orderId} ${card.kind === 'CANCEL_ORDER' ? 'cancelled' : 'placed'} with the Confirm button.` })
    }
  }
  const answer = answerTo(message, user, body.wishlist)
  // Like ApprovalServiceImpl: a card waits for the customer's approval; the answer keeps the card for the history
  const approval: ChatApproval | null = answer.action
    ? { id: crypto.randomUUID(), status: 'PENDING', createdAt: new Date().toISOString(), decidedAt: null, decidedByUserId: null }
    : null
  if (approval) approvals.set(approval.id, { approval, conversationId })
  conversation.messages.push({ role: 'USER', text: message }, { role: 'ASSISTANT', text: answer.answer, ...(approval ? { action: answer.action, approval } : {}) })
  conversation.messages = conversation.messages.slice(-KEPT_MESSAGES)
  return { conversationId, ...answer, approval }
}

export function chatConversation(user: DemoUser | null, conversationId: string): ChatConversation {
  return { conversationId, messages: findConversation(conversationId, user).messages }
}

/**
 * The customer's decision on a card (ChatController.decideApproval): APPROVED or REJECTED, final; the same decision
 * again changes nothing. Someone else's card (or a guest) gets 404, like the real service.
 */
export function decideChatApproval(user: DemoUser | null, approvalId: string, body: { decision?: string }): ChatApproval {
  if (body.decision !== 'APPROVED' && body.decision !== 'REJECTED') {
    throw validationFailed([{ field: 'decision', message: 'decision must be APPROVED or REJECTED' }])
  }
  const found = approvals.get(approvalId)
  if (!found || !user || conversations.get(found.conversationId)?.ownerUserId !== user.id) throw new MockApiError(404, 'This card was not found.')
  const { approval } = found
  if (approval.status !== body.decision) {
    if (approval.status !== 'PENDING') throw new MockApiError(400, `This card was already ${approval.status.toLowerCase()}.`)
    // The same object as on the conversation's line: the history shows the decision too
    approval.status = body.decision
    approval.decidedAt = new Date().toISOString()
    approval.decidedByUserId = user.id
  }
  return { ...approval }
}

export function deleteChatConversation(user: DemoUser | null, conversationId: string): void {
  findConversation(conversationId, user)
  conversations.delete(conversationId)
}

/** For tests: no conversations. */
export function resetMockChat(): void {
  conversations = new Map()
  approvals = new Map()
}

import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CircleAlert, PackageX, Pencil, ShoppingBag, ShoppingCart, Trash2 } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { addCartItem, clearCart, getCart, removeCartItem, updateCartItem } from '../../api/cart'
import { isApiError } from '../../api/client'
import { cancelMyOrder, placeOrder } from '../../api/orders'
import { queryKeys } from '../../api/queryKeys'
import type {
  AddToCartAction,
  CancelOrderAction,
  Cart,
  ChatAction,
  ChatActionLine,
  ChatApproval,
  ClearCartAction,
  Order,
  PlaceOrderAction,
  RemoveFromCartAction,
  SetCartQuantityAction,
} from '../../api/types'
import { Button } from '../../components/ui/Button'
import { ToneBar } from '../../components/ui/ToneIcon'
import { TONES, type Tone } from '../../components/ui/tones'
import { cn } from '../../utils/cn'
import { formatPrice } from '../../utils/format'
import { useAuth } from '../auth/authContext'
import { ApprovalStatus } from './ApprovalStatus'
import type { ActionDecision, ChatLine } from './useChat'

/** How long a summary can be confirmed: afterwards the customer asks for a fresh one (prices, stock and orders move on). */
export const CARD_LIFETIME_MS = 10 * 60 * 1000

interface CardProps<A extends ChatAction> {
  action: A
  /** made once for this card (useChat): every click on Confirm order sends it as the Idempotency-Key */
  actionKey: string
  /** when the card arrived (Date.now()) */
  receivedAt: number
  /** what the customer decided already */
  done: ActionDecision | undefined
  /** the customer decided: the card shows it, and `followUp` comes after the last message */
  onDone: (done: ActionDecision, followUp: ChatLine) => void
  /** the card's human approval as the backend keeps it (shown at the top of the card); missing from an older backend */
  approval?: ChatApproval
}

/**
 * A summary under an answer, with a button to confirm it: an order to place, an order to cancel, or a change of the
 * cart (add products, take products out, change an amount, empty the cart). It shows only what the backend made from the shop's
 * own data (ChatAction), never the model's words, and only the customer's click does it: this browser calls
 * order-service or cart-service itself, with its own login. Those services check again whose order or cart it is and
 * whether it is still allowed. After 10 minutes the card asks for a fresh summary instead.
 */
export function ChatActionCard({ action, ...card }: CardProps<ChatAction>) {
  if (action.kind === 'PLACE_ORDER') return <PlaceOrderCard action={action} {...card} />
  if (action.kind === 'CANCEL_ORDER') return <CancelOrderCard action={action} {...card} />
  if (action.kind === 'ADD_TO_CART') return <AddToCartCard action={action} {...card} />
  if (action.kind === 'REMOVE_FROM_CART') return <RemoveFromCartCard action={action} {...card} />
  if (action.kind === 'SET_CART_QUANTITY') return <SetCartQuantityCard action={action} {...card} />
  if (action.kind === 'CLEAR_CART') return <ClearCartCard action={action} {...card} />
  // A kind this website does not know yet (a newer backend): no card, rather than a wrong one
  return null
}

/**
 * The order to place, with "Confirm order" and "Cancel". Confirm sends POST /api/v1/orders with the summary's address,
 * payment and checkout fingerprint, and the card's Idempotency-Key. The key is the same for every click, so a double
 * click or a retry places one order. order-service refuses the order when the cart or its prices changed since the
 * summary (409): the card then says so.
 */
function PlaceOrderCard({ action, actionKey, receivedAt, done, onDone, approval }: CardProps<PlaceOrderAction>) {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const expired = useExpired(receivedAt, done)

  const place = useMutation({
    mutationFn: () =>
      placeOrder(
        { addressId: action.addressId, paymentMethod: action.paymentMethod, note: null, expectedCheckoutFingerprint: action.checkoutFingerprint },
        actionKey,
      ),
    onSuccess: (order) => {
      // The cart is emptied in the background and the order lists have a new order
      void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts })
      if (user) void queryClient.invalidateQueries({ queryKey: queryKeys.allOrderLists(user.id) })
      onDone('CONFIRMED', placedLine(order))
    },
    // The card shows its own spinner and error
    meta: { quiet: true },
  })
  // 409: the cart changed since the summary, or the same cart was ordered a moment ago. This summary is done.
  const stale = isApiError(place.error, 409)

  return (
    <CardFrame status={<ApprovalStatus approval={approval} done={done} expired={expired} />} tone="green" label="Order summary" icon={<ShoppingBag className="size-4" aria-hidden="true" />} title={`Your order${action.store ? ` from ${action.store}` : ''}`}>
      <ItemTable items={action.items} />

      <dl className="mt-2 space-y-0.5 border-t border-line pt-2">
        <SummaryRow label="Items" value={action.itemsTotal} />
        {action.couponDiscount && <SummaryRow label="Coupon" value={`- ${action.couponDiscount}`} />}
        <SummaryRow label="Delivery" value={action.deliveryFee} />
        <SummaryRow label="To pay" value={action.payable} strong />
      </dl>

      <dl className="mt-3 space-y-1">
        <InfoRow label="Deliver to" value={action.deliverTo} />
        <InfoRow label="Payment" value={action.payment} />
      </dl>
      {action.recentOrder && <p className="mt-3 rounded-lg bg-page px-3 py-2 text-xs">Note: {action.recentOrder}</p>}

      {done === 'CONFIRMED' ? (
        <p className="mt-3 font-bold text-brand-text">Confirmed: the order is placed.</p>
      ) : done === 'DISMISSED' ? (
        <p className="mt-3 text-body">Cancelled: nothing was ordered.</p>
      ) : expired ? (
        <ExpiredNote />
      ) : (
        <>
          {place.error && <ErrorNote message={`${place.error.message}${stale ? ' Ask me again for a new summary.' : ''}`} />}
          {!stale && (
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="sm" loading={place.isPending} onClick={() => place.mutate()}>
                Confirm order
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={place.isPending}
                onClick={() => onDone('DISMISSED', { role: 'ASSISTANT', text: 'OK, nothing was ordered. Your cart is unchanged.' })}
              >
                Cancel
              </Button>
            </div>
          )}
        </>
      )}
    </CardFrame>
  )
}

/**
 * The order to cancel, with "Confirm cancellation" and "Keep order" (the answers say "press Confirm" for both cards).
 * Confirm sends POST /api/v1/orders/{id}/cancel (the same call as the order page) with the customer's reason.
 * order-service answers 404 for another customer's order and 400 when the order can no longer be cancelled (e.g. it
 * left the store since the summary): asking again changes nothing, so the card then shows only the message.
 */
function CancelOrderCard({ action, receivedAt, done, onDone, approval }: CardProps<CancelOrderAction>) {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const expired = useExpired(receivedAt, done)

  const cancel = useMutation({
    mutationFn: () => cancelMyOrder(action.orderId, action.reason ?? null),
    onSuccess: (order) => {
      // The order page and the order lists show the cancelled order
      if (user) {
        queryClient.setQueryData(queryKeys.order(user.id, order.id), order)
        void queryClient.invalidateQueries({ queryKey: queryKeys.allOrderLists(user.id) })
      }
      onDone('CONFIRMED', cancelledLine(order))
    },
    // Refused: the order page loads the order again when it is opened
    onError: () => {
      if (user) void queryClient.invalidateQueries({ queryKey: queryKeys.order(user.id, action.orderId) })
    },
    // The card shows its own spinner and error
    meta: { quiet: true },
  })
  const refused = isApiError(cancel.error, 400) || isApiError(cancel.error, 404) || isApiError(cancel.error, 409)

  return (
    <CardFrame status={<ApprovalStatus approval={approval} done={done} expired={expired} />} tone="pink" label="Order to cancel" icon={<PackageX className="size-4" aria-hidden="true" />} title={`Cancel order #${action.orderId}${action.store ? ` from ${action.store}` : ''}?`}>
      <ItemTable items={action.items} />

      <dl className="mt-2 space-y-0.5 border-t border-line pt-2">
        <SummaryRow label="Total" value={action.payable} strong />
      </dl>

      <dl className="mt-3 space-y-1">
        <InfoRow label="Status" value={action.status} />
        <InfoRow label="Payment" value={action.refund} />
        {action.reason && <InfoRow label="Reason" value={action.reason} />}
      </dl>

      {done === 'CONFIRMED' ? (
        <p className="mt-3 font-bold text-brand-text">Done: the order is cancelled.</p>
      ) : done === 'DISMISSED' ? (
        <p className="mt-3 text-body">Kept: nothing was cancelled.</p>
      ) : expired ? (
        <ExpiredNote />
      ) : (
        <>
          {cancel.error && <ErrorNote message={cancel.error.message} />}
          {!refused && (
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="sm" variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>
                Confirm cancellation
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={cancel.isPending}
                onClick={() => onDone('DISMISSED', { role: 'ASSISTANT', text: `OK, order #${action.orderId} stays as it is.` })}
              >
                Keep order
              </Button>
            </div>
          )}
        </>
      )}
    </CardFrame>
  )
}

/**
 * The products to add to the cart (one, or several for "Add Tomato 1 kg and Onion 2 kg"), with "Confirm and add" and
 * "Don't add". Confirm reads the cart once, then makes each product's packs in the cart the line's newQuantity: PUT
 * /api/v1/cart/items/{productId} when the product is in the cart, POST /api/v1/cart/items when it is not (the same calls
 * as the cart page and the Add button), with this browser's login. So a second click, or a retry after an error, adds
 * nothing twice. cart-service checks the store, the stock and the limit of 20 again: a product it refuses (400, 409)
 * does not stop the others, and the line after the click says why; when it refuses every product the card shows its
 * reason without another try. Other errors (busy, no answer) can be tried again.
 */
function AddToCartCard({ action, receivedAt, done, onDone, approval }: CardProps<AddToCartAction>) {
  const queryClient = useQueryClient()
  const expired = useExpired(receivedAt, done)
  const lines = action.items

  const add = useMutation({
    mutationFn: async () => {
      let cart = await getCart()
      let firstRefusal: unknown
      const refused: { line: ChatActionLine; reason: string }[] = []
      for (const line of lines) {
        const inCart = cart.items.some((item) => item.productId === line.productId)
        try {
          cart = inCart ? await updateCartItem(line.productId, line.newQuantity) : await addCartItem(line.productId, line.newQuantity)
        } catch (error) {
          // cart-service's "no" for this product (out of stock, another store, at most 20): the others still go in
          if (!isApiError(error, 400) && !isApiError(error, 409)) throw error
          firstRefusal = firstRefusal ?? error
          refused.push({ line, reason: error.message })
        }
      }
      // Nothing was added: the card says why
      if (refused.length === lines.length) throw firstRefusal
      return { cart, refused }
    },
    onSuccess: ({ cart, refused }) => {
      // The cart badge, the cart page and its totals show the new cart
      void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts })
      const added = lines.filter((line) => !refused.some((item) => item.line === line))
      const notAdded = refused.map(({ line, reason }) => ` ${lineLabel(line)} was not added: ${reason}`).join('')
      onDone('CONFIRMED', cartLine(`Added ${joinAdded(added)} to your cart.${notAdded}`, cart))
    },
    // After an error the cart may have changed anyway: it is loaded again
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts }),
    // The card shows its own spinner and error
    meta: { quiet: true },
  })
  // Nothing to try again: cart-service refused every product (400, 409), or the login has expired (401)
  const refused = isApiError(add.error, 400) || isApiError(add.error, 409) || isApiError(add.error, 401)
  const growing = lines.filter((line) => line.newQuantity > line.quantity)

  return (
    <CardFrame status={<ApprovalStatus approval={approval} done={done} expired={expired} />}
      tone="orange"
      label={lines.length === 1 ? 'Product to add' : 'Products to add'}
      icon={<ShoppingCart className="size-4" aria-hidden="true" />}
      title={`Add to your cart${action.store ? ` (${action.store})` : ''}?`}
    >
      <ItemTable items={action.items} />
      {growing.length > 0 && (
        <p className="mt-2 text-xs text-body">
          Your cart will then have {growing.map((line) => `${line.newQuantity} x ${lineLabel(line)}`).join(', ')}.
        </p>
      )}

      {done === 'CONFIRMED' ? (
        <p className="mt-3 font-bold text-brand-text">{lines.length === 1 ? 'Done: the product is added.' : 'Done: the products are added.'}</p>
      ) : done === 'DISMISSED' ? (
        <p className="mt-3 text-body">Not added: your cart is unchanged.</p>
      ) : expired ? (
        <ExpiredNote />
      ) : (
        <>
          {add.error && (
            <ErrorNote message={isApiError(add.error, 401) ? 'Your login has expired. Please log in again, then ask me again.' : add.error.message} />
          )}
          {!refused && (
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="sm" loading={add.isPending} onClick={() => add.mutate()}>
                Confirm and add
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={add.isPending}
                onClick={() => onDone('DISMISSED', { role: 'ASSISTANT', text: 'OK, nothing was added to your cart.' })}
              >
                Don&apos;t add
              </Button>
            </div>
          )}
        </>
      )}
    </CardFrame>
  )
}

/** "2 x Tomato (1 kg) and 1 x Onion (1 kg)" */
function joinAdded(lines: ChatActionLine[]): string {
  const labels = lines.map((line) => `${line.quantity} x ${lineLabel(line)}`)
  return labels.length <= 1 ? labels.join('') : `${labels.slice(0, -1).join(', ')} and ${labels[labels.length - 1]}`
}

/**
 * The products to take out of the cart (one, or several for "Remove Tomato and Potato"), with "Confirm removal" and
 * "Keep it". Confirm sends DELETE /api/v1/cart/items/{productId} for each product, one after the other (the same call as
 * the remove button of the cart page) with this browser's login, so cart-service only ever changes the customer's own
 * cart. It cannot remove anything twice: once it worked the buttons are gone, and a product that is no longer in the
 * cart (removed on the cart page meanwhile, or by a first click that did work before an error) gets 404. With several
 * products the others are still removed and the line after the click says which one was gone; when every product was
 * gone the card says so without another try. Other errors (busy, a conflicting change at the same moment) can be tried
 * again.
 */
function RemoveFromCartCard({ action, receivedAt, done, onDone, approval }: CardProps<RemoveFromCartAction>) {
  const queryClient = useQueryClient()
  const expired = useExpired(receivedAt, done)
  const lines = action.items
  const labels = joinLabels(lines)

  const remove = useMutation({
    mutationFn: async () => {
      let cart: Cart | undefined
      let goneError: unknown
      const gone: ChatActionLine[] = []
      for (const line of lines) {
        try {
          cart = await removeCartItem(line.productId)
        } catch (error) {
          if (!isApiError(error, 404)) throw error
          goneError = error
          gone.push(line)
        }
      }
      // Nothing was removed: every product was gone already (the card says so)
      if (!cart) throw goneError
      return { cart, gone }
    },
    onSuccess: ({ cart, gone }) => {
      // The cart badge, the cart page and its totals show the cart without the products
      void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts })
      const removed = lines.filter((line) => !gone.includes(line))
      const wasGone = gone.length === 0 ? '' : ` ${joinLabels(gone)} ${gone.length === 1 ? 'was' : 'were'} no longer in your cart.`
      onDone('CONFIRMED', cartLine(`Removed ${joinLabels(removed)} from your cart.${wasGone}`, cart))
    },
    // After an error the cart may have changed anyway (e.g. removed meanwhile): it is loaded again
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts }),
    // The card shows its own spinner and error
    meta: { quiet: true },
  })
  // Nothing to try again: the product is gone already (404), or the login has expired (401; the website says so)
  const gone = isApiError(remove.error, 404)
  const loggedOut = isApiError(remove.error, 401)

  return (
    <CardFrame status={<ApprovalStatus approval={approval} done={done} expired={expired} />}
      tone="pink"
      label={lines.length === 1 ? 'Product to remove' : 'Products to remove'}
      icon={<Trash2 className="size-4" aria-hidden="true" />}
      title={`Remove from your cart${action.store ? ` (${action.store})` : ''}?`}
    >
      <ItemTable items={action.items} />

      {done === 'CONFIRMED' ? (
        <p className="mt-3 font-bold text-brand-text">{lines.length === 1 ? 'Done: the product is removed.' : 'Done: the products are removed.'}</p>
      ) : done === 'DISMISSED' ? (
        <p className="mt-3 text-body">Kept: your cart is unchanged.</p>
      ) : expired ? (
        <ExpiredNote />
      ) : gone ? (
        <p className="mt-3 text-body">
          {labels} {lines.length === 1 ? 'is' : 'are'} no longer in your cart.
        </p>
      ) : loggedOut ? (
        <ErrorNote message="Your login has expired. Please log in again, then ask me again." />
      ) : (
        <>
          {remove.error && <ErrorNote message={remove.error.message} />}
          <div className="mt-3 flex flex-wrap gap-2">
            <Button size="sm" variant="danger" loading={remove.isPending} onClick={() => remove.mutate()}>
              Confirm removal
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={remove.isPending}
              onClick={() => onDone('DISMISSED', { role: 'ASSISTANT', text: `OK, ${labels} ${lines.length === 1 ? 'stays' : 'stay'} in your cart.` })}
            >
              {lines.length === 1 ? 'Keep it' : 'Keep them'}
            </Button>
          </div>
        </>
      )}
    </CardFrame>
  )
}

/**
 * A new amount of a product in the cart, with "Confirm change" and "Keep it". Confirm sends PUT
 * /api/v1/cart/items/{productId} with the new total (the same call as the quantity buttons of the cart page), so a
 * second click changes nothing more. cart-service checks the stock and the limit of 20 again: its "no" (400) and a
 * product that is no longer in the cart (404) are said without another try.
 */
function SetCartQuantityCard({ action, receivedAt, done, onDone, approval }: CardProps<SetCartQuantityAction>) {
  const queryClient = useQueryClient()
  const expired = useExpired(receivedAt, done)
  const line = action.items[0]

  const change = useMutation({
    mutationFn: () => updateCartItem(line.productId, action.newQuantity),
    onSuccess: (cart) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts })
      onDone('CONFIRMED', cartLine(`Changed ${lineLabel(line)} to ${action.newQuantity}.`, cart))
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts }),
    // The card shows its own spinner and error
    meta: { quiet: true },
  })
  const gone = isApiError(change.error, 404)
  // cart-service's "no" (e.g. not enough stock) or an expired login: asking again changes nothing
  const refused = isApiError(change.error, 400) || isApiError(change.error, 401)

  return (
    <CardFrame status={<ApprovalStatus approval={approval} done={done} expired={expired} />} tone="blue" label="Change in the cart" icon={<Pencil className="size-4" aria-hidden="true" />} title={`Change the amount of ${lineLabel(line)}?`}>
      <dl className="mt-3 space-y-1">
        <InfoRow label="Now" value={`${line.quantity} x ${lineLabel(line)}, ${line.lineTotal}`} />
        <InfoRow label="New" value={`${action.newQuantity} x ${lineLabel(line)}`} />
      </dl>

      {done === 'CONFIRMED' ? (
        <p className="mt-3 font-bold text-brand-text">Done: the amount is changed.</p>
      ) : done === 'DISMISSED' ? (
        <p className="mt-3 text-body">Kept: your cart is unchanged.</p>
      ) : expired ? (
        <ExpiredNote />
      ) : gone ? (
        <p className="mt-3 text-body">{lineLabel(line)} is no longer in your cart.</p>
      ) : (
        <>
          {change.error && <ErrorNote message={isApiError(change.error, 401) ? 'Your login has expired. Please log in again, then ask me again.' : change.error.message} />}
          {!refused && (
            <div className="mt-3 flex flex-wrap gap-2">
              <Button size="sm" loading={change.isPending} onClick={() => change.mutate()}>
                Confirm change
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={change.isPending}
                onClick={() => onDone('DISMISSED', { role: 'ASSISTANT', text: `OK, ${lineLabel(line)} stays at ${line.quantity}.` })}
              >
                Keep it
              </Button>
            </div>
          )}
        </>
      )}
    </CardFrame>
  )
}

/**
 * The whole cart to empty, with "Empty cart" and "Keep it". Confirm sends DELETE /api/v1/cart (the same call as the
 * empty button of the cart page) with this browser's login; emptying twice is still an empty cart.
 */
function ClearCartCard({ action, receivedAt, done, onDone, approval }: CardProps<ClearCartAction>) {
  const queryClient = useQueryClient()
  const expired = useExpired(receivedAt, done)

  const empty = useMutation({
    mutationFn: () => clearCart(),
    onSuccess: (cart) => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts })
      onDone('CONFIRMED', cartLine('Done.', cart))
    },
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.allCarts }),
    // The card shows its own spinner and error
    meta: { quiet: true },
  })
  const loggedOut = isApiError(empty.error, 401)

  return (
    <CardFrame status={<ApprovalStatus approval={approval} done={done} expired={expired} />} tone="pink" label="Cart to empty" icon={<Trash2 className="size-4" aria-hidden="true" />} title={`Empty your cart${action.store ? ` (${action.store})` : ''}?`}>
      <ItemTable items={action.items} />

      <dl className="mt-2 space-y-0.5 border-t border-line pt-2">
        <SummaryRow label="To pay now" value={action.payable} strong />
      </dl>

      {done === 'CONFIRMED' ? (
        <p className="mt-3 font-bold text-brand-text">Done: the cart is empty.</p>
      ) : done === 'DISMISSED' ? (
        <p className="mt-3 text-body">Kept: your cart is unchanged.</p>
      ) : expired ? (
        <ExpiredNote />
      ) : loggedOut ? (
        <ErrorNote message="Your login has expired. Please log in again, then ask me again." />
      ) : (
        <>
          {empty.error && <ErrorNote message={empty.error.message} />}
          <div className="mt-3 flex flex-wrap gap-2">
            <Button size="sm" variant="danger" loading={empty.isPending} onClick={() => empty.mutate()}>
              Empty cart
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={empty.isPending}
              onClick={() => onDone('DISMISSED', { role: 'ASSISTANT', text: 'OK, your cart stays as it is.' })}
            >
              Keep it
            </Button>
          </div>
        </>
      )}
    </CardFrame>
  )
}

/** True once the card is older than CARD_LIFETIME_MS; the card closes by itself when its time is up. */
function useExpired(receivedAt: number, done: ActionDecision | undefined): boolean {
  const [expired, setExpired] = useState(() => Date.now() - receivedAt >= CARD_LIFETIME_MS)
  useEffect(() => {
    if (expired || done) return
    const timer = window.setTimeout(() => setExpired(true), receivedAt + CARD_LIFETIME_MS - Date.now())
    return () => window.clearTimeout(timer)
  }, [expired, done, receivedAt])
  return expired
}

interface CardFrameProps {
  label: string
  /**
   * The card's colour (tones.ts): its bar along the top and its icon's tile. green = an order to place, orange = products
   * to add, blue = a new amount, pink = something to cancel, take out or empty.
   */
  tone: Tone
  icon: ReactNode
  title: string
  /** the human approval line under the title (ApprovalStatus) */
  status: ReactNode
  children: ReactNode
}

/** A card's frame: a coloured bar along the top, the title with its icon in a coloured tile, the approval line, the content */
function CardFrame({ label, tone, icon, title, status, children }: CardFrameProps) {
  return (
    <section aria-label={label} className="relative w-full overflow-hidden rounded-2xl bg-card p-4 pt-5 text-sm text-heading shadow-md ring-1 ring-line/70">
      <ToneBar tone={tone} />
      <h3 className="flex items-center gap-2.5 font-heading font-bold">
        <span aria-hidden="true" className={cn('grid size-8 shrink-0 place-items-center rounded-xl', TONES[tone].soft)}>
          {icon}
        </span>
        {title}
      </h3>
      {status}
      {children}
    </section>
  )
}

function ItemTable({ items }: { items: ChatAction['items'] }) {
  return (
    <table className="mt-3 w-full">
      <caption className="sr-only">Products</caption>
      <tbody>
        {items.map((item) => (
          <tr key={`${item.name}-${item.packSize}`}>
            <td className="py-0.5 pr-3">
              {item.quantity} x {item.name}
              {item.packSize ? ` (${item.packSize})` : ''}
            </td>
            <td className="py-0.5 text-right whitespace-nowrap tabular-nums">{item.lineTotal}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function SummaryRow({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={strong ? 'flex justify-between font-bold' : 'flex justify-between'}>
      <dt>{label}</dt>
      <dd className="tabular-nums">{value}</dd>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="inline font-bold">{label}: </dt>
      <dd className="inline">{value}</dd>
    </div>
  )
}

function ExpiredNote() {
  return <p className="mt-3 text-body">This summary is older than 10 minutes. Ask me again for a fresh one.</p>
}

function ErrorNote({ message }: { message: string }) {
  return (
    <p role="alert" className="mt-3 flex items-start gap-2 rounded-lg bg-hot/5 px-3 py-2 text-hot-text">
      <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <span>{message}</span>
    </p>
  )
}

/**
 * The chat line after a placed order, made from order-service's answer (never from the model): the order number, what
 * to pay and how, and a link to the order page. An online order also gets order-service's paymentUrl, for the
 * "Pay Now" button (ChatConversation); null when order-service sent none. `confirmed` goes with the next question, so
 * the assistant knows the order exists (useChat).
 */
function placedLine(order: Order): ChatLine {
  const total = formatPrice(order.amounts.totalAmount)
  const line: ChatLine = {
    role: 'ASSISTANT',
    text: `Order #${order.id} placed. Please pay ${total} in cash when it is delivered.`,
    grounded: true,
    links: [{ label: `Order #${order.id}`, path: `/orders/${order.id}` }],
    confirmed: { kind: 'PLACE_ORDER', orderId: order.id },
  }
  if (order.paymentMethod === 'COD') return line
  return {
    ...line,
    text: `Order #${order.id} placed. Please pay ${total} online on the order page within 15 minutes; the order is confirmed after the payment.`,
    paymentUrl: order.paymentUrl ?? null,
  }
}

/** "Tomato (1 kg)" */
function lineLabel(line: ChatActionLine): string {
  return line.packSize ? `${line.name} (${line.packSize})` : line.name
}

/** "Tomato (1 kg)", "Tomato (1 kg) and Potato (1 kg)", "Tomato (1 kg), Onion (1 kg) and Potato (1 kg)" */
function joinLabels(lines: ChatActionLine[]): string {
  const labels = lines.map(lineLabel)
  return labels.length <= 1 ? labels.join('') : `${labels.slice(0, -1).join(', ')} and ${labels[labels.length - 1]}`
}

/**
 * The chat line after a change of the cart: what was done, then the cart as cart-service answered it (never from the
 * model): "Removed Tomato (1 kg) from your cart. Your cart now has 2 products; the amount to pay is ₹159."
 */
function cartLine(done: string, cart: Cart): ChatLine {
  const count = cart.items.length
  const now =
    count === 0
      ? 'Your cart is now empty.'
      : `Your cart now has ${count === 1 ? '1 product' : `${count} products`}; the amount to pay is ${formatPrice(cart.totals.payableAmount)}.`
  return { role: 'ASSISTANT', text: `${done} ${now}`, grounded: true, links: [{ label: 'Your cart', path: '/cart' }] }
}

/**
 * The chat line after a cancelled order, made from order-service's answer like the order page's message: a paid order
 * stays PAID until payment-service reports the refund.
 */
function cancelledLine(order: Order): ChatLine {
  const refund = order.paymentStatus === 'PAID' || order.paymentStatus === 'REFUNDED' ? ' Your refund is on its way.' : ''
  return {
    role: 'ASSISTANT',
    text: `Order #${order.id} is cancelled.${refund}`,
    grounded: true,
    links: [{ label: `Order #${order.id}`, path: `/orders/${order.id}` }],
    confirmed: { kind: 'CANCEL_ORDER', orderId: order.id },
  }
}

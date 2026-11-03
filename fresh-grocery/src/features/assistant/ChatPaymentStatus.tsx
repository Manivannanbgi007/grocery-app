import { BadgeCheck, CircleAlert, CreditCard } from 'lucide-react'
import { isApiError } from '../../api/client'
import type { Payment } from '../../api/types'
import { formatPrice } from '../../utils/format'
import { usesTestGateway } from '../orders/razorpayCheckout'
import { usePayment } from '../orders/useOrders'
import type { AssistantKind } from './assistantTexts'
import { PayNowButton } from './PayNowButton'

interface ChatPaymentStatusProps {
  /** the order placed with the chat's Confirm button; undefined when an answer only brought the payment link */
  orderId?: number
  /** where to pay, exactly as order-service sent it (Order.paymentUrl); null when it sent none */
  paymentUrl: string | null
  /** which chat it is: decides which pages may be opened (chatLinks.ts) */
  kind: AssistantKind
  /** after the click on Pay Now (the chat may close on small screens) */
  onNavigate: () => void
}

/**
 * Under the chat line of a placed ONLINE order: the order, the amount, where its payment stands and how to pay, with
 * the Pay Now button (it opens the order page, where Razorpay's window takes the payment). When the payment is made,
 * the box turns into the confirmation "Payment of ₹231 received for order #1001."
 *
 * The status always comes from payment-service (GET /api/v1/payments/orders/{id}), which marks a payment paid only
 * after it checked Razorpay's signature: the browser never decides that an order is paid. It is asked again every few
 * seconds until the payment is paid, failed or cancelled, so the chat shows the result also when the customer paid on
 * the order page, or Razorpay's webhook confirmed it later.
 */
export function ChatPaymentStatus({ orderId, ...button }: ChatPaymentStatusProps) {
  // "Order #34 waits for your payment" (an answer of the assistant) has the link but no order of this chat: the button only
  if (orderId === undefined) return <PayNowButton {...button} />
  return <PaymentOfOrder orderId={orderId} {...button} />
}

function PaymentOfOrder({ orderId, paymentUrl, kind, onNavigate }: ChatPaymentStatusProps & { orderId: number }) {
  const { data: payment, error } = usePayment(orderId, true, true)
  const label = `Payment of order #${orderId}`

  // The end of the payment: paid, failed, refunded or cancelled. No Pay Now any more.
  const result = payment && resultOf(payment, orderId)
  if (result) {
    const Icon = result.good ? BadgeCheck : CircleAlert
    return (
      <section aria-label={label} className="w-full">
        <p role="status" className={result.good ? 'flex items-start gap-2 rounded-lg bg-brand-light px-3 py-2 text-sm font-bold text-brand-text' : 'flex items-start gap-2 rounded-lg bg-hot/5 px-3 py-2 text-sm text-hot-text'}>
          <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>{result.text}</span>
        </p>
      </section>
    )
  }

  // Still to pay: the details, then Pay Now
  return (
    <section aria-label={label} className="w-full rounded-2xl bg-card p-4 text-sm text-heading shadow-sm ring-1 ring-line/70">
      <h3 className="flex items-center gap-2 font-heading font-bold">
        <CreditCard className="size-4 text-brand" aria-hidden="true" />
        Online payment
      </h3>
      <dl className="mt-2 mb-3 space-y-0.5">
        <Row label="Order" value={`#${orderId}`} />
        {payment && <Row label="Amount" value={formatPrice(payment.amount)} />}
        <Row label="Status" value={statusText(payment, error)} />
        {/* Known once payment-service opened the payment: Razorpay, or the test gateway of local development */}
        {payment?.status === 'AWAITING_PAYMENT' && <Row label="Pay with" value={usesTestGateway(payment) ? 'Test payment gateway (no real money)' : 'Razorpay: UPI, cards, net banking or wallets'} />}
      </dl>
      <PayNowButton paymentUrl={paymentUrl} kind={kind} onNavigate={onNavigate} />
    </section>
  )
}

/** The text for a payment that has ended, or null while it can still be paid */
function resultOf(payment: Payment, orderId: number): { good: boolean; text: string } | null {
  const amount = formatPrice(payment.amount)
  switch (payment.status) {
    case 'PAID':
      return { good: true, text: `Payment of ${amount} received for order #${orderId}.` }
    case 'FAILED':
      return { good: false, text: `The payment failed${payment.failureReason ? `: ${payment.failureReason}` : ''}. Order #${orderId} is being cancelled; no money was taken.` }
    case 'REFUNDED':
      return { good: true, text: `Order #${orderId} was cancelled, and your payment of ${amount} was refunded.` }
    case 'CANCELLED':
      return { good: false, text: `Order #${orderId} was cancelled before it was paid; no money was taken.` }
    default:
      return null
  }
}

/** "Waiting for your payment": what payment-service says about a payment that is still to be made */
function statusText(payment: Payment | undefined, error: Error | null): string {
  if (payment?.status === 'AWAITING_PAYMENT') return payment.failureReason ? `Your last attempt did not work (${payment.failureReason}). You can try again.` : 'Waiting for your payment'
  // The payment opens a moment after the order, once the items are reserved (until then payment-service answers 404)
  if (payment?.status === 'PENDING' || isApiError(error, 404)) return 'Reserving your items…'
  if (error) return 'Could not be checked right now. Pay Now shows it.'
  return 'Checking…'
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="inline font-bold">{label}: </dt>
      <dd className="inline">{value}</dd>
    </div>
  )
}

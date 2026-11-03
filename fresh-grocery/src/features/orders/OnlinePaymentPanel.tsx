import { CreditCard, LockKeyhole } from 'lucide-react'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import type { Order, Payment } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { ErrorState } from '../../components/ui/ErrorState'
import { Spinner } from '../../components/ui/Spinner'
import { useCountdown } from '../../hooks/useCountdown'
import { formatPrice } from '../../utils/format'
import { useAuth } from '../auth/authContext'
import { RazorpayClosedError, usesTestGateway } from './razorpayCheckout'
import { usePayment, usePayNow, usePayWithRazorpay } from './useOrders'

const pad = (value: number) => String(value).padStart(2, '0')

/** "Time left to pay: 14:32": an unpaid online order is cancelled when it runs out. */
function PayBeforeCountdown({ payBefore }: { payBefore: string }) {
  const left = useCountdown(payBefore)
  if (left.ended) return <p className="text-sm font-bold text-hot">The time to pay has run out. The order will be cancelled.</p>
  const minutes = left.hours * 60 + left.minutes
  return (
    <p className="text-sm">
      Time left to pay:{' '}
      <span role="timer" aria-label={`${minutes} minutes left`} className="font-heading font-bold text-heading">
        {minutes}:{pad(left.seconds)}
      </span>
      . After that the order is cancelled.
    </p>
  )
}

/** A waiting message with a spinner, read out by screen readers when it appears. */
function Waiting({ text }: { text: string }) {
  return (
    <p role="status" className="flex items-center gap-2 text-sm text-heading">
      <Spinner size="sm" className="text-brand" />
      {text}
    </p>
  )
}

/** The mock gateway (local development): pay, or let the bank decline to try that path. */
function TestGatewayButtons({ payNow, total, pay }: { payNow: ReturnType<typeof usePayNow>; total: string; pay: (success: boolean) => void }) {
  return (
    <>
      <p className="rounded-lg bg-surface px-4 py-3 text-xs">Test payment gateway: no real money is taken. Use “Decline” to see what happens when a bank refuses a payment.</p>
      {payNow.error && (
        <p role="alert" className="text-sm text-hot">
          {payNow.error.message}
        </p>
      )}
      <div className="flex flex-wrap gap-3">
        <Button size="lg" loading={payNow.isPending && payNow.variables} disabled={payNow.isPending} onClick={() => pay(true)} className="shadow-lg shadow-brand/30">
          Pay {total}
        </Button>
        <Button size="lg" variant="ghost" loading={payNow.isPending && !payNow.variables} disabled={payNow.isPending} onClick={() => pay(false)}>
          Decline (test)
        </Button>
      </div>
    </>
  )
}

/** Razorpay: their payment window (UPI, cards, net banking, wallets); a failed attempt can be tried again. */
function RazorpayButton({ razorpay, payment, total, pay }: { razorpay: ReturnType<typeof usePayWithRazorpay>; payment: Payment; total: string; pay: () => void }) {
  const closed = razorpay.error instanceof RazorpayClosedError
  return (
    <>
      <p className="text-sm">Pay securely in Razorpay&apos;s window: UPI, cards, net banking or wallets.</p>
      {payment.failureReason && !razorpay.isPending && <p className="text-sm text-heading">Your last attempt did not work: {payment.failureReason}. You can try again.</p>}
      {razorpay.error && (
        <p role={closed ? 'status' : 'alert'} className={closed ? 'text-sm text-heading' : 'text-sm text-hot'}>
          {razorpay.error.message}
        </p>
      )}
      <Button size="lg" loading={razorpay.isPending} onClick={pay} className="shadow-lg shadow-brand/30">
        <LockKeyhole className="size-4" aria-hidden="true" />
        Pay {total}
      </Button>
    </>
  )
}

/**
 * The payment of an ONLINE order that is still PENDING. The payment opens a moment after the order (once the stock
 * is reserved); then the customer pays: with Razorpay, or with the test gateway when payment-service runs the mock
 * (local development).
 */
export function OnlinePaymentPanel({ order }: { order: Order }) {
  const { user } = useAuth()
  const paymentQuery = usePayment(order.id, true)
  // The mutations live here, not in the buttons: the buttons disappear as soon as the payment is made, and a
  // component that is gone gets no onSuccess (so no "Payment received" message)
  const payNow = usePayNow(order.id)
  const razorpay = usePayWithRazorpay(order.id)
  const total = formatPrice(order.amounts.totalAmount)

  function payWithTestGateway(success: boolean) {
    payNow.mutate(success, {
      onSuccess: (paid) => {
        if (paid.status === 'PAID') toast.success(`Payment of ${total} received`)
        else toast.error('The bank declined the payment')
      },
    })
  }

  function payWithRazorpay() {
    razorpay.mutate(
      { name: user?.fullName, email: user?.email, contact: user?.phone },
      {
        onSuccess: (paid) => {
          if (paid.status === 'PAID') toast.success(`Payment of ${total} received`)
        },
      },
    )
  }

  let content
  const payment = paymentQuery.data
  // 404 = the payment is created a moment after the order: keep waiting (usePayment asks again)
  if (paymentQuery.isPending || isApiError(paymentQuery.error, 404) || payment?.status === 'PENDING') {
    content = <Waiting text="Reserving your items. You can pay in a moment…" />
  } else if (paymentQuery.isError) {
    content = <ErrorState compact title="Could not load the payment" error={paymentQuery.error} onRetry={() => void paymentQuery.refetch()} />
  } else if (payment?.canPayNow) {
    content = (
      <div className="space-y-4">
        <p className="text-heading">
          Your items are reserved. Pay <strong>{total}</strong> to send the order to {order.storeName}.
        </p>
        {payment.payBefore && <PayBeforeCountdown payBefore={payment.payBefore} />}
        {usesTestGateway(payment) ? (
          <TestGatewayButtons payNow={payNow} total={total} pay={payWithTestGateway} />
        ) : (
          <RazorpayButton razorpay={razorpay} payment={payment} total={total} pay={payWithRazorpay} />
        )}
      </div>
    )
  } else if (payment?.status === 'PAID') {
    content = <Waiting text={`Payment of ${total} received. Confirming your order…`} />
  } else if (payment?.status === 'FAILED') {
    content = <p className="text-sm text-heading">The payment failed{payment.failureReason ? `: ${payment.failureReason}` : ''}. The order is being cancelled.</p>
  } else {
    content = null
  }

  if (!content) return null
  return (
    // A violet-to-blue card (violet is the colour of payment on the order page) with a soft glow in the corner, so the
    // payment is the first thing the customer sees. isolate + overflow-hidden keep the glow behind the content and inside.
    <section
      aria-labelledby="online-payment-title"
      className="relative isolate overflow-hidden rounded-3xl border-2 border-violet/30 bg-linear-to-br from-violet-light via-card to-sale-light p-6 shadow-lg shadow-violet/10"
    >
      <span aria-hidden="true" className="absolute -top-20 -right-16 -z-10 size-56 rounded-full bg-glow-4 opacity-80 blur-3xl motion-safe:animate-blob" />
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 id="online-payment-title" className="flex items-center gap-3 text-xl">
          {/* The card icon floats up and down (motion-safe: not when the system asks for less motion) */}
          <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-xl bg-linear-to-br from-violet to-deep-blue text-white shadow-md shadow-violet/30 motion-safe:animate-float">
            <CreditCard className="size-5" />
          </span>
          Payment
        </h2>
        <span className="inline-flex items-center gap-1.5 rounded-full bg-card px-3 py-1 text-xs font-bold text-violet-text ring-1 ring-violet/30">
          <LockKeyhole className="size-3.5" aria-hidden="true" />
          Secure payment
        </span>
      </div>
      {content}
    </section>
  )
}

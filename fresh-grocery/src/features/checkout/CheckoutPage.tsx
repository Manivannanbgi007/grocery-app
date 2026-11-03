import { ArrowRight, Banknote, CreditCard, ShieldCheck } from 'lucide-react'
import { useId, useState } from 'react'
import { useNavigate } from 'react-router'
import { isApiError } from '../../api/client'
import type { PaymentMethod } from '../../api/types'
import { BreadcrumbBar } from '../../components/layout/Breadcrumb'
import { PageHero } from '../../components/layout/PageHero'
import { PageTitle } from '../../components/layout/PageTitle'
import { Appear } from '../../components/ui/Appear'
import { Button, ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { Skeleton } from '../../components/ui/Skeleton'
import { ToneBar } from '../../components/ui/ToneIcon'
import { toneCard } from '../../components/ui/tones'
import { useStoreName } from '../../hooks/useStores'
import { useAddresses } from '../account/useAddresses'
import { RequireCustomer } from '../auth/RequireCustomer'
import { CartTotals } from '../cart/CartTotals'
import { CheckoutSteps } from '../cart/CheckoutSteps'
import { useCheckoutSummary } from '../cart/useCart'
import { usePlaceOrder } from '../orders/useOrders'
import { AddressPicker } from './AddressPicker'
import { CheckoutProblems } from './CheckoutProblems'
import { OrderReview } from './OrderReview'
import { PaymentMethodPicker } from './PaymentMethodPicker'
import { StepNumber } from './StepNumber'

const NOTE_MAX = 250

/** Checkout needs a logged-in customer: guests are sent to the login page first. */
export function CheckoutPage() {
  return (
    <RequireCustomer>
      <CheckoutContent />
    </RequireCustomer>
  )
}

/** What the order page shows once after placing an order ("Thank you ..."); passed as router state. */
export interface PlacedOrderState {
  placed: true
}

function CheckoutContent() {
  const navigate = useNavigate()
  const noteId = useId()
  const summaryQuery = useCheckoutSummary()
  const addressesQuery = useAddresses()
  const storeName = useStoreName(summaryQuery.data?.storeId)
  const placeOrder = usePlaceOrder()

  const [chosenAddressId, setChosenAddressId] = useState<number | null>(null)
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('COD')
  const [note, setNote] = useState('')
  const [addressError, setAddressError] = useState<string | null>(null)
  const [orderError, setOrderError] = useState<Error | null>(null)

  const addresses = addressesQuery.data ?? []
  // The customer's choice; until they choose, the default address (or the first one)
  const selectedAddress = addresses.find((address) => address.id === chosenAddressId) ?? addresses.find((address) => address.defaultAddress) ?? addresses[0]

  function chooseAddress(addressId: number) {
    setChosenAddressId(addressId)
    setAddressError(null)
  }

  function handlePlaceOrder() {
    setOrderError(null)
    if (!selectedAddress) {
      setAddressError('Please add a delivery address first.')
      return
    }
    setAddressError(null)
    placeOrder.mutate(
      { addressId: selectedAddress.id, paymentMethod, note: note.trim() || null },
      {
        // replace: "Back" on the order page goes to the (now empty) cart, not to this form again
        onSuccess: (order) => navigate(`/orders/${order.id}`, { replace: true, state: { placed: true } satisfies PlacedOrderState }),
        onError: (error) => {
          if (isApiError(error, 401)) return // the login expired: the AuthProvider already says so
          if (isApiError(error, 409)) void summaryQuery.refetch() // the cart changed: show its problems
          if (error.message.includes('PIN code')) setAddressError(error.message)
          else setOrderError(error)
        },
      },
    )
  }

  let content
  if (summaryQuery.isPending || addressesQuery.isPending) {
    content = (
      <div className="grid gap-8 lg:grid-cols-[1fr_26rem]">
        <Skeleton className="h-96 rounded-3xl" />
        <Skeleton className="h-96 rounded-3xl" />
      </div>
    )
  } else if (summaryQuery.isError || addressesQuery.isError) {
    content = (
      <ErrorState
        title="Could not prepare the checkout"
        error={summaryQuery.error ?? addressesQuery.error}
        onRetry={() => {
          void summaryQuery.refetch()
          void addressesQuery.refetch()
        }}
      />
    )
  } else if (summaryQuery.data.items.length === 0) {
    content = (
      // A light orange-to-blue card around the "empty" message
      <div className="rounded-3xl border border-line bg-linear-to-br from-new-light via-card to-sale-light motion-safe:animate-rise">
        <EmptyState
          icon="🛒"
          title="Your cart is empty"
          message="Add some products before checking out."
          action={
            <ButtonLink to="/shop" className="shadow-lg shadow-brand/30">
              Continue shopping
            </ButtonLink>
          }
        />
      </div>
    )
  } else if (!summaryQuery.data.readyForCheckout) {
    content = (
      <div className="mx-auto max-w-2xl space-y-6">
        <CheckoutProblems problems={summaryQuery.data.problems} title="Your cart cannot be ordered yet:" />
        <ButtonLink to="/cart">Back to cart</ButtonLink>
      </div>
    )
  } else {
    const summary = summaryQuery.data
    content = (
      <div className="grid items-start gap-8 lg:grid-cols-[1fr_26rem]">
        {/* The three steps, each in a card of its colour: the address blue (delivery), the payment violet, the note orange */}
        <div className="space-y-6">
          <Appear>
            <div className={toneCard('blue', 'p-5 sm:p-6')}>
              <ToneBar tone="blue" />
              <AddressPicker addresses={addresses} selectedId={selectedAddress?.id ?? null} onSelect={chooseAddress} error={addressError} />
            </div>
          </Appear>
          <Appear order={1}>
            <div className={toneCard('violet', 'p-5 sm:p-6')}>
              <ToneBar tone="violet" />
              <PaymentMethodPicker value={paymentMethod} onChange={setPaymentMethod} />
            </div>
          </Appear>
          <Appear order={2}>
            <div className={toneCard('orange', 'p-5 sm:p-6')}>
              <ToneBar tone="orange" />
              <label htmlFor={noteId} className="mb-4 flex items-center gap-3 font-heading text-xl font-bold text-heading">
                <StepNumber number={3} tone="orange" /> Note for the store (optional)
              </label>
              <textarea
                id={noteId}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                maxLength={NOTE_MAX}
                rows={3}
                placeholder="e.g. Please ring the bell twice"
                className="w-full rounded-2xl border border-line bg-card p-4 text-heading transition-shadow outline-none focus:border-new focus:ring-4 focus:ring-new/15"
              />
              <p className="text-right text-xs">
                {note.length}/{NOTE_MAX}
              </p>
            </div>
          </Appear>
        </div>

        <aside className="space-y-4 lg:sticky lg:top-below-header">
          <Appear order={1}>
            <OrderReview items={summary.items} storeName={storeName} />
          </Appear>
          <Appear order={2}>
            <CartTotals cart={summary} title="Order total" />
          </Appear>
          {orderError && (
            <div role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
              <p>{orderError.message}</p>
              {isApiError(orderError) && orderError.details.length > 0 && (
                <ul className="mt-1 list-disc pl-5">
                  {orderError.details.map((detail) => (
                    <li key={detail.message}>{detail.message}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
          {/* group: the arrow slides to the right under the mouse */}
          <Button size="lg" className="group w-full shadow-lg shadow-brand/30" loading={placeOrder.isPending} onClick={handlePlaceOrder}>
            Place order
            {!placeOrder.isPending && <ArrowRight className="size-5 transition-transform duration-200 group-hover:translate-x-1" aria-hidden="true" />}
          </Button>
          <p className="flex items-center justify-center gap-1.5 text-center text-xs">
            {paymentMethod === 'COD' ? <Banknote className="size-4 text-brand-text" aria-hidden="true" /> : <CreditCard className="size-4 text-violet-text" aria-hidden="true" />}
            {paymentMethod === 'COD' ? 'You pay in cash when the order arrives.' : 'You pay on the next page, right after placing the order.'}
          </p>
        </aside>
      </div>
    )
  }

  return (
    <>
      <PageTitle title="Checkout" />
      <BreadcrumbBar crumbs={[{ label: 'Cart', to: '/cart' }, { label: 'Checkout' }]} />
      <div className="page-container mt-8">
        <PageHero icon={ShieldCheck} tone="green" title="Checkout" subtitle="Choose where to deliver and how to pay, then place your order.">
          <CheckoutSteps current={1} />
        </PageHero>
        {content}
      </div>
    </>
  )
}

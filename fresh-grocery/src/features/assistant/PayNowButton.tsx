import { CircleAlert, CreditCard } from 'lucide-react'
import { ButtonLink } from '../../components/ui/Button'
import type { AssistantKind } from './assistantTexts'
import { safeInternalPath } from './chatLinks'

interface PayNowButtonProps {
  /** where to pay, exactly as order-service sent it (Order.paymentUrl); null when it sent none */
  paymentUrl: string | null
  /** which chat it is: decides which pages may be opened (chatLinks.ts) */
  kind: AssistantKind
  /** after the click (the chat may close on small screens) */
  onNavigate: () => void
}

/**
 * "Pay Now" under the line of a placed online order. It opens the page order-service named for the payment (the order
 * page, where Razorpay's window or the test gateway takes the payment) in this tab, with React Router. Razorpay opens
 * its own secure window on that page, so no new tab is needed.
 * The address is never made up here, and it is checked like every link of the chat (safeInternalPath): when it is
 * missing, or is not a page of the shop (another site, "javascript:..."), the chat says how to pay instead of showing
 * a button that leads nowhere.
 */
export function PayNowButton({ paymentUrl, kind, onNavigate }: PayNowButtonProps) {
  const path = paymentUrl ? safeInternalPath(paymentUrl, kind) : null

  if (!path) {
    return (
      <p role="alert" className="flex items-start gap-2 rounded-lg bg-hot/5 px-3 py-2 text-sm text-hot-text">
        <CircleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <span>The payment link is not available. Please open the order from My orders to pay.</span>
      </p>
    )
  }
  return (
    <ButtonLink to={path} onClick={onNavigate} size="sm">
      <CreditCard className="size-4" aria-hidden="true" />
      Pay Now
    </ButtonLink>
  )
}

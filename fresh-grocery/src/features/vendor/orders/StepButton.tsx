import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Order } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import type { ButtonSize } from '../../../components/ui/buttonClasses'
import { useOrderSteps } from './useVendorOrders'
import { nextStep, type StoreStep } from './vendorOrderStatus'

const ACCESSIBLE_NAMES: Record<StoreStep, (orderId: number) => string> = {
  accept: (orderId) => `Accept order #${orderId}`,
  pack: (orderId) => `Mark order #${orderId} as packed`,
  ready: (orderId) => `Mark order #${orderId} ready for pickup`,
}

const DONE_MESSAGES: Record<StoreStep, (orderId: number) => string> = {
  accept: (orderId) => `Order #${orderId} accepted`,
  pack: (orderId) => `Order #${orderId} is packed`,
  ready: (orderId) => `Order #${orderId} is ready for pickup`,
}

/** The store's next step for the order ("Accept", "Mark as packed", "Ready for pickup"); nothing when there is none. */
export function StepButton({ order, size, className }: { order: Pick<Order, 'id' | 'status'>; size?: ButtonSize; className?: string }) {
  const { step } = useOrderSteps(order.id)
  const next = nextStep(order.status)
  if (!next) return null

  // mutateAsync, not mutate(…, { onSuccess }): in the queue the order often leaves its tab (the live update reloads the
  // list) before the step's answer is handled, and React Query skips mutate's callbacks once the button is gone. The
  // awaited promise still settles, so the message always shows.
  async function run(storeStep: StoreStep) {
    try {
      await step.mutateAsync(storeStep)
      toast.success(DONE_MESSAGES[storeStep](order.id))
    } catch (error) {
      // e.g. "Order 12 is CANCELLED, so it cannot become ACCEPTED" (the customer cancelled it a moment ago)
      if (error instanceof Error && !isApiError(error, 401)) toast.error(error.message)
    }
  }

  return (
    <Button size={size} loading={step.isPending} onClick={() => void run(next.step)} aria-label={ACCESSIBLE_NAMES[next.step](order.id)} className={className}>
      {next.label}
    </Button>
  )
}

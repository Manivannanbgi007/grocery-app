import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import { Button } from '../../../components/ui/Button'
import { ReasonDialog } from '../../../components/ui/ReasonDialog'
import { useOrderSteps } from './useVendorOrders'

const COMMON_REASONS = ['Some items are out of stock', 'The store is closing early today', 'Too many orders right now']

/** "Reject" for a new order: asks for the reason (required: the customer sees it); the stock goes back, a paid order is refunded. */
export function RejectOrderButton({ orderId }: { orderId: number }) {
  const { reject } = useOrderSteps(orderId)
  const [open, setOpen] = useState(false)

  function confirm(reason: string) {
    reject.mutate(reason, {
      onSuccess: () => {
        setOpen(false)
        toast.success(`Order #${orderId} rejected`)
      },
      onError: (rejectError) => {
        setOpen(false)
        if (!isApiError(rejectError, 401)) toast.error(rejectError.message)
      },
    })
  }

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        Reject
      </Button>
      <ReasonDialog
        open={open}
        title={`Reject order #${orderId}?`}
        message="The customer is told the reason, the items go back into stock, and a paid order is refunded."
        commonReasons={COMMON_REASONS}
        requiredMessage="Please tell the customer why."
        confirmLabel="Reject order"
        loading={reject.isPending}
        onConfirm={confirm}
        onCancel={() => setOpen(false)}
      />
    </>
  )
}

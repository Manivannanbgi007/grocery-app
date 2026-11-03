import { X } from 'lucide-react'
import { useId, useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../api/client'
import type { Order } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { useCancelOrder } from './useOrders'

const REASON_MAX = 255

/** "Cancel order": possible until the order leaves the store. Asks first, with an optional reason. */
export function CancelOrderButton({ order }: { order: Order }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const reasonId = useId()
  const cancel = useCancelOrder(order.id)
  const paid = order.paymentStatus === 'PAID'

  function confirm() {
    cancel.mutate(reason.trim() || null, {
      onSuccess: () => {
        setOpen(false)
        toast.success(paid ? `Order #${order.id} is cancelled. Your refund is on its way.` : `Order #${order.id} is cancelled.`)
      },
      onError: (error) => {
        setOpen(false)
        // e.g. "An order that is OUT_FOR_DELIVERY can no longer be cancelled" (it left the store in the meantime)
        if (!isApiError(error, 401)) toast.error(error.message)
      },
    })
  }

  return (
    <>
      <Button variant="danger-outline" onClick={() => setOpen(true)}>
        <X className="size-4" aria-hidden="true" />
        Cancel order
      </Button>
      <ConfirmDialog
        open={open}
        title={`Cancel order #${order.id}?`}
        message={
          <div className="space-y-3">
            <p>{paid ? `${order.storeName} will not prepare it, and the money you paid goes back to you.` : `${order.storeName} will not prepare it.`}</p>
            <div>
              <label htmlFor={reasonId} className="mb-1 block font-heading text-sm font-bold text-heading">
                Reason (optional)
              </label>
              <textarea
                id={reasonId}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                maxLength={REASON_MAX}
                rows={2}
                placeholder="e.g. Ordered by mistake"
                className="w-full rounded-lg border border-line bg-card p-3 text-sm text-heading outline-none focus:border-brand"
              />
            </div>
          </div>
        }
        confirmLabel="Cancel order"
        cancelLabel="Keep order"
        loading={cancel.isPending}
        onConfirm={confirm}
        onCancel={() => setOpen(false)}
      />
    </>
  )
}

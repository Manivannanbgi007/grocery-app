import { useId, useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Delivery } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { Skeleton } from '../../../components/ui/Skeleton'
import { cn } from '../../../utils/cn'
import { useAssignDelivery, usePartnerList } from './useAdminDeliveries'

/**
 * "Assign a partner" for a delivery nobody took: choose one of the approved partners (online ones first). The backend
 * refuses a partner who is busy with another delivery; the message says so.
 */
export function AssignPartnerButton({ delivery }: { delivery: Delivery }) {
  const assign = useAssignDelivery(delivery.id)
  const partners = usePartnerList('APPROVED')
  const groupId = useId()
  const [open, setOpen] = useState(false)
  const [chosen, setChosen] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const sorted = [...(partners.data ?? [])].sort((a, b) => Number(b.online) - Number(a.online) || a.fullName.localeCompare(b.fullName))

  function close() {
    setOpen(false)
    setChosen(null)
    setError(null)
  }

  function confirm() {
    if (chosen === null) {
      setError('Choose a partner.')
      return
    }
    assign.mutate(chosen, {
      onSuccess: () => {
        const name = sorted.find((partner) => partner.userId === chosen)?.fullName
        close()
        toast.success(`Order #${delivery.orderId} assigned to ${name}`)
      },
      onError: (assignError) => {
        if (isApiError(assignError, 401)) close()
        else setError(assignError.message)
      },
    })
  }

  let choices
  if (partners.isPending) choices = <Skeleton className="h-24" />
  else if (partners.isError) choices = <p className="text-sm text-hot">Could not load the partners: {partners.error.message}</p>
  else if (sorted.length === 0) choices = <p className="text-sm">No approved delivery partners yet.</p>
  else {
    choices = (
      <fieldset aria-describedby={error ? `${groupId}-error` : undefined}>
        <legend className="mb-2 font-heading text-sm font-bold text-heading">Delivery partner</legend>
        <div className="max-h-64 space-y-2 overflow-y-auto">
          {sorted.map((partner) => (
            <label key={partner.userId} className={cn('flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm', chosen === partner.userId ? 'border-brand bg-brand-light' : 'border-line')}>
              <input
                type="radio"
                name={groupId}
                value={partner.userId}
                checked={chosen === partner.userId}
                onChange={() => {
                  setChosen(partner.userId)
                  setError(null)
                }}
                className="size-4 accent-brand"
              />
              <span className="flex-1">
                <span className="block font-bold text-heading">{partner.fullName}</span>
                <span className="text-xs">
                  {partner.zoneName ?? 'No zone'} · {partner.phone}
                </span>
              </span>
              <span className={cn('rounded-full px-2 py-0.5 text-xs font-bold', partner.online ? 'bg-brand text-white' : 'bg-line text-heading')}>{partner.online ? 'Online' : 'Offline'}</span>
            </label>
          ))}
        </div>
      </fieldset>
    )
  }

  return (
    <>
      <Button size="sm" onClick={() => setOpen(true)} aria-label={`Assign a partner to order #${delivery.orderId}`}>
        Assign
      </Button>
      <ConfirmDialog
        open={open}
        title={`Assign order #${delivery.orderId}`}
        message={
          <div className="space-y-3">
            <p>
              From {delivery.storeName} to {delivery.customerName}. The partner gets the job at once (online or not) and the customer is told who brings the
              order.
            </p>
            {choices}
            {error && (
              <p id={`${groupId}-error`} role="alert" className="text-sm text-hot">
                {error}
              </p>
            )}
          </div>
        }
        confirmLabel="Assign"
        loading={assign.isPending}
        onConfirm={confirm}
        onCancel={close}
      />
    </>
  )
}

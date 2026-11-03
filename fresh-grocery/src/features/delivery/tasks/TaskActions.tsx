import { useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { DeliveryStep } from '../../../api/delivery'
import type { Delivery } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { formatPrice } from '../../../utils/format'
import { canReportIssue, nextTaskStep } from '../deliveryTexts'
import { ReportIssueButton } from './ReportIssueButton'
import { useTaskActions } from './useTasks'

/**
 * The buttons for what the partner can do now: Accept or Reject a new job, then one big button per step (picked up,
 * on my way, delivered, returned), and "Report a problem" while the partner has the items.
 * Rejecting and delivering a cash order ask first.
 */
export function TaskActions({ delivery }: { delivery: Delivery }) {
  const { step } = useTaskActions(delivery.id)
  const navigate = useNavigate()
  const [confirming, setConfirming] = useState<'reject' | 'deliver' | null>(null)
  const next = nextTaskStep(delivery.status)

  function take(chosen: DeliveryStep, done: string) {
    step.mutate(chosen, {
      onSuccess: () => {
        setConfirming(null)
        toast.success(done)
        // A rejected job is gone: back to today's tasks
        if (chosen === 'reject') navigate('/delivery')
      },
      onError: (error) => {
        setConfirming(null)
        if (!isApiError(error, 401)) toast.error(error.message)
      },
    })
  }

  if (delivery.status === 'ASSIGNED') {
    return (
      <div className="flex flex-wrap gap-3">
        <Button size="lg" loading={step.isPending && step.variables === 'accept'} onClick={() => take('accept', 'Accepted: go to the store')} className="flex-1 sm:flex-none">
          Accept
        </Button>
        <Button size="lg" variant="outline" onClick={() => setConfirming('reject')} className="flex-1 sm:flex-none">
          Reject
        </Button>
        <ConfirmDialog
          open={confirming === 'reject'}
          title="Reject this delivery?"
          message="It goes to another partner, and it is not offered to you again."
          confirmLabel="Reject delivery"
          cancelLabel="Keep it"
          loading={step.isPending}
          onConfirm={() => take('reject', 'Rejected: it goes to another partner')}
          onCancel={() => setConfirming(null)}
        />
      </div>
    )
  }

  if (!next && !canReportIssue(delivery.status)) return null
  // Delivering a cash order: make sure the money was collected
  const asksFirst = next?.step === 'deliver' && delivery.amountToCollect > 0

  return (
    <div className="flex flex-wrap items-center gap-3">
      {next && (
        <Button size="lg" loading={step.isPending && confirming === null} onClick={() => (asksFirst ? setConfirming('deliver') : take(next.step, next.done))} className="flex-1 sm:flex-none">
          {next.label}
        </Button>
      )}
      {canReportIssue(delivery.status) && <ReportIssueButton delivery={delivery} />}
      <ConfirmDialog
        open={confirming === 'deliver'}
        title={`Collected ${formatPrice(delivery.amountToCollect)}?`}
        message={`Only mark the order delivered when ${delivery.customerName} has it and you have the cash.`}
        confirmLabel="Yes, delivered"
        cancelLabel="Not yet"
        loading={step.isPending}
        onConfirm={() => take('deliver', 'Delivered. Well done!')}
        onCancel={() => setConfirming(null)}
      />
    </div>
  )
}

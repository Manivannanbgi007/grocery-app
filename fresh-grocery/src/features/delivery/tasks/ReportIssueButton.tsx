import { useId, useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Delivery, DeliveryIssue } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { cn } from '../../../utils/cn'
import { ISSUE_LABELS } from '../deliveryTexts'
import { useTaskActions } from './useTasks'

const ISSUES = Object.keys(ISSUE_LABELS) as DeliveryIssue[]
const NOTE_MAX = 255

/**
 * "Report a problem" once the partner has the items: why the delivery cannot be finished (and an optional note). The
 * delivery fails, the customer and the store are told, and the partner takes the items back to the store.
 */
export function ReportIssueButton({ delivery }: { delivery: Delivery }) {
  const { report } = useTaskActions(delivery.id)
  const groupId = useId()
  const noteId = useId()
  const [open, setOpen] = useState(false)
  const [issue, setIssue] = useState<DeliveryIssue | null>(null)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  function close() {
    setOpen(false)
    setError(null)
  }

  function confirm() {
    if (!issue) {
      setError('Choose what went wrong.')
      return
    }
    report.mutate(
      { issue, note: note.trim() || null },
      {
        onSuccess: () => {
          close()
          toast.success('Problem reported. Take the items back to the store.')
        },
        onError: (reportError) => {
          close()
          if (!isApiError(reportError, 401)) toast.error(reportError.message)
        },
      },
    )
  }

  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>
        Report a problem
      </Button>
      <ConfirmDialog
        open={open}
        title="What went wrong?"
        message={
          <div className="space-y-3">
            <p>The customer and the store are told. You then take the items back to {delivery.storeName}.</p>
            <fieldset aria-describedby={error ? `${groupId}-error` : undefined} className="space-y-2">
              <legend className="sr-only">The problem</legend>
              {ISSUES.map((choice) => (
                <label key={choice} className={cn('flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm text-heading', issue === choice ? 'border-brand bg-brand-light' : 'border-line')}>
                  <input
                    type="radio"
                    name={groupId}
                    checked={issue === choice}
                    onChange={() => {
                      setIssue(choice)
                      setError(null)
                    }}
                    className="size-4 accent-brand"
                  />
                  {ISSUE_LABELS[choice]}
                </label>
              ))}
            </fieldset>
            <div>
              <label htmlFor={noteId} className="mb-1 block font-heading text-sm font-bold text-heading">
                Note (optional)
              </label>
              <textarea
                id={noteId}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                maxLength={NOTE_MAX}
                rows={2}
                placeholder="e.g. Called twice, no answer"
                className="w-full rounded-lg border border-line bg-card p-3 text-sm text-heading outline-none focus:border-brand"
              />
            </div>
            {error && (
              <p id={`${groupId}-error`} role="alert" className="text-sm text-hot">
                {error}
              </p>
            )}
          </div>
        }
        confirmLabel="Report problem"
        cancelLabel="Go back"
        loading={report.isPending}
        onConfirm={confirm}
        onCancel={close}
      />
    </>
  )
}

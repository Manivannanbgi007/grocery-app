import { useId, useState } from 'react'
import { cn } from '../../utils/cn'
import { ConfirmDialog } from './ConfirmDialog'

const REASON_MAX = 255

interface ReasonDialogProps {
  open: boolean
  /** "Reject order #12?" */
  title: string
  /** what happens, e.g. "The customer is told the reason ..." */
  message: string
  /** quick choices that fill in the reason */
  commonReasons?: string[]
  /** shown when the reason is left empty, e.g. "Please tell the customer why." */
  requiredMessage: string
  confirmLabel: string
  cancelLabel?: string
  loading?: boolean
  /** red confirm button: every reason dialog so far is for a negative action (reject, disable, cancel, hide) */
  danger?: boolean
  onConfirm: (reason: string) => void
  onCancel: () => void
}

/**
 * "Are you sure?" with a required reason (at most 255 characters, the backend's limit), e.g. rejecting an order or a
 * store. The person concerned sees the reason. Quick choices fill in a common one.
 */
export function ReasonDialog({ open, title, message, commonReasons = [], requiredMessage, confirmLabel, cancelLabel = 'Keep it', loading = false, danger = true, onConfirm, onCancel }: ReasonDialogProps) {
  const reasonId = useId()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  function change(value: string) {
    setReason(value)
    setError(null)
  }

  function confirm() {
    if (!reason.trim()) {
      setError(requiredMessage)
      return
    }
    onConfirm(reason.trim())
  }

  function cancel() {
    setError(null)
    onCancel()
  }

  return (
    <ConfirmDialog
      open={open}
      title={title}
      message={
        <div className="space-y-3">
          <p>{message}</p>
          {commonReasons.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {commonReasons.map((common) => (
                <button
                  key={common}
                  type="button"
                  onClick={() => change(common)}
                  className="rounded-full border border-line px-3 py-1 text-xs text-heading hover:border-brand hover:text-brand-text"
                >
                  {common}
                </button>
              ))}
            </div>
          )}
          <div>
            <label htmlFor={reasonId} className="mb-1 block font-heading text-sm font-bold text-heading">
              Reason
            </label>
            <textarea
              id={reasonId}
              value={reason}
              onChange={(event) => change(event.target.value)}
              maxLength={REASON_MAX}
              rows={2}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${reasonId}-error` : undefined}
              className={cn('w-full rounded-lg border p-3 text-sm text-heading outline-none focus:border-brand', error ? 'border-hot' : 'border-line')}
            />
            {error && (
              <p id={`${reasonId}-error`} className="mt-1 text-sm text-hot">
                {error}
              </p>
            )}
          </div>
        </div>
      }
      confirmLabel={confirmLabel}
      cancelLabel={cancelLabel}
      loading={loading}
      danger={danger}
      onConfirm={confirm}
      onCancel={cancel}
    />
  )
}

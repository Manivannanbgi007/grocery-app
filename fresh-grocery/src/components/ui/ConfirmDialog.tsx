import { useId, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useDismiss } from '../../hooks/useDismiss'
import { Button } from './Button'

interface ConfirmDialogProps {
  open: boolean
  title: string
  message: ReactNode
  confirmLabel?: string
  cancelLabel?: string
  /** while the confirmed action runs: spinner on the confirm button, cannot be closed */
  loading?: boolean
  /** a destructive action (delete, remove, cancel an order, reject, disable): the confirm button is red */
  danger?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * "Are you sure?" pop-up, e.g. before emptying the cart.
 * Escape or a click outside counts as Cancel. The Cancel button gets the focus, so Enter never confirms by accident.
 * It only closes the dialog, so it stays neutral; with `danger` the confirm button is red.
 */
export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', cancelLabel = 'Cancel', loading = false, danger = false, onConfirm, onCancel }: ConfirmDialogProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const titleId = useId()
  const messageId = useId()

  useDismiss(panelRef, open, () => {
    if (!loading) onCancel()
  })

  if (!open) return null

  // A portal renders the dialog at the end of <body>, above everything else on the page
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/50 p-4">
      <div ref={panelRef} role="alertdialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={messageId} className="w-full max-w-md rounded-2xl border border-line bg-card p-6 shadow-xl">
        <h2 id={titleId} className="text-xl">
          {title}
        </h2>
        <div id={messageId} className="mt-2 text-body">
          {message}
        </div>
        <div className="mt-6 flex justify-end gap-3">
          <Button variant="ghost" onClick={onCancel} disabled={loading} autoFocus>
            {cancelLabel}
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

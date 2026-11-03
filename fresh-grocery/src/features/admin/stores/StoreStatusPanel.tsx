import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { StoreDetails } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { ConfirmDialog } from '../../../components/ui/ConfirmDialog'
import { ReasonDialog } from '../../../components/ui/ReasonDialog'
import { cn } from '../../../utils/cn'
import { formatDate } from '../../../utils/format'
import { useStoreActions } from './useAdminStores'

const REJECT_REASONS = ['The address could not be verified', 'The phone number does not answer', 'The store details are incomplete']
const DISABLE_REASONS = ['Many customer complaints', 'The store asked to stop selling', 'Unpaid commission']

type Dialog = 'approve' | 'reject' | 'disable' | null

/**
 * The store's status and what an admin can do about it: approve or reject a new store, disable an active one, enable a
 * disabled one. The vendor is told (notification and email) and sees the reason.
 */
export function StoreStatusPanel({ store }: { store: StoreDetails }) {
  const { approve, reject, disable, enable } = useStoreActions(store.id)
  const [dialog, setDialog] = useState<Dialog>(null)

  function failed(error: Error) {
    setDialog(null)
    if (!isApiError(error, 401)) toast.error(error.message)
  }

  const done = (message: string) => () => {
    setDialog(null)
    toast.success(message)
  }

  let title: string
  let text: string
  let actions = null
  if (store.status === 'PENDING_APPROVAL') {
    title = 'Waiting for approval'
    text = `Registered on ${formatDate(store.createdAt)}. Check the details below, then approve the store or tell the vendor what to fix.`
    actions = (
      <>
        <Button onClick={() => setDialog('approve')}>Approve</Button>
        <Button variant="outline" onClick={() => setDialog('reject')}>
          Reject
        </Button>
      </>
    )
  } else if (store.status === 'ACTIVE') {
    title = 'Active'
    text = `${store.approvedAt ? `Approved on ${formatDate(store.approvedAt)}. ` : ''}Customers can order from it${store.acceptingOrders ? '.' : ', but the vendor has paused new orders right now.'}`
    actions = (
      <Button variant="outline" onClick={() => setDialog('disable')}>
        Disable store
      </Button>
    )
  } else if (store.status === 'REJECTED') {
    title = 'Rejected'
    text = `Reason: “${store.statusReason ?? 'none given'}”. When the vendor changes the details, the store comes back to the approval queue.`
  } else {
    title = 'Disabled'
    text = `Reason: “${store.statusReason ?? 'none given'}”. Customers cannot order from it.`
    actions = (
      <Button loading={enable.isPending} onClick={() => enable.mutate(undefined, { onSuccess: done(`${store.name} is active again`), onError: failed })}>
        Enable store
      </Button>
    )
  }

  return (
    <section
      aria-labelledby="store-status-title"
      className={cn('rounded-2xl border p-6', store.status === 'PENDING_APPROVAL' ? 'border-new/40 bg-new/10' : store.status === 'ACTIVE' ? 'border-brand/30 bg-brand-light' : 'border-line bg-card')}
    >
      <h2 id="store-status-title" className="text-xl">
        {title}
      </h2>
      <p className="mt-1 text-heading">{text}</p>
      {actions && <div className="mt-4 flex flex-wrap gap-3">{actions}</div>}

      <ConfirmDialog
        open={dialog === 'approve'}
        title={`Approve ${store.name}?`}
        message="Customers can order from it once it has products in stock. The vendor is told; their vendor panel opens at their next visit."
        confirmLabel="Approve store"
        loading={approve.isPending}
        onConfirm={() => approve.mutate(undefined, { onSuccess: done(`${store.name} approved`), onError: failed })}
        onCancel={() => setDialog(null)}
      />
      <ReasonDialog
        open={dialog === 'reject'}
        title={`Reject ${store.name}?`}
        message="The vendor sees the reason and can change the details, which sends the store back for approval."
        commonReasons={REJECT_REASONS}
        requiredMessage="Please tell the vendor why."
        confirmLabel="Reject store"
        loading={reject.isPending}
        onConfirm={(reason) => reject.mutate(reason, { onSuccess: done(`${store.name} rejected`), onError: failed })}
        onCancel={() => setDialog(null)}
      />
      <ReasonDialog
        open={dialog === 'disable'}
        title={`Disable ${store.name}?`}
        message="Customers can no longer order from it; orders already placed stay as they are. The vendor sees the reason."
        commonReasons={DISABLE_REASONS}
        requiredMessage="Please tell the vendor why."
        confirmLabel="Disable store"
        loading={disable.isPending}
        onConfirm={(reason) => disable.mutate(reason, { onSuccess: done(`${store.name} disabled`), onError: failed })}
        onCancel={() => setDialog(null)}
      />
    </section>
  )
}

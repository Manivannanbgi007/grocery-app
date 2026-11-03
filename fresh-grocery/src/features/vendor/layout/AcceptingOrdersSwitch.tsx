import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { StoreDetails } from '../../../api/types'
import { cn } from '../../../utils/cn'
import { useStoreChanges } from '../useVendorStore'

/**
 * "Taking new orders" on/off: a busy store can pause new orders (customers then get "… is not taking orders right now"
 * when they try to order). Orders already placed are not affected.
 */
export function AcceptingOrdersSwitch({ store }: { store: StoreDetails }) {
  const { acceptingOrders } = useStoreChanges()
  const on = store.acceptingOrders

  function toggle() {
    acceptingOrders.mutate(!on, {
      onSuccess: (changed) => toast.success(changed.acceptingOrders ? 'You are taking new orders again' : 'New orders are paused'),
      onError: (error) => {
        if (!isApiError(error, 401)) toast.error(error.message)
      },
    })
  }

  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      onClick={toggle}
      disabled={acceptingOrders.isPending}
      className={cn(
        'flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-bold transition-colors disabled:opacity-60',
        on ? 'border-brand-soft bg-brand-light text-brand-text' : 'border-new/50 bg-new/10 text-heading',
      )}
    >
      <span aria-hidden="true" className={cn('relative h-5 w-9 rounded-full transition-colors', on ? 'bg-brand' : 'bg-muted')}>
        <span className={cn('absolute top-0.5 size-4 rounded-full bg-white shadow transition-all', on ? 'left-4.5' : 'left-0.5')} />
      </span>
      {on ? 'Taking new orders' : 'New orders paused'}
    </button>
  )
}

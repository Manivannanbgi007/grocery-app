import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Availability } from '../../../api/types'
import { cn } from '../../../utils/cn'
import { useAvailabilityChanges } from '../usePartner'

/**
 * "Online" on/off: only online partners get new deliveries. Going online may bring a waiting delivery at once; going
 * offline keeps the current delivery. The panel keeps the partner online while it is open (useStayOnline).
 */
export function OnlineSwitch({ availability }: { availability: Availability | undefined }) {
  const { online, offline } = useAvailabilityChanges()
  const on = availability?.online ?? false
  const pending = online.isPending || offline.isPending

  function toggle() {
    const change = on ? offline : online
    change.mutate(undefined, {
      onSuccess: (changed) => toast.success(changed.online ? 'You are online: new deliveries come to you' : 'You are offline: no new deliveries'),
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
      disabled={pending || availability === undefined}
      className={cn(
        'flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm font-bold transition-colors disabled:opacity-60',
        on ? 'border-brand-soft bg-brand-light text-brand-text' : 'border-line bg-surface text-heading',
      )}
    >
      <span aria-hidden="true" className={cn('relative h-5 w-9 rounded-full transition-colors', on ? 'bg-brand' : 'bg-muted')}>
        <span className={cn('absolute top-0.5 size-4 rounded-full bg-white shadow transition-all', on ? 'left-4.5' : 'left-0.5')} />
      </span>
      {on ? 'Online' : 'Offline'}
    </button>
  )
}

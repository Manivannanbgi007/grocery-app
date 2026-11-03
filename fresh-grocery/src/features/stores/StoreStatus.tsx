import type { Store } from '../../api/types'
import { Badge } from '../../components/ui/Badge'

/** "Open now" / "Closed now", and "Not taking orders" when the store paused new orders. */
export function StoreStatus({ store }: { store: Store }) {
  return (
    <span className="flex flex-wrap gap-2">
      <Badge tone={store.openNow ? 'brand' : 'neutral'}>{store.openNow ? 'Open now' : 'Closed now'}</Badge>
      {!store.acceptingOrders && <Badge tone="hot">Not taking orders right now</Badge>}
    </span>
  )
}

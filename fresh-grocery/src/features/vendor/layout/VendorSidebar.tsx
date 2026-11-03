import { Store } from 'lucide-react'
import type { StoreDetails } from '../../../api/types'
import { PanelSidebar } from '../../../components/panel/PanelSidebar'
import { useStoreOrderCount } from '../orders/useVendorOrders'
import { VENDOR_LINKS } from './vendorNavLinks'

interface VendorSidebarProps {
  store: StoreDetails
  /** phones: the menu slides in when open */
  open: boolean
  onClose: () => void
}

/** The vendor panel's menu: the store's name on top, and the number of new orders next to "Orders". */
export function VendorSidebar({ store, open, onClose }: VendorSidebarProps) {
  const { data: newOrders = 0 } = useStoreOrderCount('CONFIRMED')
  const links = VENDOR_LINKS.map((link) => (link.to === '/vendor/orders' ? { ...link, count: newOrders, countLabel: 'new' } : link))

  return (
    <PanelSidebar
      name="Vendor panel"
      home="/vendor"
      groups={[{ links }]}
      header={
        <div className="flex items-center gap-3 rounded-2xl border border-line bg-linear-to-br from-new-light to-card px-3 py-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-new to-hot text-white shadow-lg shadow-new/30">
            <Store className="size-5" aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <p className="text-xs">Your store</p>
            <p className="truncate font-heading font-bold text-heading">{store.name}</p>
          </div>
        </div>
      }
      open={open}
      onClose={onClose}
    />
  )
}

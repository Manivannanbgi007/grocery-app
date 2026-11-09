import { Bell, Settings, ShoppingBasket, Store } from 'lucide-react'
import type { StoreDetails } from '../../../api/types'
import { NotificationBell } from '../../../components/layout/NotificationBell'
import { PanelTopBar } from '../../../components/panel/PanelTopBar'
import type { ProfileAction } from '../../../components/profile/ProfileMenu'
import { AcceptingOrdersSwitch } from './AcceptingOrdersSwitch'

interface VendorTopBarProps {
  /** the store, once the panel is open (the switch needs it) */
  store?: StoreDetails
  /** opens the menu drawer on phones; left out while the panel is closed (store not ready) */
  onOpenMenu?: () => void
}

const PROFILE_ACTIONS: ProfileAction[] = [
  { to: '/vendor/store', label: 'Store settings', icon: Settings, tone: 'orange' },
  { to: '/vendor/notifications', label: 'Notifications', icon: Bell, tone: 'yellow' },
  { to: '/', label: 'See the shop', icon: ShoppingBasket, tone: 'green' },
]

/** The bar above every vendor page: menu button (phones), "taking new orders", the bell and the vendor's profile card. */
export function VendorTopBar({ store, onOpenMenu }: VendorTopBarProps) {
  return (
    <PanelTopBar
      loginPath="/vendor/login"
      onOpenMenu={onOpenMenu}
      actions={<NotificationBell />}
      profileActions={PROFILE_ACTIONS}
      profileDetails={store ? [{ label: 'Store', value: store.name, icon: Store }] : []}
    >
      {store ? <AcceptingOrdersSwitch store={store} /> : <span className="font-heading font-bold text-heading">Vendor panel</span>}
    </PanelTopBar>
  )
}

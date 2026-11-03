import { History, IdCard, Navigation, Wallet } from 'lucide-react'
import type { PanelLink } from '../../../components/panel/PanelSidebar'

/** The pages of the delivery panel, in the order of the menu. */
export const DELIVERY_LINKS: PanelLink[] = [
  { to: '/delivery', label: 'Today', icon: Navigation, end: true },
  { to: '/delivery/history', label: 'History', icon: History },
  { to: '/delivery/earnings', label: 'Earnings', icon: Wallet },
  { to: '/delivery/profile', label: 'Profile', icon: IdCard },
]

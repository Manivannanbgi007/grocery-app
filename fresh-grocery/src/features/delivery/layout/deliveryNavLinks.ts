import { History, IdCard, Navigation, Wallet } from 'lucide-react'
import type { PanelLink } from '../../../components/panel/PanelSidebar'

/** The pages of the delivery panel, in the order of the menu. */
export const DELIVERY_LINKS: PanelLink[] = [
  { to: '/delivery', label: 'Today', icon: Navigation, tone: 'blue', end: true },
  { to: '/delivery/history', label: 'History', icon: History, tone: 'teal' },
  { to: '/delivery/earnings', label: 'Earnings', icon: Wallet, tone: 'green' },
  { to: '/delivery/profile', label: 'Profile', icon: IdCard, tone: 'violet' },
]

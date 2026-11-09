import { Carrot, LayoutDashboard, Package, Star, Store, Tag } from 'lucide-react'
import type { PanelLink } from '../../../components/panel/PanelSidebar'

/** The pages of the vendor panel, in the order of the menu. */
export const VENDOR_LINKS: PanelLink[] = [
  { to: '/vendor', label: 'Dashboard', icon: LayoutDashboard, tone: 'teal', end: true },
  { to: '/vendor/orders', label: 'Orders', icon: Package, tone: 'green' },
  { to: '/vendor/products', label: 'Products', icon: Carrot, tone: 'orange' },
  { to: '/vendor/coupons', label: 'Coupons', icon: Tag, tone: 'pink' },
  { to: '/vendor/reviews', label: 'Reviews', icon: Star, tone: 'yellow' },
  { to: '/vendor/store', label: 'Store settings', icon: Store, tone: 'orange' },
]

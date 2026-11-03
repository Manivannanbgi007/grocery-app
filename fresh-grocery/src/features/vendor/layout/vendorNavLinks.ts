import { Carrot, LayoutDashboard, Package, Star, Store, Tag } from 'lucide-react'
import type { PanelLink } from '../../../components/panel/PanelSidebar'

/** The pages of the vendor panel, in the order of the menu. */
export const VENDOR_LINKS: PanelLink[] = [
  { to: '/vendor', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/vendor/orders', label: 'Orders', icon: Package },
  { to: '/vendor/products', label: 'Products', icon: Carrot },
  { to: '/vendor/coupons', label: 'Coupons', icon: Tag },
  { to: '/vendor/reviews', label: 'Reviews', icon: Star },
  { to: '/vendor/store', label: 'Store settings', icon: Store },
]

import {
  Activity,
  Bell,
  Bike,
  ChartColumn,
  Carrot,
  CreditCard,
  FolderTree,
  Image,
  LayoutDashboard,
  Map,
  Package,
  Percent,
  ScrollText,
  Settings,
  Star,
  Store,
  Tag,
  ToggleRight,
  Truck,
  UserCog,
  Users,
} from 'lucide-react'
import type { PanelLinkGroup } from '../../../components/panel/PanelSidebar'

/** The pages of the admin panel, in the order of the menu. */
export const ADMIN_LINK_GROUPS: PanelLinkGroup[] = [
  { links: [{ to: '/admin', label: 'Dashboard', icon: LayoutDashboard, end: true }] },
  {
    title: 'Operations',
    links: [
      { to: '/admin/orders', label: 'Orders', icon: Package },
      { to: '/admin/deliveries', label: 'Deliveries', icon: Truck },
      { to: '/admin/payments', label: 'Payments', icon: CreditCard },
    ],
  },
  {
    title: 'Partners',
    links: [
      { to: '/admin/stores', label: 'Stores', icon: Store },
      { to: '/admin/partners', label: 'Delivery partners', icon: Bike },
      { to: '/admin/zones', label: 'Delivery zones', icon: Map },
    ],
  },
  { title: 'People', links: [{ to: '/admin/accounts', label: 'Accounts', icon: Users }] },
  {
    title: 'Catalog',
    links: [
      { to: '/admin/products', label: 'Products', icon: Carrot },
      { to: '/admin/categories', label: 'Categories', icon: FolderTree },
      { to: '/admin/reviews', label: 'Reviews', icon: Star },
    ],
  },
  {
    title: 'Marketing',
    links: [
      { to: '/admin/coupons', label: 'Coupons', icon: Tag },
      { to: '/admin/banners', label: 'Banners', icon: Image },
    ],
  },
  {
    title: 'Insights',
    links: [
      { to: '/admin/reports', label: 'Reports', icon: ChartColumn },
      { to: '/admin/notifications', label: 'Notifications', icon: Bell },
    ],
  },
]

/** The super admin's own pages, shown below the others only to the super admin. */
export const OWNER_LINK_GROUP: PanelLinkGroup = {
  title: 'Owner',
  links: [
    { to: '/admin/admins', label: 'Admins', icon: UserCog },
    { to: '/admin/settings', label: 'Settings', icon: Settings },
    { to: '/admin/feature-flags', label: 'Feature flags', icon: ToggleRight },
    { to: '/admin/commission', label: 'Commission', icon: Percent },
    { to: '/admin/audit-log', label: 'Audit log', icon: ScrollText },
    { to: '/admin/system', label: 'System', icon: Activity },
  ],
}

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
  { links: [{ to: '/admin', label: 'Dashboard', icon: LayoutDashboard, tone: 'teal', end: true }] },
  {
    title: 'Operations',
    links: [
      { to: '/admin/orders', label: 'Orders', icon: Package, tone: 'green' },
      { to: '/admin/deliveries', label: 'Deliveries', icon: Truck, tone: 'blue' },
      { to: '/admin/payments', label: 'Payments', icon: CreditCard, tone: 'violet' },
    ],
  },
  {
    title: 'Partners',
    links: [
      { to: '/admin/stores', label: 'Stores', icon: Store, tone: 'orange' },
      { to: '/admin/partners', label: 'Delivery partners', icon: Bike, tone: 'blue' },
      { to: '/admin/zones', label: 'Delivery zones', icon: Map, tone: 'teal' },
    ],
  },
  { title: 'People', links: [{ to: '/admin/accounts', label: 'Accounts', icon: Users, tone: 'violet' }] },
  {
    title: 'Catalog',
    links: [
      { to: '/admin/products', label: 'Products', icon: Carrot, tone: 'orange' },
      { to: '/admin/categories', label: 'Categories', icon: FolderTree, tone: 'green' },
      { to: '/admin/reviews', label: 'Reviews', icon: Star, tone: 'yellow' },
    ],
  },
  {
    title: 'Marketing',
    links: [
      { to: '/admin/coupons', label: 'Coupons', icon: Tag, tone: 'pink' },
      { to: '/admin/banners', label: 'Banners', icon: Image, tone: 'violet' },
    ],
  },
  {
    title: 'Insights',
    links: [
      { to: '/admin/reports', label: 'Reports', icon: ChartColumn, tone: 'blue' },
      { to: '/admin/notifications', label: 'Notifications', icon: Bell, tone: 'yellow' },
    ],
  },
]

/** The super admin's own pages, shown below the others only to the super admin. */
export const OWNER_LINK_GROUP: PanelLinkGroup = {
  title: 'Owner',
  links: [
    { to: '/admin/admins', label: 'Admins', icon: UserCog, tone: 'violet' },
    { to: '/admin/settings', label: 'Settings', icon: Settings, tone: 'teal' },
    { to: '/admin/feature-flags', label: 'Feature flags', icon: ToggleRight, tone: 'orange' },
    { to: '/admin/commission', label: 'Commission', icon: Percent, tone: 'green' },
    { to: '/admin/audit-log', label: 'Audit log', icon: ScrollText, tone: 'yellow' },
    { to: '/admin/system', label: 'System', icon: Activity, tone: 'teal' },
  ],
}

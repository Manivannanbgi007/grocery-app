import { Package, PackageCheck, PackageX, ShoppingBag, Store, Tag, type LucideIcon } from 'lucide-react'
import type { OrderStatus } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { PanelHero } from '../../../components/panel/PanelHero'
import type { KpiAccent } from '../../../components/sales/KpiTile'
import { StatCard } from '../../../components/sales/StatCard'
import { Appear } from '../../../components/ui/Appear'
import { useAuth } from '../../auth/authContext'
import { vendorOrdersLink } from '../orders/orderTabs'
import { useStoreOrderCount } from '../orders/useVendorOrders'
import { useLowStockCount } from '../products/useVendorProducts'
import { useMyStore } from '../useVendorStore'
import { SalesSection } from './SalesSection'

/** The order statuses that wait for the store, as tiles that open that tab of the queue. */
const WORK: { status: OrderStatus; tab: string; label: string; hint: string; icon: LucideIcon; accent: KpiAccent }[] = [
  { status: 'CONFIRMED', tab: 'new', label: 'New orders', hint: 'to accept or reject', icon: ShoppingBag, accent: 'green' },
  { status: 'ACCEPTED', tab: 'accepted', label: 'Accepted', hint: 'to pick and pack', icon: PackageCheck, accent: 'blue' },
  { status: 'PACKED', tab: 'packed', label: 'Packed', hint: 'to hand over (ready for pickup)', icon: Package, accent: 'orange' },
]

/** One kind of waiting order: its number counts up ("–" while it loads); the tile opens that tab of the queue. */
function WorkTile({ status, tab, label, hint, icon, accent }: (typeof WORK)[number]) {
  const { data: count } = useStoreOrderCount(status)
  return <StatCard label={label} value={count ?? null} hint={hint} icon={icon} accent={accent} to={vendorOrdersLink(tab)} />
}

/** How many products run low (available units at or below their warning level), linking to that filter. Amber when any. */
function LowStockTile() {
  const { data: count } = useLowStockCount()
  return <StatCard label="Low stock" value={count ?? null} hint="products to restock" icon={PackageX} accent={count ? 'amber' : 'green'} to="/vendor/products?lowStock=true" />
}

/**
 * The vendor panel's start page: the hero in the vendor's orange look, what needs doing now, and the sales. The rows come
 * in one after the other; the page is a container, so its rows follow the room beside the menu.
 */
export function VendorDashboardPage() {
  const { vendor } = useAuth()
  const { data: store } = useMyStore()

  return (
    <div className="@container space-y-8">
      <PageTitle title="Vendor dashboard" />
      <Appear order={0}>
        <PanelHero
          look="vendor"
          title={`Hello, ${vendor?.fullName.split(' ')[0] ?? ''}`}
          wave
          subtitle={`${store?.name ?? 'Your store'}: here is what needs your attention.`}
          links={[
            { to: '/vendor/orders', label: 'Order queue', icon: ShoppingBag },
            { to: '/vendor/products', label: 'Products', icon: Package },
            { to: '/vendor/coupons', label: 'Coupons', icon: Tag },
            { to: '/vendor/store', label: 'Store', icon: Store },
          ]}
        />
      </Appear>
      <Appear order={1}>
        <section aria-label="What needs your attention" className="grid grid-cols-2 gap-3 sm:gap-4 @3xl:grid-cols-4">
          {WORK.map((work) => (
            <WorkTile key={work.status} {...work} />
          ))}
          <LowStockTile />
        </section>
      </Appear>
      <Appear order={2}>
        <SalesSection />
      </Appear>
    </div>
  )
}

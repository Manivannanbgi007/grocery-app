import { IndianRupee, RotateCcw, ShoppingBag, Wallet } from 'lucide-react'
import type { AdminDashboard } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Appear } from '../../../components/ui/Appear'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { formatPrice } from '../../../utils/format'
import { useAuth } from '../../auth/authContext'
import { useDashboard } from '../reports/useAdminReports'
import { AttentionTiles } from './AttentionTiles'
import { LatestOrdersCard } from './LatestOrdersCard'
import { PlatformCard } from './PlatformCard'
import { PlatformSalesSection } from './PlatformSalesSection'
import { StatCard } from '../../../components/sales/StatCard'
import { WelcomeBanner } from './WelcomeBanner'

/** Today's four numbers (report-service), each a link to the page with the details. */
function TodayCards({ dashboard }: { dashboard: AdminDashboard }) {
  const refundsWait = dashboard.pendingRefunds > 0
  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 @3xl:grid-cols-4">
      <StatCard label="Orders today" value={dashboard.ordersToday} icon={ShoppingBag} accent="green" to="/admin/orders" />
      <StatCard label="Sales today" value={dashboard.salesToday} format={formatPrice} hint="orders placed today that count" icon={IndianRupee} accent="blue" to="/admin/reports" />
      <StatCard label="Revenue today" value={dashboard.revenueToday} format={formatPrice} hint="orders delivered today" icon={Wallet} accent="orange" to="/admin/payments" />
      <StatCard
        label="Pending refunds"
        value={dashboard.pendingRefunds}
        hint="paid orders that ended without delivery"
        icon={RotateCcw}
        // amber while refunds wait, green when there are none
        accent={refundsWait ? 'amber' : 'green'}
        to="/admin/payments"
      />
    </div>
  )
}

/**
 * The admin panel's start page: a welcome, the work that waits, today's numbers, the platform beside the latest orders,
 * and the sales. The rows come in one after the other (Appear). The page is a container: its rows arrange themselves by
 * the room beside the menu (@3xl, @4xl …), not by the screen width.
 */
export function AdminDashboardPage() {
  const { admin } = useAuth()
  const dashboard = useDashboard()

  // Today's cards and the platform card show the same request: grey blocks while it loads, the error once if it fails
  let today
  let platform
  if (dashboard.isPending) {
    today = <Skeleton className="h-40 rounded-2xl" />
    platform = <Skeleton className="h-80 rounded-2xl" />
  } else if (dashboard.isError) {
    today = <ErrorState compact title="Could not load the dashboard numbers" error={dashboard.error} onRetry={() => void dashboard.refetch()} className="rounded-2xl bg-card" />
  } else {
    today = <TodayCards dashboard={dashboard.data} />
    platform = <PlatformCard dashboard={dashboard.data} />
  }

  return (
    <div className="@container space-y-8">
      <PageTitle title="Admin dashboard" />
      <Appear order={0}>
        <WelcomeBanner firstName={admin?.fullName.split(' ')[0] ?? ''} superAdmin={admin?.role === 'SUPER_ADMIN'} />
      </Appear>
      <Appear order={1}>
        <AttentionTiles />
      </Appear>
      <Appear order={2}>
        <section aria-labelledby="today-title">
          <h2 id="today-title" className="mb-3 text-xl">
            Today
          </h2>
          {today}
        </section>
      </Appear>
      {/* The platform (two thirds) beside the latest orders (one third), at the same height */}
      <div className="grid gap-6 @4xl:grid-cols-3">
        {platform && (
          <Appear order={3} className="@4xl:col-span-2">
            {platform}
          </Appear>
        )}
        <Appear order={4}>
          <LatestOrdersCard />
        </Appear>
      </div>
      <Appear order={5}>
        <PlatformSalesSection />
      </Appear>
    </div>
  )
}

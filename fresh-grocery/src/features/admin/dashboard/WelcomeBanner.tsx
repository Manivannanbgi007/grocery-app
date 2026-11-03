import { Activity, ChartColumn, Package, Store } from 'lucide-react'
import { DashboardIllustration } from '../../../components/auth/illustrations/DashboardIllustration'
import { PanelHero } from '../../../components/panel/PanelHero'

interface WelcomeBannerProps {
  firstName: string
  /** the super admin also gets a link to the System page */
  superAdmin: boolean
}

/** The top of the admin dashboard: the panel hero in the admin's dark look, with the animated dashboard drawing. */
export function WelcomeBanner({ firstName, superAdmin }: WelcomeBannerProps) {
  return (
    <PanelHero
      look="admin"
      title={`Hello, ${firstName}`}
      wave
      subtitle="Here is what needs the platform team, and how the shop is doing."
      links={[
        { to: '/admin/orders', label: 'Orders', icon: Package },
        { to: '/admin/stores', label: 'Stores', icon: Store },
        { to: '/admin/reports', label: 'Reports', icon: ChartColumn },
        ...(superAdmin ? [{ to: '/admin/system', label: 'System', icon: Activity }] : []),
      ]}
      illustration={<DashboardIllustration animated className="hidden h-52 w-auto md:block lg:h-60" />}
    />
  )
}

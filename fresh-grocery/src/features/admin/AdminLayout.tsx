import { ShoppingBasket, UserRound } from 'lucide-react'
import { useState } from 'react'
import { Outlet, ScrollRestoration } from 'react-router'
import { PageEnter } from '../../components/layout/PageEnter'
import { PanelTopBar } from '../../components/panel/PanelTopBar'
import type { ProfileAction } from '../../components/profile/ProfileMenu'
import { PanelAssistantButton } from '../assistant/AssistantButton'
import { useAuth } from '../auth/authContext'
import { AdminSidebar } from './layout/AdminSidebar'
import { RequireAdmin } from './RequireAdmin'

/** The frame of the admin panel (route element of /admin/...): only for admins and the super admin. */
export function AdminLayout() {
  return (
    <RequireAdmin>
      <AdminFrame />
    </RequireAdmin>
  )
}

function AdminFrame() {
  const { admin } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const profileActions: ProfileAction[] = [
    ...(admin ? [{ to: `/admin/accounts/${admin.id}`, label: 'My account details', icon: UserRound }] : []),
    { to: '/', label: 'See the shop', icon: ShoppingBasket },
  ]

  return (
    <div className="flex min-h-screen">
      <a href="#main" className="sr-only rounded-md bg-brand px-4 py-2 font-bold text-white focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50">
        Skip to content
      </a>
      <AdminSidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
      <div className="flex min-w-0 flex-1 flex-col">
        {/* No bell: notification-service sends nothing to admins; the menu shows the waiting work instead */}
        <PanelTopBar loginPath="/admin/login" onOpenMenu={() => setMenuOpen(true)} profileActions={profileActions}>
          <span className="font-heading font-bold text-heading">Admin panel</span>
        </PanelTopBar>
        {/* pb-24: room under the last row (e.g. the page controls), so the assistant's button in the corner never covers it */}
        <main id="main" className="flex-1 px-4 pt-4 pb-24 md:px-6 md:pt-6 lg:px-8 lg:pt-8">
          <PageEnter>
            <Outlet />
          </PageEnter>
        </main>
      </div>
      <PanelAssistantButton kind="admin" />
      <ScrollRestoration />
    </div>
  )
}

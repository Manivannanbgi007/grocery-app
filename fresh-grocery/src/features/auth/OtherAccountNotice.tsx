import { toast } from 'sonner'
import type { User } from '../../api/types'
import { Button, ButtonLink } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { aRole, PANELS, panelOf, type Panel } from './authContext'
import { useLogOutToLogin } from './loginRedirect'

interface OtherAccountNoticeProps {
  /** the logged-in user, whose account belongs to another panel */
  user: User
  /** the panel of the page that was opened */
  panel: Panel
}

const NOTICES: Record<Panel, { icon: string; title: string; goTo: string }> = {
  CUSTOMER: { icon: '🛒', title: 'This page is for customers', goTo: 'Go to the shop' },
  VENDOR: { icon: '🏪', title: 'This panel is for vendors', goTo: 'Go to the vendor panel' },
  DELIVERY: { icon: '🛵', title: 'This panel is for delivery partners', goTo: 'Go to the delivery panel' },
  ADMIN: { icon: '🛡️', title: 'This panel is for the platform team', goTo: 'Go to the admin panel' },
}

/**
 * "This page is for customers" (or vendors, partners, admins): shown instead of a page when the logged-in account belongs to
 * another panel, with a way to that panel and a way to log out (to log in with the right account).
 */
export function OtherAccountNotice({ user, panel }: OtherAccountNoticeProps) {
  const logOutToLogin = useLogOutToLogin()
  const own = panelOf(user.role)

  async function handleLogOut() {
    await logOutToLogin(PANELS[panel].login)
    toast.success('You are logged out.')
  }

  return (
    <EmptyState
      icon={NOTICES[panel].icon}
      title={NOTICES[panel].title}
      message={`You are logged in with ${aRole(user.role)} account (${user.email}). Log out to continue with another account.`}
      action={
        <div className="flex flex-wrap justify-center gap-3">
          {own && <ButtonLink to={PANELS[own].home}>{NOTICES[own].goTo}</ButtonLink>}
          <Button variant="outline" onClick={handleLogOut}>
            Log out
          </Button>
        </div>
      }
    />
  )
}

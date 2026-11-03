import { Menu } from 'lucide-react'
import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { useAuth } from '../../features/auth/authContext'
import { useLogOutToLogin } from '../../features/auth/loginRedirect'
import { ProfileMenu, type ProfileAction, type ProfileDetail } from '../profile/ProfileMenu'

interface PanelTopBarProps {
  /** where "Log out" leads (the panel's login) */
  loginPath: string
  /** opens the menu drawer on phones; left out while the panel has no menu */
  onOpenMenu?: () => void
  /** the left side, e.g. the vendor's "taking new orders" switch or the panel's name */
  children: ReactNode
  /** the right side, before the user's name, e.g. the bell */
  actions?: ReactNode
  /** the links in the user's profile card (it always ends with "Log out") */
  profileActions?: ProfileAction[]
  /** extra lines in the profile card, e.g. the vendor's store */
  profileDetails?: ProfileDetail[]
}

/** The bar above every page of a panel: menu button (phones), the panel's own controls, and the user's profile card. */
export function PanelTopBar({ loginPath, onOpenMenu, children, actions, profileActions = [], profileDetails = [] }: PanelTopBarProps) {
  const { user } = useAuth()
  const logOutToLogin = useLogOutToLogin()

  async function handleLogOut() {
    // The panel's login at its plain address (/vendor/login), without ?next=<the page the user was on>
    await logOutToLogin(loginPath)
    toast.success('You are logged out.')
  }

  return (
    <header className="glass-bar sticky top-0 z-40 flex items-center gap-3 border-b border-line px-4 py-3 md:px-6 lg:px-8">
      {onOpenMenu && (
        <button type="button" onClick={onOpenMenu} className="-ml-1 rounded-md p-1 text-heading hover:text-brand-text lg:hidden" aria-label="Open menu">
          <Menu className="size-7" aria-hidden="true" />
        </button>
      )}
      {children}
      <div className="ml-auto flex items-center gap-3 sm:gap-4">
        {actions}
        {user && <ProfileMenu user={user} actions={profileActions} details={profileDetails} onLogOut={handleLogOut} variant="panel" />}
      </div>
    </header>
  )
}

import { Bell, Heart, LayoutDashboard, MapPin, Navigation, Package, User, UserRound } from 'lucide-react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { LOGGING_OUT_STATE, PANELS, panelOf, useAuth } from '../../features/auth/authContext'
import { ProfileMenu, type ProfileAction } from '../profile/ProfileMenu'
import { HeaderIconLink } from './HeaderIconLink'

const CUSTOMER_ACTIONS: ProfileAction[] = [
  { to: '/account', label: 'My account', icon: UserRound, tone: 'violet' },
  { to: '/orders', label: 'My orders', icon: Package, tone: 'green' },
  { to: '/orders/track', label: 'Track my order', icon: Navigation, tone: 'blue' },
  { to: '/account/addresses', label: 'Addresses', icon: MapPin, tone: 'blue' },
  { to: '/wishlist', label: 'Wishlist', icon: Heart, tone: 'pink' },
  { to: '/account/notifications', label: 'Notifications', icon: Bell, tone: 'yellow' },
]

/**
 * "Account" in the header: a link to the login page for guests; once logged in, the profile card (details and the
 * customer's pages, or the way back to a vendor's or an admin's own panel).
 */
export function AccountMenu() {
  const { user, logOut } = useAuth()
  const navigate = useNavigate()

  if (!user) return <HeaderIconLink to="/login" icon={User} label="Account" />

  async function handleLogOut() {
    // Show the home page first, then log out: on a page that needs a login, its guard would otherwise send the guest
    // to the login page. flushSync: the router puts the new page on screen at once (not later, in a React transition).
    await navigate('/', { flushSync: true, state: LOGGING_OUT_STATE })
    logOut()
    toast.success('You are logged out. See you soon!')
  }

  const ownPanel = panelOf(user.role)
  const actions: ProfileAction[] =
    user.role === 'CUSTOMER' ? CUSTOMER_ACTIONS : ownPanel ? [{ to: PANELS[ownPanel].home, label: PANELS[ownPanel].label, icon: LayoutDashboard, tone: 'teal' }] : []

  return <ProfileMenu user={user} actions={actions} onLogOut={handleLogOut} variant="shop" />
}

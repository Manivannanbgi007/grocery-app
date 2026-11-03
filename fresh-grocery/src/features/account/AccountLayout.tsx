import { Bell, Heart, LogOut, MapPin, Package, UserRound, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { BreadcrumbBar, type Crumb } from '../../components/layout/Breadcrumb'
import { PageTitle } from '../../components/layout/PageTitle'
import { PanelHero } from '../../components/panel/PanelHero'
import { Appear } from '../../components/ui/Appear'
import { TONES, type Tone } from '../../components/ui/tones'
import { cn } from '../../utils/cn'
import { useAuth } from '../auth/authContext'

/** The account's pages; `tone` is only the colour of the link's icon chip (the colours of tones.ts) */
const LINKS: { to: string; label: string; icon: LucideIcon; tone: Tone }[] = [
  { to: '/account', label: 'Profile and password', icon: UserRound, tone: 'violet' },
  { to: '/account/addresses', label: 'Addresses', icon: MapPin, tone: 'blue' },
  { to: '/orders', label: 'My orders', icon: Package, tone: 'green' },
  { to: '/wishlist', label: 'Wishlist', icon: Heart, tone: 'pink' },
  { to: '/account/notifications', label: 'Notifications', icon: Bell, tone: 'yellow' },
]

/** The menu on the left of the account pages. */
function AccountNav() {
  const { logOut } = useAuth()
  const navigate = useNavigate()
  const item = 'flex w-full items-center gap-3 rounded-xl px-4 py-3 font-heading font-bold transition duration-200'

  async function handleLogOut() {
    // Show the home page first, then log out: on a page that needs a login, its guard would otherwise send the guest
    // to the login page. flushSync: the router puts the new page on screen at once (not later, in a React transition).
    await navigate('/', { flushSync: true })
    logOut()
    toast.success('You are logged out. See you soon!')
  }

  return (
    <nav aria-label="My account" className="rounded-3xl border border-line bg-card p-3">
      <ul className="space-y-1">
        {LINKS.map(({ to, label, icon: Icon, tone }) => (
          <li key={to}>
            {/* end: "/account" is only active on that page, not on "/account/addresses" */}
            {/* The current page: the green pill of every panel's menu; the others slide a little on hover */}
            <NavLink
              to={to}
              end
              className={({ isActive }) =>
                cn(item, isActive ? 'bg-linear-to-r from-brand to-brand-dark text-white shadow-lg shadow-brand/30' : 'text-heading hover:translate-x-1 hover:bg-brand-light hover:text-brand-text')
              }
            >
              {/* Each page's icon in a chip of its own colour (on the green pill of the current page: a light see-through chip) */}
              {({ isActive }) => (
                <>
                  <span aria-hidden="true" className={cn('grid size-8 shrink-0 place-items-center rounded-lg transition-colors', isActive ? 'bg-white/20' : TONES[tone].soft)}>
                    <Icon className="size-4.5" />
                  </span>
                  {label}
                </>
              )}
            </NavLink>
          </li>
        ))}
        <li className="border-t border-line pt-1">
          <button type="button" onClick={handleLogOut} className={cn(item, 'text-heading hover:bg-hot/10 hover:text-hot-text')}>
            <span aria-hidden="true" className="grid size-8 shrink-0 place-items-center rounded-lg bg-surface">
              <LogOut className="size-4.5" />
            </span>
            Log out
          </button>
        </li>
      </ul>
    </nav>
  )
}

interface AccountLayoutProps {
  title: string
  /** the path after "Home", e.g. My account › Addresses */
  crumbs: Crumb[]
  children: ReactNode
}

/**
 * The frame of the account pages: breadcrumb, a compact hero with the page's title (the customer's green look of every
 * panel's hero), the account menu on the left (on top on phones) and the page on the right.
 */
export function AccountLayout({ title, crumbs, children }: AccountLayoutProps) {
  const { customer } = useAuth()
  return (
    <>
      <PageTitle title={title} />
      <BreadcrumbBar crumbs={crumbs} />
      <div className="page-container mt-8 space-y-8">
        <Appear>
          <PanelHero
            look="shop"
            compact
            title={title}
            eyebrow={customer ? `Signed in as ${customer.fullName}` : 'My account'}
            subtitle="Your details, addresses, orders and wishlist in one place."
          />
        </Appear>
        <div className="grid items-start gap-8 lg:grid-cols-[17rem_1fr]">
          <AccountNav />
          <div className="min-w-0">{children}</div>
        </div>
      </div>
    </>
  )
}

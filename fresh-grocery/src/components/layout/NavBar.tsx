import { Headset } from 'lucide-react'
import { Link, NavLink, useLocation } from 'react-router'
import { CUSTOMER_HOME } from '../../features/auth/authContext'
import { usePlatformSettings } from '../../hooks/usePlatformSettings'
import { useShopHome } from '../../hooks/useShopHome'
import { cn } from '../../utils/cn'
import { phoneHref } from '../../utils/format'
import { CategoryMenu } from './CategoryMenu'
import { MAIN_LINKS, type MainLink } from './navLinks'

/** One pill of the main menu: icon and name; filled green for the page you are on. */
const pillClass = (current: boolean) =>
  cn(
    // A little smaller below 1536 px, so the menu fits next to "Browse All Categories" (and the support phone) from 1024 px on
    'group flex items-center gap-1.5 rounded-full px-2.5 py-2 font-heading text-sm font-bold whitespace-nowrap transition 2xl:gap-2 2xl:px-4 2xl:text-[0.95rem]',
    current ? 'bg-brand text-white shadow-md shadow-brand/30' : 'text-heading hover:bg-card hover:text-brand-text hover:shadow-sm',
  )

function PillContent({ link, current }: { link: MainLink; current: boolean }) {
  const Icon = link.icon
  // The flame of Hot Deals is pink, the other icons green; white on the green pill
  const iconColour = current ? 'text-white' : link.label === 'Hot Deals' ? 'text-hot' : 'text-brand-text'
  return (
    <>
      <Icon className={cn('size-4 shrink-0 transition-transform group-hover:scale-110', iconColour)} aria-hidden="true" />
      {link.label}
    </>
  )
}

/** The row below the header on big screens: category menu, main links and the support phone. */
export function NavBar() {
  const { data: settings } = usePlatformSettings()
  const { pathname } = useLocation()
  // Home and its sections: /customer/home for a logged-in customer, '/' for everyone else
  const { homeHref } = useShopHome()
  const links = MAIN_LINKS.map((link) => ({ ...link, to: homeHref(link.to) }))
  // "Home" is also current on a logged-in customer's own home page (CUSTOMER_HOME): it is the same page
  const isCurrent = (link: MainLink, isActive: boolean) => isActive || (link.to === '/' && pathname === CUSTOMER_HOME)

  return (
    <div className="hidden border-b border-line bg-glass lg:block">
      <div className="page-container flex h-[4.5rem] items-center gap-4 xl:gap-8">
        <CategoryMenu />

        {/* The links sit in one light track, so the menu reads as one control */}
        <nav aria-label="Main" className="flex items-center gap-1 rounded-full border border-line bg-surface p-1">
          {links.map((link) =>
            // Links to a section of the home page ("/#deals") are never "current", so they use a plain Link
            link.to.includes('#') ? (
              <Link key={link.to} to={link.to} className={pillClass(false)}>
                <PillContent link={link} current={false} />
              </Link>
            ) : (
              <NavLink key={link.to} to={link.to} end className={({ isActive }) => pillClass(isCurrent(link, isActive))}>
                {({ isActive }) => <PillContent link={link} current={isCurrent(link, isActive)} />}
              </NavLink>
            ),
          )}
        </nav>

        {settings && (
          // From 1280 px on (below it the menu needs the room; the phone is also in the footer and the phone menu)
          <a href={phoneHref(settings.supportPhone)} className="ml-auto hidden shrink-0 items-center gap-2.5 whitespace-nowrap xl:flex">
            <Headset className="size-9 text-heading" strokeWidth={1.5} aria-hidden="true" />
            <span className="flex flex-col leading-tight">
              <span className="font-heading text-2xl font-bold text-brand-text">{settings.supportPhone}</span>
              <span className="text-xs">Support Center</span>
            </span>
          </a>
        )}
      </div>
    </div>
  )
}

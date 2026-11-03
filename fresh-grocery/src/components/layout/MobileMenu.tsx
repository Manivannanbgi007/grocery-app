import { BriefcaseBusiness, LayoutDashboard, LogIn, LogOut, Mail, MapPin, Package, Phone, User, X } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { Link, useNavigate } from 'react-router'
import { toast } from 'sonner'
import { PANELS, panelOf, useAuth } from '../../features/auth/authContext'
import { useCategories } from '../../hooks/useCategories'
import { useDismiss } from '../../hooks/useDismiss'
import { usePlatformSettings } from '../../hooks/usePlatformSettings'
import { cn } from '../../utils/cn'
import { categoryEmoji } from '../../utils/emoji'
import { phoneHref } from '../../utils/format'
import { shopLink } from '../../utils/links'
import { Logo } from './Logo'
import { MAIN_LINKS } from './navLinks'

interface MobileMenuProps {
  open: boolean
  onClose: () => void
}

/** The menu that slides in from the left on phones and tablets (the ☰ button in the header). */
export function MobileMenu({ open, onClose }: MobileMenuProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const { data: categories = [] } = useCategories()
  const { data: settings } = usePlatformSettings()
  const { user, logOut } = useAuth()
  const navigate = useNavigate()
  const ownPanel = user ? panelOf(user.role) : null

  useDismiss(panelRef, open, onClose)

  async function handleLogOut() {
    onClose()
    // Show the home page first, then log out: on a page that needs a login, its guard would otherwise send the guest
    // to the login page. flushSync: the router puts the new page on screen at once (not later, in a React transition).
    await navigate('/', { flushSync: true })
    logOut()
    toast.success('You are logged out. See you soon!')
  }

  // While the menu is open the page behind it must not scroll
  useEffect(() => {
    if (!open) return
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = ''
    }
  }, [open])

  if (!open) return null

  // Every row has an icon in front, so the names line up
  const linkClass = 'flex items-center gap-3 rounded-md px-3 py-2.5 font-heading font-bold text-heading hover:bg-brand-light hover:text-brand-text'
  const iconClass = 'size-4 shrink-0 text-brand'

  return (
    <div className="fixed inset-0 z-50 lg:hidden">
      <div className="absolute inset-0 bg-ink/50" aria-hidden="true" />

      <div ref={panelRef} role="dialog" aria-modal="true" aria-label="Menu" className="absolute inset-y-0 left-0 flex w-80 max-w-[85vw] flex-col gap-6 overflow-y-auto bg-card p-5 shadow-xl">
        <div className="flex items-center justify-between">
          <Logo />
          <button type="button" onClick={onClose} className="rounded-full p-2 text-heading hover:bg-brand-light" aria-label="Close menu" autoFocus>
            <X className="size-6" aria-hidden="true" />
          </button>
        </div>

        <nav aria-label="Mobile">
          <ul>
            {MAIN_LINKS.map((link) => (
              <li key={link.to}>
                <Link to={link.to} onClick={onClose} className={linkClass}>
                  <link.icon className={cn(iconClass, link.label === 'Hot Deals' && 'text-hot')} aria-hidden="true" />
                  {link.label}
                </Link>
              </li>
            ))}
            {user?.role === 'CUSTOMER' && (
              <>
                <li>
                  <Link to="/account" onClick={onClose} className={linkClass}>
                    <User className={iconClass} aria-hidden="true" />
                    My account
                  </Link>
                </li>
                <li>
                  <Link to="/orders" onClick={onClose} className={linkClass}>
                    <Package className={iconClass} aria-hidden="true" />
                    My orders
                  </Link>
                </li>
                <li>
                  <Link to="/orders/track" onClick={onClose} className={linkClass}>
                    <MapPin className={iconClass} aria-hidden="true" />
                    Track my order
                  </Link>
                </li>
              </>
            )}
            {ownPanel && ownPanel !== 'CUSTOMER' && (
              <li>
                <Link to={PANELS[ownPanel].home} onClick={onClose} className={linkClass}>
                  <LayoutDashboard className={iconClass} aria-hidden="true" />
                  {PANELS[ownPanel].label}
                </Link>
              </li>
            )}
            <li>
              {user ? (
                <button type="button" onClick={handleLogOut} className={cn(linkClass, 'w-full text-left')}>
                  <LogOut className={iconClass} aria-hidden="true" />
                  Log out ({user.fullName.split(' ')[0]})
                </button>
              ) : (
                <Link to="/login" onClick={onClose} className={linkClass}>
                  <LogIn className={iconClass} aria-hidden="true" />
                  Log in
                </Link>
              )}
            </li>
            <li>
              <Link to="/register?role=VENDOR" onClick={onClose} className={linkClass}>
                <BriefcaseBusiness className={iconClass} aria-hidden="true" />
                Become Vendor
              </Link>
            </li>
          </ul>
        </nav>

        {categories.length > 0 && (
          <section aria-labelledby="mobile-menu-categories">
            <h2 id="mobile-menu-categories" className="mb-2 px-3 text-lg">
              Categories
            </h2>
            <ul>
              {categories.map((category) => (
                <li key={category.id}>
                  <Link to={shopLink({ category: category.id })} onClick={onClose} className="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-heading hover:bg-brand-light">
                    <span aria-hidden="true">{categoryEmoji(category.slug)}</span>
                    {category.name}
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}

        {settings && (
          <div className="mt-auto space-y-2 border-t border-line px-3 pt-4 text-sm">
            <a href={phoneHref(settings.supportPhone)} className="flex items-center gap-2 hover:text-brand-text">
              <Phone className="size-4 text-brand" aria-hidden="true" />
              {settings.supportPhone}
            </a>
            <a href={`mailto:${settings.supportEmail}`} className="flex items-center gap-2 hover:text-brand-text">
              <Mail className="size-4 text-brand" aria-hidden="true" />
              {settings.supportEmail}
            </a>
          </div>
        )}
      </div>
    </div>
  )
}

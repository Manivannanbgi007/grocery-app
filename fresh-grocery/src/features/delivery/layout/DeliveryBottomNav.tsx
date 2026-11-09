import { NavLink } from 'react-router'
import { MenuIcon } from '../../../components/ui/ToneIcon'
import { cn } from '../../../utils/cn'
import { DELIVERY_LINKS } from './deliveryNavLinks'

/**
 * Phones and tablets: the delivery panel's pages in a bar at the bottom of the screen, in reach of the thumb (the
 * partner is often on the move). Big screens use the menu on the left instead.
 */
export function DeliveryBottomNav() {
  return (
    <nav aria-label="Delivery pages" className="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-card/95 pb-[env(safe-area-inset-bottom)] backdrop-blur lg:hidden print:hidden">
      <ul className="mx-auto grid max-w-lg grid-cols-4">
        {DELIVERY_LINKS.map(({ to, label, icon, tone, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) =>
                cn('group flex flex-col items-center gap-1 px-1 pt-2 pb-2.5 text-xs font-bold transition-colors', isActive ? 'text-brand-text' : 'text-body hover:text-heading')
              }
            >
              {({ isActive }) => (
                <>
                  {/* Each page's icon in a filled tile of its colour; the current page's sits in the green pill with a glow,
                      as in every panel's menu */}
                  <span
                    className={cn(
                      'grid h-9 w-14 place-items-center rounded-full transition duration-200',
                      isActive && 'bg-linear-to-r from-brand to-brand-dark shadow-md shadow-brand/30',
                    )}
                  >
                    <MenuIcon icon={icon} tone={tone} active={isActive} className="size-7 rounded-md" />
                  </span>
                  {label}
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

import { ChevronLeft, ChevronRight, ExternalLink, X, type LucideIcon } from 'lucide-react'
import { useId, useRef, useState, type FocusEvent, type MouseEvent, type ReactNode } from 'react'
import { NavLink } from 'react-router'
import { useCollapsedMenu } from '../../hooks/useCollapsedMenu'
import { useDismiss } from '../../hooks/useDismiss'
import { cn } from '../../utils/cn'
import { Logo } from '../layout/Logo'
import { MenuIcon } from '../ui/ToneIcon'
import type { Tone } from '../ui/tones'

/** One page in a panel's menu. */
export interface PanelLink {
  to: string
  label: string
  icon: LucideIcon
  /** the colour of the icon's tile, by what the page is about (the meanings in tones.ts) */
  tone: Tone
  /** true = only marked as the current page on exactly this address (the dashboard) */
  end?: boolean
  /** work waiting on that page, shown as a red number (left out or 0 = nothing shown) */
  count?: number
  /** what the number counts, for screen readers: "Orders, 3 new" */
  countLabel?: string
}

/** Links under one heading; a panel with few pages has one group without a heading. */
export interface PanelLinkGroup {
  title?: string
  links: PanelLink[]
}

interface PanelSidebarProps {
  /** the menu's name for screen readers, e.g. "Vendor panel" */
  name: string
  /** the panel's start page (the logo leads there) */
  home: string
  groups: PanelLinkGroup[]
  /** shown under the logo, e.g. the vendor's store */
  header?: ReactNode
  /** phones: the menu slides in when open (left out: no drawer, e.g. the delivery panel has a bar at the bottom) */
  open?: boolean
  onClose?: () => void
  /** true for a dark menu (the admin panel); the vendor and delivery panels have a white one */
  dark?: boolean
}

/** The name of a collapsed menu's icon, shown beside it; `top` is the middle of the icon on the screen. */
interface Tip {
  label: string
  top: number
}

interface MenuContentProps extends Omit<PanelSidebarProps, 'open'> {
  /** big screens only: icons without their names */
  collapsed?: boolean
  /** a collapsed menu reports the icon under the mouse or the keyboard focus (null: none) */
  onTip?: (tip: Tip | null) => void
}

/** What is inside the menu: logo, header, the links and "See the shop". The same for the column and the drawer. */
function MenuContent({ name, home, groups, header, onClose, dark = false, collapsed = false, onTip }: MenuContentProps) {
  const groupId = useId()

  // A collapsed menu is too narrow for the names: the name shows beside the icon instead
  function showTip(event: MouseEvent<HTMLElement> | FocusEvent<HTMLElement>, label: string) {
    if (!collapsed || !onTip) return
    const box = event.currentTarget.getBoundingClientRect()
    onTip({ label, top: box.top + box.height / 2 })
  }
  const tipEvents = (label: string) => ({
    onMouseEnter: (event: MouseEvent<HTMLElement>) => showTip(event, label),
    onFocus: (event: FocusEvent<HTMLElement>) => showTip(event, label),
    onMouseLeave: () => onTip?.(null),
    onBlur: () => onTip?.(null),
  })

  return (
    <>
      <div className={cn('flex items-center gap-2', collapsed ? 'justify-center' : 'justify-between')}>
        <Logo to={home} label={`${name}, dashboard`} onDark={dark} compact={collapsed} />
        <button
          type="button"
          onClick={onClose}
          className={cn('rounded-full p-2 lg:hidden', dark ? 'text-white hover:bg-ink-line' : 'text-heading hover:bg-brand-light')}
          aria-label="Close menu"
        >
          <X className="size-6" aria-hidden="true" />
        </button>
      </div>
      {!collapsed && header}
      <nav aria-label={name} className={collapsed ? 'space-y-4' : 'space-y-6'}>
        {groups.map((group, index) => (
          // Collapsed: a line between the groups instead of their headings
          <div key={group.title ?? index} className={cn(collapsed && index > 0 && 'border-t pt-4', dark ? 'border-ink-line' : 'border-line')}>
            {group.title && (
              <p
                id={`${groupId}-${index}`}
                className={cn(collapsed ? 'sr-only' : 'mb-2 px-3 text-xs font-bold tracking-widest uppercase', dark ? 'text-ink-text' : 'text-body')}
              >
                {group.title}
              </p>
            )}
            <ul aria-labelledby={group.title ? `${groupId}-${index}` : undefined} className="space-y-1">
              {group.links.map(({ to, label, icon, tone, end, count = 0, countLabel }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    end={end}
                    onClick={onClose}
                    aria-label={count > 0 ? `${label}, ${count} ${countLabel ?? 'waiting'}` : undefined}
                    {...tipEvents(label)}
                    className={({ isActive }) =>
                      cn(
                        'group relative flex items-center gap-3 rounded-xl py-1.5 font-heading font-bold whitespace-nowrap transition duration-200',
                        collapsed ? 'justify-center' : 'px-2',
                        // The current page: a green pill with a soft glow, in every panel; the others slide a little on hover
                        isActive
                          ? 'bg-linear-to-r from-brand to-brand-dark text-white shadow-lg shadow-brand/30'
                          : cn(!collapsed && 'hover:translate-x-1', dark ? 'text-ink-text hover:bg-white/5 hover:text-white' : 'text-heading hover:bg-surface hover:text-brand-text'),
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {/* Each page's icon in a filled tile of its own colour (see-through white on the current page's pill) */}
                        <MenuIcon icon={icon} tone={tone} active={isActive} className={collapsed ? 'size-9' : undefined} />
                        {/* Collapsed: the name is only for screen readers (and in the tip) */}
                        <span className={collapsed ? 'sr-only' : 'flex-1'}>{label}</span>
                        {count > 0 && (
                          <span className={cn('rounded-full bg-hot text-xs text-white', collapsed ? 'absolute top-0.5 right-1 min-w-5 px-1 text-center leading-5' : 'px-2 py-0.5')}>
                            {count}
                          </span>
                        )}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>
      <a
        href="/"
        target="_blank"
        rel="noreferrer"
        {...tipEvents('See the shop as customers do')}
        className={cn(
          'mt-auto flex items-center gap-2 text-sm font-bold hover:underline',
          collapsed ? 'justify-center py-2' : 'px-3',
          dark ? 'text-ink-text hover:text-white' : 'text-brand-text',
        )}
      >
        <ExternalLink className="size-4 shrink-0" aria-hidden="true" />
        <span className={cn(collapsed && 'sr-only')}>See the shop as customers do</span>
      </a>
    </>
  )
}

/**
 * A panel's menu. Big screens: a column on the left, which the round button on its edge collapses to icons and expands
 * again (the choice is saved in the browser). Phones and tablets: a drawer (☰ in the top bar).
 */
export function PanelSidebar({ open = false, ...menu }: PanelSidebarProps) {
  const panelRef = useRef<HTMLElement>(null)
  const columnId = useId()
  const [collapsed, toggleCollapsed] = useCollapsedMenu()
  const [tip, setTip] = useState<Tip | null>(null)
  useDismiss(panelRef, open, () => menu.onClose?.())

  // The column beside the page is glass over the gradient; the phone drawer floats over the page, so it stays solid
  const surface = menu.dark ? 'bg-ink [scrollbar-color:var(--color-ink-line)_transparent]' : 'bg-glass'
  const drawerSurface = menu.dark ? surface : 'bg-card'
  const toggleLabel = collapsed ? 'Expand menu' : 'Collapse menu'

  return (
    <>
      {/* Big screens: a fixed column; the button may stick out of it, so it sits beside the scrolling part */}
      <div className="sticky top-0 z-50 hidden h-screen shrink-0 lg:block">
        <aside
          id={columnId}
          onScroll={() => setTip(null)}
          className={cn(
            'flex h-full flex-col gap-6 overflow-y-auto py-5 transition-[width] duration-200 [scrollbar-width:thin] motion-reduce:transition-none',
            collapsed ? 'w-20 px-3' : 'w-64 px-5',
            surface,
            !menu.dark && 'border-r border-line',
          )}
        >
          <MenuContent {...menu} collapsed={collapsed} onTip={setTip} />
        </aside>
        <button
          type="button"
          onClick={() => {
            setTip(null)
            toggleCollapsed()
          }}
          aria-label={toggleLabel}
          title={toggleLabel}
          aria-controls={columnId}
          aria-expanded={!collapsed}
          className="absolute top-7.5 -right-3.5 grid size-7 place-items-center rounded-full border border-line bg-card text-heading shadow-md transition-colors hover:border-brand hover:text-brand-text"
        >
          {collapsed ? <ChevronRight className="size-4" aria-hidden="true" /> : <ChevronLeft className="size-4" aria-hidden="true" />}
        </button>
        {/* The name of the icon under the mouse or focus; screen readers already get it from the link */}
        {collapsed && tip && (
          <span
            aria-hidden="true"
            style={{ top: tip.top }}
            className="pointer-events-none fixed left-22 -translate-y-1/2 rounded-lg bg-ink px-3 py-1.5 text-sm font-bold whitespace-nowrap text-white shadow-lg"
          >
            {tip.label}
          </span>
        )}
      </div>
      {/* Phones and tablets: a drawer */}
      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-ink/50" aria-hidden="true" />
          <aside ref={panelRef} aria-label="Menu" className={cn('absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col gap-6 overflow-y-auto p-5 shadow-xl', drawerSurface)}>
            <MenuContent {...menu} />
          </aside>
        </div>
      )}
    </>
  )
}

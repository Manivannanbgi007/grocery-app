import { CalendarDays, ChevronDown, Clock, LogOut, Mail, Phone, User as UserIcon, type LucideIcon } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { Link } from 'react-router'
import type { User } from '../../api/types'
import { ROLE_NAMES } from '../../features/auth/authContext'
import { useDismiss } from '../../hooks/useDismiss'
import { cn } from '../../utils/cn'
import { formatDate, formatDateTime } from '../../utils/format'
import { Avatar } from './Avatar'

/** A link in the profile card, e.g. "My orders". */
export interface ProfileAction {
  to: string
  label: string
  icon: LucideIcon
}

/** One more line of details in the profile card, e.g. the vendor's store. */
export interface ProfileDetail {
  label: string
  value: string
  icon: LucideIcon
  /** show the label before the value ("Member since 1 Jan 2026"); otherwise only screen readers hear it */
  visibleLabel?: boolean
}

interface ProfileMenuProps {
  user: User
  /** the links of this role (the card always ends with "Log out") */
  actions: ProfileAction[]
  details?: ProfileDetail[]
  onLogOut: () => void
  /** shop: the header's icon style; panel: the avatar with the name and role */
  variant: 'shop' | 'panel'
}

/**
 * The account button of the shop's header and of the panels' top bar. It opens a card with the person's details (name,
 * role, email, phone, member since, last login) and the actions of the role. Escape or a click outside closes it.
 */
export function ProfileMenu({ user, actions, details = [], onLogOut, variant }: ProfileMenuProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const cardId = useId()
  const nameId = useId()
  const firstName = user.fullName.split(' ')[0]

  useDismiss(containerRef, open, (reason) => {
    setOpen(false)
    if (reason === 'escape') buttonRef.current?.focus()
  })

  const lines: ProfileDetail[] = [
    { label: 'Email', value: user.email, icon: Mail },
    { label: 'Phone', value: user.phone, icon: Phone },
    ...details,
    { label: 'Member since', value: formatDate(user.createdAt), icon: CalendarDays, visibleLabel: true },
    ...(user.lastLoginAt ? [{ label: 'Last login', value: formatDateTime(user.lastLoginAt), icon: Clock, visibleLabel: true }] : []),
  ]

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((isOpen) => !isOpen)}
        aria-expanded={open}
        aria-controls={cardId}
        aria-label={`Account of ${user.fullName}`}
        className={cn(
          'flex items-center gap-2 text-heading hover:text-brand-text',
          variant === 'panel' && 'rounded-full py-0.5 pr-2 pl-0.5 transition-colors hover:bg-surface',
        )}
      >
        {variant === 'shop' ? (
          <>
            <UserIcon className="size-6" strokeWidth={1.75} aria-hidden="true" />
            <span className="hidden text-sm xl:inline">Hi, {firstName}</span>
          </>
        ) : (
          <>
            <Avatar name={user.fullName} />
            <span className="hidden text-left leading-tight sm:block">
              <span className="block text-sm font-bold text-heading">{user.fullName}</span>
              <span className="block text-xs text-body">{ROLE_NAMES[user.role]}</span>
            </span>
          </>
        )}
        <ChevronDown className={cn('size-4 transition-transform', variant === 'shop' && 'hidden xl:block', open && 'rotate-180')} aria-hidden="true" />
      </button>

      {open && (
        <section
          id={cardId}
          aria-labelledby={nameId}
          // Phones: the width of the screen; bigger screens: under the button, at its right edge
          className="fixed inset-x-4 top-16 z-50 overflow-hidden rounded-2xl border border-line bg-card text-left shadow-xl sm:absolute sm:inset-x-auto sm:top-full sm:right-0 sm:mt-3 sm:w-80"
        >
          <div className="flex items-center gap-3 bg-linear-to-br from-brand-light to-card px-4 py-4">
            <Avatar name={user.fullName} size="lg" />
            <div className="min-w-0">
              <h2 id={nameId} className="truncate text-base">
                {user.fullName}
              </h2>
              <p className="mt-1 flex flex-wrap items-center gap-1.5 text-xs">
                <span className="rounded-full bg-brand px-2 py-0.5 font-bold text-white">{ROLE_NAMES[user.role]}</span>
                <span className="flex items-center gap-1">
                  <span aria-hidden="true" className={cn('size-1.5 rounded-full', user.status === 'ACTIVE' ? 'bg-brand' : 'bg-hot')} />
                  {user.status === 'ACTIVE' ? 'Active' : 'Disabled'}
                </span>
              </p>
            </div>
          </div>

          <dl className="space-y-2 border-y border-line px-4 py-3 text-sm">
            {lines.map(({ label, value, icon: Icon, visibleLabel }) => (
              <div key={label} className="flex items-center gap-2">
                <Icon className="mr-1 size-4 shrink-0 text-brand-text" aria-hidden="true" />
                <dt className={cn('shrink-0', !visibleLabel && 'sr-only')}>{label}</dt>
                <dd className="min-w-0 truncate text-heading" title={value}>
                  {value}
                </dd>
              </div>
            ))}
          </dl>

          {actions.length > 0 && (
            <ul className="p-2">
              {actions.map(({ to, label, icon: Icon }) => (
                <li key={to}>
                  <Link to={to} onClick={() => setOpen(false)} className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-heading hover:bg-brand-light hover:text-brand-text">
                    <Icon className="size-4 shrink-0" aria-hidden="true" />
                    {label}
                  </Link>
                </li>
              ))}
            </ul>
          )}

          <div className="border-t border-line p-2">
            <button
              type="button"
              onClick={() => {
                setOpen(false)
                onLogOut()
              }}
              className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-bold text-heading hover:bg-hot/10"
            >
              <LogOut className="size-4" aria-hidden="true" />
              Log out
            </button>
          </div>
        </section>
      )}
    </div>
  )
}

import type { LucideIcon } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'

interface HeaderIconLinkProps {
  to: string
  icon: LucideIcon
  label: string
  /** shown in a green bubble on the icon; leave out when unknown */
  count?: number
  className?: string
  /** overrides when the label shows (from xl by default), e.g. "xl:hidden 2xl:inline" */
  labelClassName?: string
}

/** Compare / Wishlist / Cart / Account in the header: an icon with an optional counter and a label on big screens. */
export function HeaderIconLink({ to, icon: Icon, label, count, className, labelClassName }: HeaderIconLinkProps) {
  const accessibleName = count === undefined ? label : `${label}, ${count} ${count === 1 ? 'item' : 'items'}`
  return (
    <Link to={to} className={cn('flex items-center gap-3.5 text-heading hover:text-brand-text', className)} aria-label={accessibleName}>
      <span className="relative">
        <Icon className="size-6" strokeWidth={1.75} aria-hidden="true" />
        {count !== undefined && (
          <span className="absolute -top-2 -right-2.5 flex size-5 items-center justify-center rounded-full bg-brand text-[0.65rem] font-bold text-white">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </span>
      <span className={cn('hidden text-sm xl:inline', labelClassName)}>{label}</span>
    </Link>
  )
}

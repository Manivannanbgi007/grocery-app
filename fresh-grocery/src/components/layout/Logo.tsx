import { Link } from 'react-router'
import { useShopHome } from '../../hooks/useShopHome'
import { cn } from '../../utils/cn'

/** The leaf mark, in the theme's green (same drawing as public/favicon.svg, which has the colour written in). */
function LeafMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={className} aria-hidden="true">
      <rect width="64" height="64" rx="16" className="fill-brand" />
      <path d="M17 47C14 31 24 17 48 15c1 22-11 33-27 32Z" fill="#fff" />
      <path d="M19 45c6-9 13-16 22-22" fill="none" strokeWidth="3.5" strokeLinecap="round" className="stroke-brand" />
    </svg>
  )
}

interface LogoProps {
  className?: string
  /** where it links to: the shop's home page, or e.g. the vendor panel's start page */
  to?: string
  label?: string
  /** true on a dark background (the admin menu): "Fresh" in the light green of ink surfaces, "GROCERY" in white */
  onDark?: boolean
  /** true: the leaf mark only (a collapsed panel menu) */
  compact?: boolean
  /** extra classes for the words "Fresh GROCERY", e.g. to hide them on the narrowest phones */
  wordmarkClassName?: string
}

/** "Fresh GROCERY" with the leaf mark; links to the home page. */
export function Logo({ className, to, label = 'Fresh Grocery, home', onDark = false, compact = false, wordmarkClassName }: LogoProps) {
  // Without `to`: the shop's home page for this visitor (/customer/home for a logged-in customer)
  const { home } = useShopHome()
  return (
    <Link to={to ?? home} className={cn('flex shrink-0 items-center gap-2', className)} aria-label={label}>
      <LeafMark className={compact ? 'size-10' : 'size-10 lg:size-12'} />
      {!compact && (
        <span className={cn('flex flex-col leading-none', wordmarkClassName)}>
          <span className={cn('font-heading text-3xl font-bold lg:text-4xl', onDark ? 'text-ink-brand' : 'text-brand-text')}>Fresh</span>
          <span className={cn('font-heading text-[0.6rem] font-bold tracking-[0.3em] lg:text-[0.65rem]', onDark ? 'text-white' : 'text-heading')}>GROCERY</span>
        </span>
      )}
    </Link>
  )
}

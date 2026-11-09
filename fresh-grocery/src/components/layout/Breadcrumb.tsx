import { ChevronRight, House } from 'lucide-react'
import { Link } from 'react-router'
import { useShopHome } from '../../hooks/useShopHome'
import { cn } from '../../utils/cn'

/** One step of the path; the last one (the current page) has no link. */
export interface Crumb {
  label: string
  to?: string
}

/** Home › Shop › Tomato */
export function Breadcrumb({ crumbs, className }: { crumbs: Crumb[]; className?: string }) {
  const { home } = useShopHome()
  return (
    <nav aria-label="Breadcrumb" className={className}>
      <ol className="flex flex-wrap items-center gap-2 text-sm">
        <li>
          <Link to={home} className="flex items-center gap-1.5 font-bold text-brand-text hover:underline">
            <House className="size-4" aria-hidden="true" />
            Home
          </Link>
        </li>
        {crumbs.map((crumb, index) => {
          const isCurrent = index === crumbs.length - 1
          return (
            <li key={crumb.label} className="flex items-center gap-2">
              <ChevronRight className="size-3.5 text-muted" aria-hidden="true" />
              {crumb.to && !isCurrent ? (
                <Link to={crumb.to} className="font-bold text-brand-text hover:underline">
                  {crumb.label}
                </Link>
              ) : (
                <span aria-current={isCurrent ? 'page' : undefined} className={cn(isCurrent && 'text-body')}>
                  {crumb.label}
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

/** The breadcrumb in its own full-width row under the menu (shop, wishlist, product pages). */
export function BreadcrumbBar({ crumbs }: { crumbs: Crumb[] }) {
  return (
    <div className="border-b border-line">
      <Breadcrumb crumbs={crumbs} className="page-container py-5" />
    </div>
  )
}

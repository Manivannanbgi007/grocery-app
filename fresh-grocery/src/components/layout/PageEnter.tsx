import type { ReactNode } from 'react'
import { useLocation } from 'react-router'

/**
 * Each page rises and fades in when it opens (in every panel's layout, around the <Outlet />). The key makes React start
 * the animation again on every new address; a change of only the ?query (a filter, a tab) does not. Nothing moves with
 * "reduce motion".
 */
export function PageEnter({ children }: { children: ReactNode }) {
  const { pathname } = useLocation()
  return (
    <div key={pathname} className="motion-safe:animate-rise">
      {children}
    </div>
  )
}

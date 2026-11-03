import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface AppearProps {
  /** 0, 1, 2 …: the blocks of a page come in one after the other, 70 ms apart */
  order?: number
  className?: string
  children: ReactNode
}

/** A block that rises and fades in when the page opens (not when the system asks for less motion). */
export function Appear({ order = 0, className, children }: AppearProps) {
  return (
    <div className={cn('motion-safe:animate-rise', className)} style={{ animationDelay: `${order * 70}ms` }}>
      {children}
    </div>
  )
}

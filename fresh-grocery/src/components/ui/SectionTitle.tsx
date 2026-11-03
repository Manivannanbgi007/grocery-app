import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface SectionTitleProps {
  title: string
  /** shown on the right: tabs, a "View all" link, slider arrows ... */
  children?: ReactNode
  /** used by links that jump to this section, e.g. id="deals" for "/#deals" */
  id?: string
  className?: string
}

/** The big title above a home or shop section ("Popular Products"), with optional controls on the right. */
export function SectionTitle({ title, children, id, className }: SectionTitleProps) {
  return (
    <div className={cn('mb-6 flex flex-wrap items-end justify-between gap-x-6 gap-y-3', className)}>
      <h2 id={id} className="scroll-mt-6 text-2xl md:text-3xl">
        {title}
      </h2>
      {children}
    </div>
  )
}

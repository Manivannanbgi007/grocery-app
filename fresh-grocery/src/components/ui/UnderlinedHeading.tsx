import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

/** A heading underlined by a grey line with a short green part at the start, as in the sample's sidebar and columns. */
export function UnderlinedHeading({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <h2 className={cn('relative mb-6 border-b-2 border-line pb-4 text-xl after:absolute after:-bottom-0.5 after:left-0 after:h-0.5 after:w-20 after:bg-brand-soft', className)}>
      {children}
    </h2>
  )
}

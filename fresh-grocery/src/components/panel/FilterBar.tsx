import { SlidersHorizontal } from 'lucide-react'
import type { ReactNode } from 'react'

/** The search boxes and drop-downs of a list, together in one card row with a "Filter" label. */
export function FilterBar({ children }: { children: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-center gap-3 rounded-2xl border border-line bg-card p-3">
      <span className="flex items-center gap-2 px-1 font-heading text-sm font-bold text-heading">
        <SlidersHorizontal className="size-4 text-brand-text" aria-hidden="true" />
        Filter
      </span>
      {children}
    </div>
  )
}

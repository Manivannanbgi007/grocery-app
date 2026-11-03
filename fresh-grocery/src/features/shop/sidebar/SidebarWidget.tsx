import type { ReactNode } from 'react'
import { UnderlinedHeading } from '../../../components/ui/UnderlinedHeading'
import { cn } from '../../../utils/cn'

interface SidebarWidgetProps {
  title: string
  children: ReactNode
  /** the plain search box has no card around it in the sample */
  plain?: boolean
}

/** One box of the shop sidebar: a white card with a shadow and an underlined title. */
export function SidebarWidget({ title, children, plain = false }: SidebarWidgetProps) {
  return (
    <section className={cn(!plain && 'rounded-2xl border border-line bg-card p-5 shadow-sm shadow-heading/5')}>
      {plain ? <h2 className="mb-2 text-lg">{title}</h2> : <UnderlinedHeading className="mb-4 pb-3 text-lg">{title}</UnderlinedHeading>}
      {children}
    </section>
  )
}

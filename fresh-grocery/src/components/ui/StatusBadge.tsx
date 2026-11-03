import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'
import type { BadgeTone } from './Badge'
import { STATUS_TONES } from './statusTones'

interface StatusBadgeProps {
  tone: BadgeTone
  /** an icon instead of the dot, e.g. a check for "Paid" */
  icon?: LucideIcon
  /** the dot pulses: work that waits, or someone online now */
  pulse?: boolean
  className?: string
  children: ReactNode
}

/**
 * A status as a soft pill: a dot (or an icon) in the status colour and the word, e.g. "● Waiting for a partner". The
 * word always says the status, so the colour never stands alone.
 */
export function StatusBadge({ tone, icon: Icon, pulse = false, className, children }: StatusBadgeProps) {
  const colours = STATUS_TONES[tone]
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold whitespace-nowrap ring-1 ring-inset', colours.pill, className)}>
      {Icon ? (
        <Icon className="size-3.5 shrink-0" aria-hidden="true" />
      ) : (
        <span aria-hidden="true" className="relative flex size-2 shrink-0">
          {pulse && <span className={cn('absolute inline-flex size-full rounded-full opacity-70 motion-safe:animate-ping', colours.dot)} />}
          <span className={cn('relative inline-flex size-2 rounded-full', colours.dot)} />
        </span>
      )}
      {children}
    </span>
  )
}

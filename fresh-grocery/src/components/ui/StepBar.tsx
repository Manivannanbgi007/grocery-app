import { Check, type LucideIcon } from 'lucide-react'
import { cn } from '../../utils/cn'

/** One step of a StepBar. */
export interface Step {
  label: string
  icon: LucideIcon
}

interface StepBarProps {
  /** the list's name for screen readers, e.g. "Delivery progress" */
  label: string
  steps: Step[]
  /** the index of the step reached last; -1 = none yet */
  current: number
}

/**
 * Steps in a row, each a round icon joined to the one before by a line, filled up to the step reached last (e.g.
 * Accepted · Picked up · On the way · Delivered). Done steps show a tick, the current one has a ring. Fits a phone with
 * up to 5 short labels.
 */
export function StepBar({ label, steps, current }: StepBarProps) {
  return (
    <ol aria-label={label} className="flex">
      {steps.map(({ label: stepLabel, icon: Icon }, index) => {
        const reached = index <= current
        const isCurrent = index === current
        return (
          <li key={stepLabel} aria-current={isCurrent ? 'step' : undefined} className="relative flex flex-1 flex-col items-center gap-2 text-center">
            {/* The line from the step before, behind the circles */}
            {index > 0 && <span aria-hidden="true" className={cn('absolute top-4 right-1/2 h-0.5 w-full -translate-y-1/2', reached ? 'bg-brand' : 'bg-line')} />}
            <span
              aria-hidden="true"
              className={cn(
                'relative grid size-8 place-items-center rounded-full border-2',
                reached ? 'border-brand bg-brand text-white' : 'border-line bg-card text-muted',
                isCurrent && 'ring-4 ring-brand/25',
              )}
            >
              {reached && !isCurrent ? <Check className="size-4" strokeWidth={3} /> : <Icon className="size-4" />}
            </span>
            <span className={cn('text-xs leading-tight', isCurrent ? 'font-bold text-heading' : reached ? 'text-heading' : 'text-body')}>
              {stepLabel}
              {reached && !isCurrent && <span className="sr-only"> (done)</span>}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

import { BadgeCheck, Bike, Check, ClipboardList, House, Package, PackageCheck, Store, type LucideIcon } from 'lucide-react'
import type { OrderStatus, TimelineEntry } from '../../api/types'
import { cn } from '../../utils/cn'
import { formatShortDateTime } from '../../utils/format'
import { PROGRESS_STEPS, STEP_LABELS } from './orderStatus'

/** The picture of each step. */
const STEP_ICONS: Partial<Record<OrderStatus, LucideIcon>> = {
  PENDING: ClipboardList,
  CONFIRMED: BadgeCheck,
  ACCEPTED: Store,
  PACKED: Package,
  READY_FOR_PICKUP: PackageCheck,
  OUT_FOR_DELIVERY: Bike,
  DELIVERED: House,
}

/**
 * The colour of each step once it is reached: its circle, the soft ring and pulse of the current step, and the line to
 * the next step, which runs from this step's colour into the next one's (Tailwind needs the whole class names here).
 */
const GREEN_STEP = { circle: 'bg-brand text-white shadow-brand/30', ring: 'ring-brand/25', pulse: 'bg-brand/40', line: 'from-brand to-brand' }
const STEP_COLOURS: Partial<Record<OrderStatus, typeof GREEN_STEP>> = {
  PENDING: { circle: 'bg-new text-white shadow-new/30', ring: 'ring-new/25', pulse: 'bg-new/40', line: 'from-new to-sale' },
  CONFIRMED: { circle: 'bg-sale text-white shadow-sale/30', ring: 'ring-sale/25', pulse: 'bg-sale/40', line: 'from-sale to-violet' },
  ACCEPTED: { circle: 'bg-violet text-white shadow-violet/30', ring: 'ring-violet/25', pulse: 'bg-violet/40', line: 'from-violet to-accent' },
  // Yellow is light: a dark icon on it (ink stays dark in both themes)
  PACKED: { circle: 'bg-accent text-ink shadow-accent/30', ring: 'ring-accent/30', pulse: 'bg-accent/50', line: 'from-accent to-hot' },
  READY_FOR_PICKUP: { circle: 'bg-hot text-white shadow-hot/30', ring: 'ring-hot/25', pulse: 'bg-hot/40', line: 'from-hot to-brand' },
  OUT_FOR_DELIVERY: GREEN_STEP,
  DELIVERED: GREEN_STEP,
}

interface OrderProgressProps {
  status: OrderStatus
  /** the order's history: a reached step then shows when it happened */
  timeline?: TimelineEntry[]
  /** always a vertical list (for a narrow column) */
  vertical?: boolean
}

/**
 * Placed · Confirmed · Accepted · Packed · Ready for pickup · On the way · Delivered, up to the current step.
 * Each step has an icon and a line to the next; a reached step shows its own colour (STEP_COLOURS) with a tick and its
 * time (from the history), the current one has a ring, and the steps still to come are grey without a time.
 * Across the card from 768 px, a vertical list on phones (and always with `vertical`).
 * Not used for cancelled or rejected orders.
 *
 * The movement (motion-safe:, so "reduce motion" turns it off): the steps rise in one after the other, the coloured
 * line fills in from step to step over the grey track (animate-grow-up on a phone, animate-grow-x across), and the
 * current step has a soft ring that pulses while the order still has steps to go.
 */
export function OrderProgress({ status, timeline = [], vertical = false }: OrderProgressProps) {
  const current = PROGRESS_STEPS.indexOf(status)
  const across = !vertical

  return (
    <ol aria-label="Order progress" className={cn('grid', across && 'md:grid-cols-7')}>
      {PROGRESS_STEPS.map((step, index) => {
        const reached = index <= current
        const isCurrent = index === current
        const Icon = (reached && !isCurrent ? Check : STEP_ICONS[step]) ?? Check
        // The newest time of the step (a step can come twice, e.g. a delivery tried again)
        const at = reached ? timeline.findLast((entry) => entry.status === step)?.at : undefined
        const colours = STEP_COLOURS[step] ?? GREEN_STEP
        return (
          <li
            key={step}
            aria-current={isCurrent ? 'step' : undefined}
            className={cn('relative flex gap-3 pb-5 last:pb-0 motion-safe:animate-rise', across && 'md:flex-col md:items-center md:gap-2 md:pb-0 md:text-center')}
            style={{ animationDelay: `${index * 80}ms` }}
          >
            {/* The line to the next step: down on a phone, to the right across the card. A grey track; up to the
                current step a coloured line fills it in (it grows from the start of the track, one step after another,
                and its colour runs from this step's into the next one's: top to bottom on a phone, left to right across). */}
            {index < PROGRESS_STEPS.length - 1 && (
              <span
                aria-hidden="true"
                className={cn(
                  'absolute top-9 bottom-1 left-4 w-1 -translate-x-1/2 overflow-hidden rounded-full bg-line',
                  // 4 px thick, so the colours show; top-3.5 puts its middle on the middle of the 32 px circles
                  across && 'md:top-3.5 md:right-[calc(-50%+1.5rem)] md:bottom-auto md:left-[calc(50%+1.5rem)] md:h-1 md:w-auto md:translate-x-0',
                )}
              >
                {index < current && (
                  <span
                    className={cn(
                      'absolute inset-0 origin-top rounded-full bg-linear-to-b motion-safe:animate-grow-up',
                      colours.line,
                      across && 'md:origin-left md:bg-linear-to-r md:motion-safe:animate-grow-x',
                    )}
                    style={{ animationDelay: `${300 + index * 150}ms` }}
                  />
                )}
              </span>
            )}
            <span
              aria-hidden="true"
              className={cn(
                'relative grid size-8 shrink-0 place-items-center rounded-full transition-colors',
                reached ? cn('shadow-md', colours.circle) : 'border-2 border-line bg-card text-muted',
                isCurrent && cn('ring-4', colours.ring),
              )}
            >
              {/* The current step pulses softly while more steps are to come (not once the order is delivered) */}
              {isCurrent && index < PROGRESS_STEPS.length - 1 && <span className={cn('absolute inset-0 rounded-full motion-safe:animate-ping', colours.pulse)} />}
              <Icon className="relative size-4" />
            </span>
            <div className="min-w-0 pt-1 md:pt-0">
              <p className={cn('text-sm leading-snug', isCurrent ? 'font-bold text-heading' : reached ? 'text-heading' : 'text-body')}>{STEP_LABELS[step]}</p>
              {at && (
                <p className="text-xs">
                  <time dateTime={at}>{formatShortDateTime(at)}</time>
                </p>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

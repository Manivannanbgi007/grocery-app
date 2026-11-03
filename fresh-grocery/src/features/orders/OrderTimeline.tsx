import { ChevronDown } from 'lucide-react'
import type { ChangedBy, OrderStatus, TimelineEntry } from '../../api/types'
import { cn } from '../../utils/cn'
import { formatDateTime } from '../../utils/format'
import { CHANGED_BY_LABELS, isStopped, ORDER_STATUS_LABELS } from './orderStatus'

interface OrderTimelineProps {
  timeline: TimelineEntry[]
  /** the names of the steps; the vendor panel passes its own */
  statusLabels?: Record<OrderStatus, string>
  /** who did a step, from the reader's point of view ("you" is the customer on the shop, the store in the vendor panel) */
  changedByLabels?: Record<ChangedBy, string>
  /** the note shown under a step; the backend writes the notes for the customer, so the vendor panel passes its own */
  noteOf?: (entry: TimelineEntry) => string | null
  /**
   * folded: "Full history (7 updates)" that opens on a click, inside another card (the customer's order page, where
   * the progress already shows the steps); otherwise a card of its own with the title "History"
   */
  folded?: boolean
  /** folded only: open from the start (a stopped order, where the history says what happened) */
  defaultOpen?: boolean
}

/** Every step of the order so far, oldest first: when, what, and who did it. */
export function OrderTimeline({
  timeline,
  statusLabels = ORDER_STATUS_LABELS,
  changedByLabels = CHANGED_BY_LABELS,
  noteOf = (entry) => entry.note,
  folded = false,
  defaultOpen = false,
}: OrderTimelineProps) {
  const steps = (
    <ol className="space-y-5 border-l-2 border-line pl-6">
      {timeline.map((entry) => {
        const note = noteOf(entry)
        return (
          <li key={`${entry.status}-${entry.at}`} className="relative">
            <span aria-hidden="true" className={cn('absolute top-1.5 -left-[1.95rem] size-3 rounded-full ring-4 ring-card', isStopped(entry.status) ? 'bg-hot' : 'bg-brand')} />
            <p className="font-heading font-bold text-heading">{statusLabels[entry.status]}</p>
            {/* The note often says more ("Confirmed - pay cash on delivery"); one that only repeats the status is left out */}
            {note && note.toLowerCase() !== statusLabels[entry.status].toLowerCase() && <p className="text-sm text-heading">{note}</p>}
            <p className="text-xs">
              <time dateTime={entry.at}>{formatDateTime(entry.at)}</time> · by {changedByLabels[entry.changedBy]}
            </p>
          </li>
        )
      })}
    </ol>
  )

  if (folded) {
    return (
      <details open={defaultOpen} className="group mt-5 border-t border-line pt-4">
        <summary className="inline-flex cursor-pointer list-none items-center gap-2 font-heading font-bold text-heading hover:text-brand-text [&::-webkit-details-marker]:hidden">
          <ChevronDown className="size-4 transition-transform group-open:rotate-180" aria-hidden="true" />
          Full history
          <span className="font-normal text-body">
            ({timeline.length} {timeline.length === 1 ? 'update' : 'updates'})
          </span>
        </summary>
        <div className="mt-4 pl-1">{steps}</div>
      </details>
    )
  }

  return (
    <section aria-labelledby="order-timeline-title" className="rounded-2xl border border-line bg-card p-6">
      <h2 id="order-timeline-title" className="mb-4 text-xl">
        History
      </h2>
      {steps}
    </section>
  )
}

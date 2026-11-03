import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Fragment, useEffect } from 'react'
import { Link } from 'react-router'
import { useScrollRow } from '../../hooks/useScrollRow'
import { cn } from '../../utils/cn'
import type { BadgeTone } from '../ui/Badge'
import { STATUS_TONES } from '../ui/statusTones'

/** One tab of a list, e.g. "Waiting for approval" with the number of stores in it. */
export interface StatusTab {
  key: string
  label: string
  /** the list's address with this tab chosen */
  to: string
  /** work waiting in this tab (left out or 0 = no number shown) */
  count?: number
  /** a dot in the status's colour, the same as the status in the list (green for Delivered); left out = no dot */
  tone?: BadgeTone
  /** tabs of another group than the tab before get a thin line between them (orders on their way | finished ones) */
  group?: string
}

interface StatusTabsProps {
  /** the tabs' name for screen readers, e.g. "Store status" */
  label: string
  tabs: StatusTab[]
  /** the key of the tab shown now */
  active: string
}

/**
 * The tabs above a list, as one bar in a card; the chosen tab lives in the URL.
 * When the tabs do not fit (the 11 order statuses, or a phone), the bar scrolls sideways: a soft edge with a ‹ or ›
 * button shows that there is more on that side, and the chosen tab is scrolled into view.
 */
export function StatusTabs({ label, tabs, active }: StatusTabsProps) {
  const { rowRef, canPrev, canNext, scrollPrev, scrollNext } = useScrollRow<HTMLUListElement>(tabs.length)

  // The chosen tab in view, e.g. "Rejected" at the end of the bar on a phone
  useEffect(() => {
    const row = rowRef.current
    const current = row?.querySelector<HTMLElement>('[aria-current="page"]')
    if (!row || !current) return
    // Only the bar moves (scrollIntoView would scroll the whole page too)
    row.scrollLeft = current.offsetLeft - (row.clientWidth - current.offsetWidth) / 2
  }, [active, rowRef])

  return (
    <nav aria-label={label} className="relative mb-5 rounded-2xl border border-line bg-card">
      {/* relative: the tabs' offsetLeft (above) is measured from the bar */}
      <ul ref={rowRef} className="scrollbar-none relative flex items-center gap-1 overflow-x-auto p-1.5">
        {tabs.map((tab, index) => {
          const isActive = tab.key === active
          const count = tab.count ?? 0
          const newGroup = index > 0 && tab.group !== tabs[index - 1].group
          return (
            <Fragment key={tab.key}>
              {newGroup && <li aria-hidden="true" className="mx-0.5 h-6 w-px shrink-0 bg-line" />}
              <li className="shrink-0">
                <Link
                  to={tab.to}
                  aria-current={isActive ? 'page' : undefined}
                  aria-label={count > 0 ? `${tab.label}, ${count} waiting` : undefined}
                  className={cn(
                    'inline-flex h-10 items-center gap-1.5 rounded-xl px-3 font-heading text-sm font-bold whitespace-nowrap transition-colors duration-200',
                    // The chosen tab: the green of the menu's current page
                    isActive ? 'bg-linear-to-r from-brand to-brand-dark text-white shadow-md shadow-brand/30' : 'text-body hover:bg-surface hover:text-heading',
                  )}
                >
                  {tab.tone && <span aria-hidden="true" className={cn('size-2 shrink-0 rounded-full', isActive ? 'bg-white' : STATUS_TONES[tab.tone].dot)} />}
                  {tab.label}
                  {count > 0 && <span className={cn('min-w-5 rounded-full px-1.5 text-center text-xs leading-5', isActive ? 'bg-card text-brand-text' : 'bg-hot text-white')}>{count}</span>}
                </Link>
              </li>
            </Fragment>
          )
        })}
      </ul>
      {canPrev && <ScrollButton side="left" onClick={scrollPrev} />}
      {canNext && <ScrollButton side="right" onClick={scrollNext} />}
    </nav>
  )
}

/**
 * A soft edge over the cut-off tabs with a round ‹ or › button on it. For the mouse only: the keyboard (Tab) and touch
 * move the bar by themselves, so screen readers and Tab skip it.
 */
function ScrollButton({ side, onClick }: { side: 'left' | 'right'; onClick: () => void }) {
  const Icon = side === 'left' ? ChevronLeft : ChevronRight
  return (
    <div
      aria-hidden="true"
      className={cn(
        'pointer-events-none absolute inset-y-0 flex items-center from-card from-50% to-transparent px-1.5',
        side === 'left' ? 'left-0 rounded-l-2xl bg-linear-to-r pr-10' : 'right-0 rounded-r-2xl bg-linear-to-l pl-10',
      )}
    >
      <button
        type="button"
        tabIndex={-1}
        onClick={onClick}
        className="pointer-events-auto grid size-8 place-items-center rounded-full border border-line bg-card text-heading shadow-sm transition hover:border-brand hover:text-brand-text"
      >
        <Icon className="size-4" aria-hidden="true" />
      </button>
    </div>
  )
}

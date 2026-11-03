import type { Delivery } from '../../../api/types'
import { daysBefore, formatLongDay, indiaDay } from '../../../utils/dates'

/* The history page's days: which day a delivery belongs to, the day's title, and the time on the road. */

/** One day of the history with its deliveries, newest first. */
export interface HistoryDay {
  /** "2026-10-30" (India time) */
  day: string
  deliveries: Delivery[]
}

/** The day a finished delivery belongs to (India time): the day it was delivered, else the day it was created. */
export function historyDayOf(delivery: Pick<Delivery, 'deliveredAt' | 'createdAt'>): string {
  return indiaDay(delivery.deliveredAt ?? delivery.createdAt)
}

/** The deliveries (newest first) by day; the days in the order they first come up. */
export function groupByDay(deliveries: Delivery[]): HistoryDay[] {
  const days = new Map<string, Delivery[]>()
  for (const delivery of deliveries) {
    const day = historyDayOf(delivery)
    days.set(day, [...(days.get(day) ?? []), delivery])
  }
  return [...days].map(([day, list]) => ({ day, deliveries: list }))
}

/** "Today", "Yesterday", or the day itself, e.g. "Tuesday, 27 October". */
export function dayTitle(day: string, today: string = indiaDay()): string {
  if (day === today) return 'Today'
  if (day === daysBefore(today, 1)) return 'Yesterday'
  return formatLongDay(day)
}

/** How long the order was on the road, from the pick-up to the delivery: "12 min", "1 h 5 min"; null when unknown. */
export function roadTime(delivery: Pick<Delivery, 'pickedUpAt' | 'deliveredAt'>): string | null {
  if (!delivery.pickedUpAt || !delivery.deliveredAt) return null
  const minutes = Math.max(1, Math.round((Date.parse(delivery.deliveredAt) - Date.parse(delivery.pickedUpAt)) / 60_000))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}

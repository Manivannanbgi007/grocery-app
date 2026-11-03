import type { ProductUnit } from '../api/types'

const rupeesWithPaise = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2 })
const wholeRupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 0, maximumFractionDigits: 0 })

/**
 * Indian rupees with Indian digit grouping. Whole amounts have no paise:
 *   36 -> "₹36"   28.5 -> "₹28.50"   125000 -> "₹1,25,000"
 */
export function formatPrice(amount: number): string {
  return Number.isInteger(amount) ? wholeRupees.format(amount) : rupeesWithPaise.format(amount)
}

/** The pack size of a product: ("G", 500) -> "500 g", ("PIECE", 6) -> "6 pcs", ("DOZEN", 1) -> "1 dozen". */
export function formatUnit(unit: ProductUnit, unitQuantity: number): string {
  const quantity = Number(unitQuantity.toFixed(3))
  switch (unit) {
    case 'KG':
      return `${quantity} kg`
    case 'G':
      return `${quantity} g`
    case 'L':
      return `${quantity} L`
    case 'ML':
      return `${quantity} ml`
    case 'PIECE':
      return quantity === 1 ? '1 pc' : `${quantity} pcs`
    case 'PACK':
      return quantity === 1 ? '1 pack' : `${quantity} packs`
    case 'DOZEN':
      return `${quantity} dozen`
  }
}

const shortDate = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

/** "2026-10-26T10:15:00Z" -> "26 Oct 2026" */
export function formatDate(isoDate: string): string {
  return shortDate.format(new Date(isoDate))
}

// Order times are shown in India time (like the backend's emails), whatever the computer's time zone is
const dateTime = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' })

/** "2026-10-26T10:15:00Z" -> "26 Oct 2026, 3:45 pm" (India time) */
export function formatDateTime(isoDate: string): string {
  return dateTime.format(new Date(isoDate))
}

const indiaDate = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Asia/Kolkata' })

/** "2026-10-26T20:15:00Z" -> "27 Oct 2026": the day in India time, like formatDateTime (an order's day) */
export function formatIndiaDate(isoDate: string): string {
  return indiaDate.format(new Date(isoDate))
}

const dayMonthTime = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' })

/** "2026-10-26T10:15:00Z" -> "26 Oct, 3:45 pm" (India time): short, for the steps of one order where the year is clear */
export function formatShortDateTime(isoDate: string): string {
  return dayMonthTime.format(new Date(isoDate))
}

const timeOnly = new Intl.DateTimeFormat('en-IN', { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' })

/** "2026-10-26T10:15:00Z" -> "3:45 pm" (India time) */
export function formatTime(isoDate: string): string {
  return timeOnly.format(new Date(isoDate))
}

/**
 * Rupees in a few characters, for chart axes (Indian units: thousand K, lakh L):
 *   0 -> "₹0"   750 -> "₹750"   1500 -> "₹1.5K"   250000 -> "₹2.5L"
 */
export function formatCompactRupees(amount: number): string {
  const trim = (value: number) => String(Math.round(value * 10) / 10)
  if (amount >= 100_000) return `₹${trim(amount / 100_000)}L`
  if (amount >= 1000) return `₹${trim(amount / 1000)}K`
  return `₹${Math.round(amount)}`
}

/** A phone number for a tel: link: "1800-123-4567" -> "18001234567". */
export function phoneHref(phone: string): string {
  return `tel:${phone.replace(/[^\d+]/g, '')}`
}

/** "Priya Sharma" -> "PS": the letters in a round avatar (accounts have no photo). */
export function initialsOf(fullName: string): string {
  return fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join('')
}

/*
 * The shared look of the panels' list tables: the card around the table, the header row and the cells. Written once
 * here, so every list (deliveries, payments, stores, partners, notifications …) looks the same.
 */

/** The card around a table; it scrolls sideways on a narrow screen. */
export const TABLE_CARD = 'relative overflow-x-auto rounded-2xl border border-line bg-card'

/** The header row: small capitals on a light grey band. */
export const TABLE_HEAD = 'border-b border-line bg-surface font-heading text-xs tracking-wide text-body uppercase'

/** A cell of the header row and of the body. */
export const HEAD_CELL = 'px-4 py-3'
export const CELL = 'px-4 py-3.5 align-top'

/** The round icon at the start of a row (the order, the payment, the store …). */
export const ROW_ICON = 'grid size-9 shrink-0 place-items-center rounded-xl'

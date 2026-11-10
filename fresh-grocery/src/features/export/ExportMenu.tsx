import { ChevronDown, Download, FileSpreadsheet, FileText, Sheet, X, type LucideIcon } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { toast } from 'sonner'
import type { User } from '../../api/types'
import { buttonClasses } from '../../components/ui/buttonClasses'
import { Spinner } from '../../components/ui/Spinner'
import { MenuIcon } from '../../components/ui/ToneIcon'
import type { Tone } from '../../components/ui/tones'
import { useDismiss } from '../../hooks/useDismiss'
import { cn } from '../../utils/cn'
import { saveFile } from '../../utils/download'
import { ROLE_NAMES, useAuth } from '../auth/authContext'
import { MAX_EXPORT_ROWS } from './exportLimits'
import type { ExportFormat, ExportMeta, ExportSpec } from './exportTypes'
import { exportFileName } from './exportValues'
import { fetchAllRows } from './fetchAllRows'
import { toCsv } from './toCsv'

interface FormatChoice {
  format: ExportFormat
  name: string
  hint: string
  icon: LucideIcon
  tone: Tone
}

const FORMATS: FormatChoice[] = [
  { format: 'xlsx', name: 'Excel', hint: 'Numbers and dates ready to sort, filter and sum', icon: FileSpreadsheet, tone: 'green' },
  { format: 'csv', name: 'CSV', hint: 'Plain data for any spreadsheet or tool', icon: Sheet, tone: 'blue' },
  { format: 'pdf', name: 'PDF', hint: 'A report to read, share or print', icon: FileText, tone: 'pink' },
]

/** Why an export stopped without a message: the page was left (the export belongs to the page). */
const LEFT_PAGE = 'left-page'

/** The menu's width (w-80) plus the page's side margin: less room than this left of the button's right edge, and the menu opens to the right instead. */
const MENU_ROOM = 320 + 16

const count = (number: number) => number.toLocaleString('en-IN')

/** Who exports and when, written into the Excel and PDF files. */
function exportMeta(user: User | null, total: number, truncated: boolean): ExportMeta {
  return { exportedBy: user ? `${user.fullName} (${ROLE_NAMES[user.role]})` : 'Guest', exportedAt: new Date(), total, truncated }
}

/** The file in the chosen format. Excel and PDF load their library only now, the first time one is made. */
async function buildFile<T>(format: ExportFormat, spec: ExportSpec<T>, rows: T[], meta: ExportMeta): Promise<Blob> {
  if (format === 'csv') return new Blob([toCsv(spec.columns, rows)], { type: 'text/csv;charset=utf-8' })
  if (format === 'xlsx') return (await import('./toXlsx')).toXlsx(spec, rows, meta)
  return (await import('./toPdf')).toPdf(spec, rows, meta)
}

interface Running {
  format: ExportFormat
  loaded: number
  expected: number
}

interface ExportMenuProps<T> {
  spec: ExportSpec<T>
  /** the rows in the list now (its total with the filters): a list longer than a format's limit says so beforehand */
  total: number
  className?: string
}

/**
 * "Export" above a list: Excel, CSV or PDF of the WHOLE list with its current filters (not only the page on screen),
 * loaded page by page from the same API the table uses, so it shows exactly what this user may see. While it runs,
 * the button shows the progress and a Cancel button; leaving the page cancels it. The file is saved like a download.
 */
export function ExportMenu<T>({ spec, total, className }: ExportMenuProps<T>) {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  // The menu opens under the button towards the side with room: to the left (under its right edge) as a rule, to the
  // right when the button sits near the left edge of the screen (on a phone, where the controls wrap onto a new line)
  const [opensRight, setOpensRight] = useState(false)
  const [running, setRunning] = useState<Running | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const menuId = useId()

  useDismiss(containerRef, open, (reason) => {
    setOpen(false)
    if (reason === 'escape') buttonRef.current?.focus()
  })

  // The menu opens with its first choice focused, for the keyboard
  useEffect(() => {
    if (open) menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus()
  }, [open])

  // Leaving the page stops a running export
  useEffect(() => () => controllerRef.current?.abort(LEFT_PAGE), [])

  async function run(format: ExportFormat) {
    setOpen(false)
    const controller = new AbortController()
    controllerRef.current = controller
    const max = MAX_EXPORT_ROWS[format]
    const name = FORMATS.find((choice) => choice.format === format)!.name
    setRunning({ format, loaded: 0, expected: Math.min(total, max) })
    try {
      const { rows, total: all, truncated } = await fetchAllRows(spec, max, controller.signal, (loaded, expected) => setRunning({ format, loaded, expected }))
      if (rows.length === 0) {
        toast.info(`There are no ${spec.itemWords[1]} to export.`)
        return
      }
      const meta = exportMeta(user, all, truncated)
      const blob = await buildFile(format, spec, rows, meta)
      if (controller.signal.aborted) return
      saveFile(blob, exportFileName(spec.fileName, format, meta.exportedAt))
      const what = `${count(rows.length)} ${rows.length === 1 ? spec.itemWords[0] : spec.itemWords[1]}`
      if (truncated) toast.success(`Exported the first ${what} of ${count(all)} to ${name}. Narrow the filters to export the rest.`)
      else toast.success(`Exported ${what} to ${name}.`)
    } catch (error) {
      if (controller.signal.aborted) {
        if (controller.signal.reason !== LEFT_PAGE) toast.info('Export cancelled.')
      } else {
        toast.error(`Could not export the ${spec.itemWords[1]}. ${error instanceof Error ? error.message : ''}`.trim())
      }
    } finally {
      if (controllerRef.current === controller) {
        controllerRef.current = null
        setRunning(null)
      }
    }
  }

  // Arrow keys, Home and End move between the choices; Tab leaves the menu (and closes it)
  function handleMenuKeys(event: KeyboardEvent<HTMLDivElement>) {
    const items = [...(menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])]
    const index = items.indexOf(document.activeElement as HTMLElement)
    const moveTo = (next: number) => {
      event.preventDefault()
      items[(next + items.length) % items.length]?.focus()
    }
    if (event.key === 'ArrowDown') moveTo(index + 1)
    else if (event.key === 'ArrowUp') moveTo(index - 1)
    else if (event.key === 'Home') moveTo(0)
    else if (event.key === 'End') moveTo(items.length - 1)
    else if (event.key === 'Tab') setOpen(false)
  }

  if (running) {
    const done = running.expected > 0 && running.loaded >= running.expected
    const percent = running.expected > 0 ? Math.round((running.loaded / running.expected) * 100) : 0
    const name = FORMATS.find((choice) => choice.format === running.format)!.name
    return (
      <div className={cn('flex items-center gap-1.5', className)}>
        <span role="status" className="inline-flex h-8 items-center gap-2 rounded-lg bg-brand-light px-3 text-sm font-bold text-brand-text tabular-nums">
          <Spinner size="sm" />
          {done ? `Making the ${name} file…` : `Exporting to ${name}… ${percent}%`}
        </span>
        <button type="button" onClick={() => controllerRef.current?.abort()} className={buttonClasses('ghost', 'sm')} aria-label="Cancel the export">
          <X className="size-4" aria-hidden="true" />
          Cancel
        </button>
      </div>
    )
  }

  return (
    <div ref={containerRef} className={cn('relative', className)}>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => {
          if (!open) setOpensRight((buttonRef.current?.getBoundingClientRect().right ?? Infinity) < MENU_ROOM)
          setOpen(!open)
        }}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        className={buttonClasses('outline', 'sm')}
      >
        <Download className="size-4" aria-hidden="true" />
        Export
        <ChevronDown className={cn('size-4 transition-transform', open && 'rotate-180')} aria-hidden="true" />
      </button>

      {open && (
        <div
          ref={menuRef}
          id={menuId}
          role="menu"
          aria-label={`Export ${spec.title.toLowerCase()}`}
          onKeyDown={handleMenuKeys}
          className={cn(
            'absolute z-30 mt-2 w-80 max-w-[calc(100vw-2rem)] rounded-2xl border border-line bg-card p-1.5 text-left shadow-xl motion-safe:animate-message-in',
            opensRight ? 'left-0' : 'right-0',
          )}
        >
          <p className="px-2.5 pt-1.5 pb-2 text-xs">
            {/* A paged list: the whole list, not only the page on screen; a table loaded whole has no pages to mention */}
            All {count(total)} {total === 1 ? spec.itemWords[0] : spec.itemWords[1]}
            {spec.rows ? ' in this table.' : ' with the filters in use, not only this page.'}
          </p>
          {FORMATS.map(({ format, name, hint, icon, tone }) => {
            const limited = total > MAX_EXPORT_ROWS[format]
            return (
              <button
                key={format}
                type="button"
                role="menuitem"
                onClick={() => void run(format)}
                className="group flex w-full items-start gap-3 rounded-xl px-2.5 py-2 text-left outline-none hover:bg-surface focus-visible:bg-surface"
              >
                <MenuIcon icon={icon} tone={tone} />
                <span className="min-w-0">
                  <span className="block font-heading text-sm font-bold text-heading">{name}</span>
                  <span className="block text-xs">{hint}</span>
                  {limited && (
                    <span className="mt-0.5 block text-xs font-semibold text-warn-text">
                      The first {count(MAX_EXPORT_ROWS[format])} of {count(total)}: narrow the filters for the rest
                    </span>
                  )}
                </span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

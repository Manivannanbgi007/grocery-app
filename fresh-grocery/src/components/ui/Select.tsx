import { Check, ChevronDown, type LucideIcon } from 'lucide-react'
import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { useDismiss } from '../../hooks/useDismiss'
import { usePopoverPlacement } from '../../hooks/usePopoverPlacement'
import { cn } from '../../utils/cn'

/** One choice of a Select. */
export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

interface SelectProps {
  value: string
  onChange: (value: string) => void
  options: SelectOption[]
  /** shown while no option has the value, e.g. "Choose a zone" */
  placeholder?: string
  /** the id of the visible label; without one, give `aria-label` */
  labelledBy?: string
  'aria-label'?: string
  /** the ids of a hint or an error under the field */
  describedBy?: string
  invalid?: boolean
  disabled?: boolean
  /** md: forms (48 px, like TextField); sm: filters and toolbars (40 px) */
  size?: 'sm' | 'md'
  /** plain: no border of its own, inside another box (the shop's search bar) */
  variant?: 'box' | 'plain'
  /** a small icon before the choice, e.g. a clock */
  icon?: LucideIcon
  /** as wide as the longest option (like a native drop-down), so the field does not jump when the choice changes */
  fitOptions?: boolean
  /** on the outer box, e.g. a width */
  className?: string
}

/** Letters typed within this time count as one search: "gr" finds "Green Basket". */
const TYPE_AHEAD_MS = 600
/** How far PageUp and PageDown move. */
const PAGE_STEP = 10

/** The nearest option that can be chosen, from `index` on in the direction `step`; -1 when there is none. */
function enabledFrom(options: SelectOption[], index: number, step: 1 | -1): number {
  for (let i = index; i >= 0 && i < options.length; i += step) {
    if (!options[i].disabled) return i
  }
  return -1
}

/**
 * Our drop-down, instead of the browser's <select>: a button that shows the choice, and a list that opens under it (or
 * above it when there is no room). It works like a native drop-down with the mouse, a finger and the keyboard (the
 * WAI-ARIA "select-only combobox"): ↑ ↓ Home End PageUp PageDown move, Enter or Space chooses, Escape closes, Tab
 * chooses and moves on, and typing letters jumps to the first option that starts with them. The focus stays on the
 * button; `aria-activedescendant` tells screen readers which option is highlighted.
 */
export function Select({
  value,
  onChange,
  options,
  placeholder = 'Choose…',
  labelledBy,
  'aria-label': ariaLabel,
  describedBy,
  invalid = false,
  disabled = false,
  size = 'md',
  variant = 'box',
  icon: Icon,
  fitOptions = false,
  className,
}: SelectProps) {
  const id = useId()
  const listId = `${id}-list`
  const optionId = (index: number) => `${id}-option-${index}`
  const rootRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const typed = useRef({ text: '', at: 0 })
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const placement = usePopoverPlacement(rootRef, listRef, open)

  /** Closes the list; letters typed next start a new search. */
  function close() {
    setOpen(false)
    typed.current = { text: '', at: 0 }
  }
  useDismiss(rootRef, open, close)

  const selectedIndex = options.findIndex((option) => option.value === value)
  const selected = options[selectedIndex]
  const last = options.length - 1

  // The highlighted option stays in view: in the middle when the list opens, then just in view while the keyboard moves.
  // Only the list scrolls, never the page.
  const justOpened = useRef(false)
  useEffect(() => {
    const list = listRef.current
    const option = document.getElementById(`${id}-option-${active}`)
    if (!open || !list || !option) return
    if (justOpened.current) {
      justOpened.current = false
      list.scrollTop = option.offsetTop - (list.clientHeight - option.offsetHeight) / 2
    } else if (option.offsetTop < list.scrollTop) list.scrollTop = option.offsetTop
    else if (option.offsetTop + option.offsetHeight > list.scrollTop + list.clientHeight) list.scrollTop = option.offsetTop + option.offsetHeight - list.clientHeight
  }, [open, active, id])

  function openAt(index: number) {
    justOpened.current = true
    setActive(index >= 0 ? index : enabledFrom(options, 0, 1))
    setOpen(true)
  }

  function choose(index: number) {
    const option = options[index]
    if (!option || option.disabled) return
    if (option.value !== value) onChange(option.value)
    close()
  }

  /** The next option whose label starts with the letters typed so far; -1 when there is none. */
  function typeAhead(letter: string): number {
    const now = Date.now()
    const text = now - typed.current.at < TYPE_AHEAD_MS ? typed.current.text + letter : letter
    typed.current = { text, at: now }
    // The same letter again (p, p) moves on to the next option with it; more letters keep refining from the current one
    const start = text.length === 1 ? active + 1 : Math.max(active, 0)
    for (let i = 0; i < options.length; i++) {
      const index = (start + i) % options.length
      if (!options[index].disabled && options[index].label.toLowerCase().startsWith(text.toLowerCase())) return index
    }
    return -1
  }

  const isLetter = (event: KeyboardEvent) => event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey
  const typing = () => Date.now() - typed.current.at < TYPE_AHEAD_MS

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    const { key } = event
    if (!open) {
      if (key === 'ArrowDown' || key === 'ArrowUp' || key === 'Enter' || key === ' ') openAt(selectedIndex)
      else if (key === 'Home') openAt(enabledFrom(options, 0, 1))
      else if (key === 'End') openAt(enabledFrom(options, last, -1))
      else if (isLetter(event)) {
        const found = typeAhead(key)
        if (found >= 0) openAt(found)
        return
      } else return
      event.preventDefault() // no page scrolling, and no click on the button
      return
    }

    let next = -1
    if (key === 'ArrowDown') next = enabledFrom(options, active + 1, 1)
    else if (key === 'ArrowUp') next = enabledFrom(options, active - 1, -1)
    else if (key === 'Home') next = enabledFrom(options, 0, 1)
    else if (key === 'End') next = enabledFrom(options, last, -1)
    else if (key === 'PageDown') next = enabledFrom(options, Math.min(active + PAGE_STEP, last), -1)
    else if (key === 'PageUp') next = enabledFrom(options, Math.max(active - PAGE_STEP, 0), 1)
    else if (key === 'Enter' || (key === ' ' && !typing())) {
      event.preventDefault()
      choose(active)
      return
    } else if (key === 'Escape') {
      event.preventDefault()
      event.stopPropagation() // only this list closes, not a calendar around it
      close()
      return
    } else if (key === 'Tab') {
      choose(active) // and the focus moves on as usual
      return
    } else if (isLetter(event)) next = typeAhead(key)
    else return

    event.preventDefault()
    if (next >= 0) setActive(next)
  }

  return (
    <div ref={rootRef} className={cn('relative', className)}>
      <button
        type="button"
        role="combobox"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={open && active >= 0 ? optionId(active) : undefined}
        aria-labelledby={labelledBy}
        aria-label={labelledBy ? undefined : ariaLabel}
        aria-describedby={describedBy}
        aria-invalid={invalid || undefined}
        disabled={disabled}
        onClick={() => (open ? close() : openAt(selectedIndex))}
        onKeyDown={handleKeyDown}
        // Space would click the button when it is let go: the keydown above handles it
        onKeyUp={(event) => event.key === ' ' && event.preventDefault()}
        onBlur={close}
        className={cn(
          'flex w-full items-center gap-2 text-left text-heading outline-none transition-[border-color,box-shadow] disabled:cursor-not-allowed disabled:opacity-60',
          size === 'md' ? 'h-12 px-3.5' : 'h-10 px-3 text-sm',
          variant === 'box' && 'rounded-lg border bg-card hover:border-brand-soft focus-visible:border-brand focus-visible:ring-4 focus-visible:ring-brand/15',
          variant === 'box' && (invalid ? 'border-hot' : open ? 'border-brand ring-4 ring-brand/15' : 'border-line'),
          variant === 'plain' && 'h-full rounded-md font-heading text-sm font-bold hover:bg-surface focus-visible:bg-surface',
        )}
      >
        {Icon && <Icon className="size-4 shrink-0 text-body" aria-hidden="true" />}
        <span className="grid min-w-0 flex-1">
          {/* Every label in the same cell, hidden: the widest one sets the width. Drawn by CSS (content: attr), so the
              labels are not in the page's text a second time */}
          {fitOptions &&
            options.map((option) => (
              <span key={option.value} data-label={option.label} aria-hidden="true" className="invisible col-start-1 row-start-1 truncate before:content-[attr(data-label)]" />
            ))}
          <span className={cn('col-start-1 row-start-1 truncate', !selected && 'text-muted')}>{selected?.label ?? placeholder}</span>
        </span>
        <ChevronDown className={cn('size-4 shrink-0 text-body transition-transform duration-200', open && 'rotate-180')} aria-hidden="true" />
      </button>
      {open && (
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-labelledby={labelledBy}
          aria-label={labelledBy ? undefined : ariaLabel}
          // A click in the list (also on its scrollbar) keeps the focus on the button
          onMouseDown={(event) => event.preventDefault()}
          className={cn(
            'absolute z-50 max-h-72 w-max max-w-[calc(100vw-2rem)] min-w-full overflow-y-auto overscroll-contain rounded-xl border border-line bg-card p-1.5 shadow-xl shadow-heading/10',
            'transition-[opacity,scale] duration-150 starting:scale-95 starting:opacity-0 motion-reduce:transition-none',
            placement.up ? 'bottom-full mb-1.5 origin-bottom' : 'top-full mt-1.5 origin-top',
            placement.alignRight ? 'right-0' : 'left-0',
          )}
        >
          {options.map((option, index) => {
            const isSelected = index === selectedIndex
            return (
              <li
                key={option.value}
                id={optionId(index)}
                role="option"
                aria-selected={isSelected}
                aria-disabled={option.disabled || undefined}
                onClick={() => choose(index)}
                onPointerMove={() => !option.disabled && setActive(index)}
                className={cn(
                  'flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-heading',
                  index === active && 'bg-brand-light',
                  isSelected && 'font-bold text-brand-text',
                  option.disabled && 'cursor-not-allowed opacity-50',
                )}
              >
                <span className="flex-1">{option.label}</span>
                {isSelected && <Check className="size-4 shrink-0" aria-hidden="true" />}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

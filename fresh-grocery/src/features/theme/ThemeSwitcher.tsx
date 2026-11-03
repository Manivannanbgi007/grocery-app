import { Monitor, Moon, Sun, type LucideIcon } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { useDismiss } from '../../hooks/useDismiss'
import type { ThemeChoice } from './theme'

const CHOICES: { value: ThemeChoice; label: string; icon: LucideIcon }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'Same as device', icon: Monitor },
]

interface ThemeSwitcherProps {
  choice: ThemeChoice
  onChange: (choice: ThemeChoice) => void
}

/**
 * The tab in the middle of the right edge of every page. It opens a small panel with the themes as radio buttons
 * (the arrow keys move between them); Escape or a click outside closes it.
 */
export function ThemeSwitcher({ choice, onChange }: ThemeSwitcherProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()
  const current = CHOICES.find((option) => option.value === choice) ?? CHOICES[2]
  const CurrentIcon = current.icon

  useDismiss(containerRef, open, (reason) => {
    setOpen(false)
    if (reason === 'escape') buttonRef.current?.focus()
  })

  return (
    <div ref={containerRef} className="fixed top-1/2 right-0 z-40 -translate-y-1/2 print:hidden">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((isOpen) => !isOpen)}
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={`Theme: ${current.label}`}
        title="Change the theme"
        className="flex h-12 w-11 items-center justify-center rounded-l-2xl border border-r-0 border-line bg-card text-heading shadow-lg shadow-heading/10 transition-colors hover:text-brand-text"
      >
        <CurrentIcon className="size-5" aria-hidden="true" />
      </button>

      {open && (
        <div id={panelId} className="absolute top-1/2 right-full mr-3 w-60 -translate-y-1/2 rounded-2xl border border-line bg-card p-4 shadow-xl">
          <fieldset>
            <legend className="mb-3 font-heading font-bold text-heading">Theme</legend>
            <div className="grid gap-2">
              {CHOICES.map(({ value, label, icon: Icon }) => (
                <label
                  key={value}
                  className="flex cursor-pointer items-center gap-3 rounded-xl border border-line px-3 py-2.5 text-sm font-bold text-heading hover:border-brand-soft has-checked:border-brand has-checked:bg-brand-light has-checked:text-brand-text has-focus-visible:ring-2 has-focus-visible:ring-brand"
                >
                  <input type="radio" name="theme" value={value} checked={choice === value} onChange={() => onChange(value)} className="sr-only" />
                  <Icon className="size-5 shrink-0" aria-hidden="true" />
                  {label}
                </label>
              ))}
            </div>
          </fieldset>
        </div>
      )}
    </div>
  )
}

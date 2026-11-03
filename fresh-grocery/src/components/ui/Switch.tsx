import { cn } from '../../utils/cn'

interface SwitchProps {
  checked: boolean
  /** what is switched on, e.g. "Coupon SAVE10 on" (read out with "on" or "off") */
  label: string
  /** switches to the other state (the caller saves it and says so) */
  onToggle: () => void
  disabled?: boolean
}

/** A small on/off switch (role="switch"), e.g. for a coupon or a feature flag. */
export function Switch({ checked, label, onToggle, disabled = false }: SwitchProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={onToggle}
      disabled={disabled}
      className={cn('relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:opacity-60', checked ? 'bg-brand' : 'bg-muted')}
    >
      <span aria-hidden="true" className={cn('absolute top-1 size-4 rounded-full bg-white shadow transition-all', checked ? 'left-6' : 'left-1')} />
    </button>
  )
}

import { Minus, Plus } from 'lucide-react'
import { cn } from '../../utils/cn'

interface QuantityStepperProps {
  value: number
  onChange: (quantity: number) => void
  min?: number
  max: number
  /** e.g. while the new quantity is being saved */
  disabled?: boolean
  /** what is being counted, for screen readers: "Quantity of Tomato" */
  label: string
  className?: string
}

/** [ - 2 + ] : the buttons stop at min and max. */
export function QuantityStepper({ value, onChange, min = 1, max, disabled = false, label, className }: QuantityStepperProps) {
  const buttonClass = 'flex size-9 items-center justify-center text-heading hover:text-brand-text disabled:cursor-not-allowed disabled:opacity-40'
  return (
    <div role="group" aria-label={label} className={cn('inline-flex items-center rounded-md border border-brand-soft bg-card', className)}>
      <button type="button" className={buttonClass} onClick={() => onChange(value - 1)} disabled={disabled || value <= min} aria-label="Decrease quantity">
        <Minus className="size-4" aria-hidden="true" />
      </button>
      <span className="min-w-8 text-center font-heading font-bold text-heading" aria-live="polite">
        {value}
      </span>
      <button type="button" className={buttonClass} onClick={() => onChange(value + 1)} disabled={disabled || value >= max} aria-label="Increase quantity">
        <Plus className="size-4" aria-hidden="true" />
      </button>
    </div>
  )
}

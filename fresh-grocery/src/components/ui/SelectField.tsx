import { useId, type ReactNode } from 'react'
import { Select, type SelectOption } from './Select'

interface SelectFieldProps {
  label: string
  value: string
  /** gets the chosen option's value */
  onChange: (value: string) => void
  options: SelectOption[]
  /** shown while nothing is chosen, e.g. "Choose a zone" */
  placeholder?: string
  /** shown in red under the field; also marks the field invalid for screen readers */
  error?: string
  hint?: ReactNode
  disabled?: boolean
  className?: string
}

/** A labelled drop-down with its hint and error message, like TextField. */
export function SelectField({ label, error, hint, className, ...select }: SelectFieldProps) {
  const id = useId()
  const labelId = `${id}-label`
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={className}>
      <span id={labelId} className="mb-1.5 block font-heading text-sm font-bold text-heading">
        {label}
      </span>
      <Select labelledBy={labelId} describedBy={describedBy} invalid={Boolean(error)} {...select} />
      {hint && (
        <div id={hintId} className="mt-1 text-xs">
          {hint}
        </div>
      )}
      {error && (
        <p id={errorId} className="mt-1 text-sm text-hot">
          {error}
        </p>
      )}
    </div>
  )
}

import type { LucideIcon } from 'lucide-react'
import { useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** shown in red under the field; also marks the field invalid for screen readers */
  error?: string
  /** a tip under the field, e.g. the password rules */
  hint?: ReactNode
  /** something inside the box on the right, e.g. a "Show password" button */
  trailing?: ReactNode
  /** something inside the box on the left, e.g. "+91" */
  leading?: ReactNode
  /** a small icon inside the box on the left, e.g. an envelope for an email */
  icon?: LucideIcon
}

/** A labelled input with its hint and error message, wired up for screen readers (aria-invalid, aria-describedby). */
export function TextField({ label, error, hint, trailing, leading, icon: Icon, className, ...input }: TextFieldProps) {
  const id = useId()
  const hintId = `${id}-hint`
  const errorId = `${id}-error`
  const describedBy = [hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block font-heading text-sm font-bold text-heading">
        {label}
      </label>
      <div
        className={cn(
          'group flex h-12 items-center rounded-lg border bg-card transition-shadow focus-within:border-brand focus-within:ring-4 focus-within:ring-brand/15',
          error ? 'border-hot' : 'border-line',
        )}
      >
        {Icon && <Icon className="ml-3.5 size-5 shrink-0 text-body transition-colors group-focus-within:text-brand-text" aria-hidden="true" />}
        {leading && <span className="border-r border-line px-3 text-sm text-heading">{leading}</span>}
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn('h-full min-w-0 flex-1 bg-transparent px-4 text-heading outline-none', Icon && 'pl-2.5')}
          {...input}
        />
        {trailing}
      </div>
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

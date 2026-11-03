import { Star } from 'lucide-react'
import { useId } from 'react'
import { cn } from '../../utils/cn'

interface StarInputProps {
  /** the question, e.g. "How was the delivery?" */
  label: string
  /** 1 to 5, or null while nothing is chosen */
  value: number | null
  onChange: (stars: number) => void
  error?: string
}

const FIVE = [1, 2, 3, 4, 5]
const WORDS = ['', 'Poor', 'Fair', 'Good', 'Very good', 'Excellent']

/**
 * Choose 1 to 5 stars. Underneath it is a group of radio buttons ("4 stars"), so it works with the keyboard
 * (arrow keys) and screen readers; the stars are only the picture.
 */
export function StarInput({ label, value, onChange, error }: StarInputProps) {
  const name = useId()
  const errorId = `${name}-error`

  return (
    <fieldset aria-describedby={error ? errorId : undefined}>
      <legend className="mb-1.5 font-heading text-sm font-bold text-heading">{label}</legend>
      <div className="flex items-center gap-0.5">
        {FIVE.map((stars) => (
          <label key={stars} className="cursor-pointer rounded-md p-0.5 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand">
            <input
              type="radio"
              name={name}
              value={stars}
              checked={value === stars}
              onChange={() => onChange(stars)}
              aria-label={`${stars} ${stars === 1 ? 'star' : 'stars'}`}
              className="sr-only"
            />
            <Star className={cn('size-7', value !== null && stars <= value ? 'fill-accent text-accent' : 'text-muted')} aria-hidden="true" />
          </label>
        ))}
        {value !== null && <span className="ml-2 text-sm text-heading">{WORDS[value]}</span>}
      </div>
      {error && (
        <p id={errorId} className="mt-1 text-sm text-hot">
          {error}
        </p>
      )}
    </fieldset>
  )
}

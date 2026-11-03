import { X } from 'lucide-react'
import { useId, useState, type KeyboardEvent } from 'react'
import { Button } from '../ui/Button'

const PINCODE = /^[1-9]\d{5}$/

interface PincodeListEditorProps {
  pincodes: string[]
  onChange: (pincodes: string[]) => void
  /** the list's name for screen readers, e.g. "PIN codes you deliver to" */
  label: string
  /** at most this many codes (the backend allows 50) */
  max?: number
  /** "A store can deliver to at most ..." */
  maxMessage: string
}

/**
 * A list of PIN codes as chips, with a field to add one (Enter or "Add") and × to remove one. It is not a form of its
 * own, so it can sit inside another form; the caller saves the list.
 */
export function PincodeListEditor({ pincodes, onChange, label, max = 50, maxMessage }: PincodeListEditorProps) {
  const inputId = useId()
  const [newCode, setNewCode] = useState('')
  const [error, setError] = useState<string | null>(null)

  /** Why the code cannot be added, or null when it can. */
  function problemWith(code: string): string | null {
    if (!PINCODE.test(code)) return 'Enter a 6-digit PIN code.'
    if (pincodes.includes(code)) return `${code} is already in the list.`
    if (pincodes.length >= max) return maxMessage
    return null
  }

  function add() {
    const code = newCode.trim()
    const problem = problemWith(code)
    setError(problem)
    if (problem) return
    onChange([...pincodes, code].sort())
    setNewCode('')
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    // Enter adds the code (and does not send a form around the editor)
    if (event.key === 'Enter') {
      event.preventDefault()
      add()
    }
  }

  return (
    <div className="space-y-3">
      <ul aria-label={label} className="flex flex-wrap gap-2">
        {pincodes.map((code) => (
          <li key={code} className="flex items-center gap-1 rounded-full border border-brand-soft bg-brand-light py-1 pr-1 pl-3 text-sm font-bold text-brand-text">
            {code}
            <button type="button" onClick={() => onChange(pincodes.filter((other) => other !== code))} aria-label={`Remove ${code}`} className="rounded-full p-1 hover:bg-card">
              <X className="size-3.5" aria-hidden="true" />
            </button>
          </li>
        ))}
      </ul>
      <div className="flex max-w-xs gap-2">
        <label htmlFor={inputId} className="sr-only">
          PIN code to add
        </label>
        <input
          id={inputId}
          value={newCode}
          onChange={(event) => {
            setNewCode(event.target.value.replace(/\D/g, ''))
            setError(null)
          }}
          onKeyDown={handleKeyDown}
          inputMode="numeric"
          maxLength={6}
          placeholder="e.g. 560071"
          className="h-10 min-w-0 flex-1 rounded-lg border border-line bg-card px-3 text-heading outline-none focus:border-brand"
        />
        <Button type="button" variant="light" onClick={add}>
          Add
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-sm text-hot">
          {error}
        </p>
      )}
    </div>
  )
}

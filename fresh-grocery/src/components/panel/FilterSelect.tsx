import { Select } from '../ui/Select'

interface FilterSelectProps {
  /** for screen readers, e.g. "Store" */
  label: string
  /** what the empty choice says, e.g. "All stores" */
  allLabel: string
  value: string
  options: { value: string; label: string }[]
  onChange: (value: string) => void
}

/** A filter drop-down above a list ("All stores", "Fresh Mart", ...); the empty choice removes the filter. */
export function FilterSelect({ label, allLabel, value, options, onChange }: FilterSelectProps) {
  return <Select size="sm" aria-label={label} value={value} onChange={onChange} options={[{ value: '', label: allLabel }, ...options]} fitOptions className="max-w-full" />
}

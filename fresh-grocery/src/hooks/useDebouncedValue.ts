import { useEffect, useState } from 'react'

/**
 * `value`, but only once it has stopped changing for `delayMs`. While the user types, it keeps the last settled value,
 * so a search can wait until the typing pauses instead of running on every key press.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [settled, setSettled] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return settled
}

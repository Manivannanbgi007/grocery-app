import { useEffect, useState } from 'react'

/**
 * true once `active` has stayed true for `delayMs`, and false again as soon as `active` is false. For a loading sign that
 * should not flash on fast answers.
 */
export function useDelayedFlag(active: boolean, delayMs: number): boolean {
  const [waitedLongEnough, setWaitedLongEnough] = useState(false)

  useEffect(() => {
    if (!active) return
    const timer = setTimeout(() => setWaitedLongEnough(true), delayMs)
    return () => {
      clearTimeout(timer)
      setWaitedLongEnough(false)
    }
  }, [active, delayMs])

  return active && waitedLongEnough
}

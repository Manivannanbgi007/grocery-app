import { useEffect, useState } from 'react'

/** The current time, updated every `everyMs`: for texts like "updated 20 s ago" that must keep counting. */
export function useNow(everyMs = 5_000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), everyMs)
    return () => window.clearInterval(timer)
  }, [everyMs])
  return now
}

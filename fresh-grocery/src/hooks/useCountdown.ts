import { useEffect, useState } from 'react'
import { timeLeft, type TimeLeft } from '../utils/time'

/** The time left until `until` (an ISO date), updated every second. */
export function useCountdown(until: string): TimeLeft {
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  return timeLeft(until, now)
}

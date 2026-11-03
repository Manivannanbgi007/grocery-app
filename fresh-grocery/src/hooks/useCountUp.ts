import { useEffect, useRef, useState } from 'react'

/** How long a number takes to count up, in milliseconds. */
const DURATION = 900

/** True when the system allows animations ("reduce motion" is off). Tests have no matchMedia: no animation there. */
function motionAllowed(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: no-preference)').matches
}

/**
 * A number that counts up to `target` when it first shows (from 0), and from the old to the new value when it changes,
 * fast at first and slower at the end. Without animations it is simply `target`.
 *
 *   const shown = useCountUp(dashboard.ordersToday)   // 0, 1, 1.6 … 2
 */
export function useCountUp(target: number): number {
  const animate = motionAllowed()
  const [shown, setShown] = useState(0)
  /** the number on the screen right now, where the next count starts */
  const onScreen = useRef(0)

  useEffect(() => {
    if (!animate) return
    const from = onScreen.current
    // The count is timed by the frames' own clock, from the first frame on: a frame's time can be a little earlier than
    // performance.now() when the effect ran, and then the progress (and the number) would start below zero
    let start: number | null = null
    let frame = requestAnimationFrame(function step(now) {
      start ??= now
      const progress = Math.min(Math.max((now - start) / DURATION, 0), 1)
      const easeOut = 1 - (1 - progress) ** 3
      onScreen.current = from + (target - from) * easeOut
      setShown(onScreen.current)
      if (progress < 1) frame = requestAnimationFrame(step)
    })
    return () => cancelAnimationFrame(frame)
  }, [target, animate])

  return animate ? shown : target
}

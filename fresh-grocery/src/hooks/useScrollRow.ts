import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * For a row that scrolls sideways (featured categories, product carousels) with ← → buttons.
 * Put `rowRef` on the scrolling element; `canPrev` / `canNext` tell whether there is more to see on each side.
 *
 * @param itemCount the number of items in the row: when it changes (data arrived) the buttons are checked again
 */
export function useScrollRow<T extends HTMLElement>(itemCount: number) {
  const ref = useRef<T>(null)
  const [canPrev, setCanPrev] = useState(false)
  const [canNext, setCanNext] = useState(false)

  const update = useCallback(() => {
    const row = ref.current
    if (!row) return
    setCanPrev(row.scrollLeft > 0)
    setCanNext(row.scrollLeft + row.clientWidth < row.scrollWidth - 1)
  }, [])

  useEffect(() => {
    update()
    const row = ref.current
    row?.addEventListener('scroll', update, { passive: true })
    window.addEventListener('resize', update)
    // The row's own width changes too without a window resize, e.g. when a panel's side menu folds away
    // (the tests' jsdom has no ResizeObserver)
    const resizes = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(update)
    if (row) resizes?.observe(row)
    return () => {
      row?.removeEventListener('scroll', update)
      window.removeEventListener('resize', update)
      resizes?.disconnect()
    }
  }, [update, itemCount])

  /** Scrolls by most of the visible width, so the next items come into view. */
  function scrollBy(direction: 1 | -1) {
    const row = ref.current
    row?.scrollBy({ left: direction * row.clientWidth * 0.8, behavior: 'smooth' })
  }

  return { rowRef: ref, canPrev, canNext, scrollPrev: () => scrollBy(-1), scrollNext: () => scrollBy(1) }
}

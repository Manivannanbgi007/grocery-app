import { useLayoutEffect, useState, type RefObject } from 'react'

export interface PopoverPlacement {
  /** open above the field: there is not enough room under it */
  up: boolean
  /** line up with the field's right edge: on the left edge it would stick out of the screen */
  alignRight: boolean
}

/** Space kept between a popover and the edge of the screen. */
const MARGIN = 8

/**
 * Where a drop-down list or a calendar opens next to its field: under it (the default) or above it, left- or
 * right-aligned, so that it stays on the screen. Measured each time it opens, before it is drawn.
 */
export function usePopoverPlacement(anchorRef: RefObject<HTMLElement | null>, popoverRef: RefObject<HTMLElement | null>, open: boolean): PopoverPlacement {
  const [placement, setPlacement] = useState<PopoverPlacement>({ up: false, alignRight: false })

  useLayoutEffect(() => {
    const anchor = anchorRef.current
    const popover = popoverRef.current
    if (!open || !anchor || !popover) return
    const box = anchor.getBoundingClientRect()
    const roomBelow = window.innerHeight - box.bottom
    setPlacement({
      up: roomBelow < popover.offsetHeight + MARGIN && box.top > roomBelow,
      alignRight: box.left + popover.offsetWidth > window.innerWidth - MARGIN && box.right - popover.offsetWidth >= MARGIN,
    })
  }, [anchorRef, popoverRef, open])

  return placement
}

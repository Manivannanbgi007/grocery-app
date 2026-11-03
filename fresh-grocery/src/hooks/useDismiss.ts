import { useEffect, useEffectEvent, type RefObject } from 'react'

export type DismissReason = 'escape' | 'outside'

/**
 * While `open` is true, calls onDismiss when the user presses Escape or clicks/taps outside `ref`.
 * Used by dropdowns and drawers (the category menu, the mobile menu).
 */
export function useDismiss(ref: RefObject<HTMLElement | null>, open: boolean, onDismiss: (reason: DismissReason) => void) {
  // useEffectEvent: always calls the latest onDismiss without re-adding the listeners on every render
  const dismiss = useEffectEvent(onDismiss)

  useEffect(() => {
    if (!open) return

    function handlePointerDown(event: PointerEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) dismiss('outside')
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') dismiss('escape')
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [ref, open])
}

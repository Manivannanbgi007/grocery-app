import { useEffect } from 'react'
import { useLocation } from 'react-router'

/**
 * Links like "/#deals" jump to a section of the home page. The router tries that straight away, but the sections above
 * may still be loading and push the target down. This scrolls to the element with this id again once `ready` is true.
 */
export function useScrollToHash(id: string, ready: boolean) {
  const { hash } = useLocation()

  useEffect(() => {
    if (ready && hash === `#${id}`) {
      document.getElementById(id)?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
    }
  }, [id, ready, hash])
}

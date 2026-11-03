import { useEffect, useState } from 'react'

/** True while the page is scrolled down more than `pixels` (the back-to-top button, the header's shadow). */
export function useScrolledPast(pixels: number): boolean {
  const [scrolledPast, setScrolledPast] = useState(() => window.scrollY > pixels)

  useEffect(() => {
    function handleScroll() {
      setScrolledPast(window.scrollY > pixels)
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [pixels])

  return scrolledPast
}

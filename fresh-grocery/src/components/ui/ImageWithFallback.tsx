import { useState, type ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface ImageWithFallbackProps {
  src: string | null | undefined
  alt: string
  /** shown when there is no image, while it loads, and when it cannot be loaded */
  fallback: ReactNode
  className?: string
}

/**
 * An <img> that never shows a broken-image icon: the fallback stays until the image has really loaded.
 * (The demo catalog and the seed banners have no photos yet, so the fallback is what you usually see.)
 */
export function ImageWithFallback({ src, alt, fallback, className }: ImageWithFallbackProps) {
  // Remembering *which* address loaded or failed keeps this right when `src` changes
  const [loadedSrc, setLoadedSrc] = useState<string | null>(null)
  const [failedSrc, setFailedSrc] = useState<string | null>(null)

  if (!src || failedSrc === src) return <>{fallback}</>

  const loaded = loadedSrc === src
  return (
    <>
      {!loaded && fallback}
      <img src={src} alt={alt} onLoad={() => setLoadedSrc(src)} onError={() => setFailedSrc(src)} className={cn(className, !loaded && 'hidden')} />
    </>
  )
}

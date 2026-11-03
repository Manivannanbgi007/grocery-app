import { ChevronLeft, ChevronRight, X, ZoomIn } from 'lucide-react'
import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from 'react'
import { createPortal } from 'react-dom'
import type { Product } from '../../api/types'
import { ProductImage } from '../../components/product/ProductImage'
import { cn } from '../../utils/cn'
import { keepFocusInside } from '../../utils/focusTrap'

/**
 * The product's photos: a big one with a zoom button, and thumbnails under it when there are two or more.
 * Without a photo it shows the emoji tile (and no zoom: there is nothing to enlarge).
 */
export function ProductGallery({ product }: { product: Product }) {
  const [shown, setShown] = useState(0)
  const [zoomed, setZoomed] = useState(false)
  const zoomButtonRef = useRef<HTMLButtonElement>(null)
  const photoCount = product.imageUrls.length

  function closeZoom() {
    setZoomed(false)
    zoomButtonRef.current?.focus()
  }

  return (
    <div>
      <div className="relative rounded-2xl border border-line bg-card p-3 sm:p-4">
        {photoCount > 0 && (
          <button
            ref={zoomButtonRef}
            type="button"
            onClick={() => setZoomed(true)}
            aria-label="Enlarge the photo"
            className="absolute top-4 right-4 z-10 grid size-10 place-items-center rounded-full bg-accent text-ink shadow-md transition-transform hover:scale-105"
          >
            <ZoomIn className="size-5" aria-hidden="true" />
          </button>
        )}
        <ProductImage product={product} photoIndex={shown} emojiClassName="text-[10rem]" />
      </div>

      {photoCount > 1 && (
        <ul aria-label="Photos" className="mt-4 flex flex-wrap justify-center gap-3">
          {product.imageUrls.map((url, index) => (
            <li key={`${index}-${url}`}>
              <button
                type="button"
                onClick={() => setShown(index)}
                aria-label={`Show photo ${index + 1} of ${photoCount}`}
                aria-pressed={index === shown}
                className={cn('block size-20 rounded-xl border-2 bg-card p-1.5 transition-colors', index === shown ? 'border-brand' : 'border-line hover:border-brand-soft')}
              >
                <ProductImage product={product} photoIndex={index} emojiClassName="text-3xl" />
              </button>
            </li>
          ))}
        </ul>
      )}

      {zoomed && <PhotoDialog product={product} index={shown} onIndexChange={setShown} onClose={closeZoom} />}
    </div>
  )
}

interface PhotoDialogProps {
  product: Product
  index: number
  onIndexChange: (index: number) => void
  onClose: () => void
}

/** The enlarged photo over the page; ← → (or the arrow buttons) for the other photos, Escape closes. */
function PhotoDialog({ product, index, onIndexChange, onClose }: PhotoDialogProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const photoCount = product.imageUrls.length
  const show = (step: number) => onIndexChange((index + step + photoCount) % photoCount)

  useEffect(() => {
    closeRef.current?.focus()
  }, [])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') onClose()
    else if (event.key === 'ArrowLeft' && photoCount > 1) show(-1)
    else if (event.key === 'ArrowRight' && photoCount > 1) show(1)
    keepFocusInside(event, panelRef.current)
  }

  function handleBackgroundClick(event: MouseEvent<HTMLDivElement>) {
    if (event.target === event.currentTarget) onClose()
  }

  const arrow = 'absolute top-1/2 grid size-11 -translate-y-1/2 place-items-center rounded-full bg-card text-heading shadow-md hover:text-brand-text'

  return createPortal(
    <div onClick={handleBackgroundClick} className="fixed inset-0 z-50 flex items-center justify-center bg-ink/80 p-4">
      <div ref={panelRef} role="dialog" aria-modal="true" aria-label={`${product.name}, photo ${index + 1} of ${photoCount}`} onKeyDown={handleKeyDown} className="relative w-full max-w-3xl rounded-2xl bg-card p-6">
        <button ref={closeRef} type="button" onClick={onClose} aria-label="Close the photo" className="absolute top-3 right-3 z-10 rounded-md p-1.5 text-heading hover:bg-surface">
          <X className="size-6" aria-hidden="true" />
        </button>
        <ProductImage product={product} photoIndex={index} className="max-h-[75vh]" emojiClassName="text-[12rem]" />
        {photoCount > 1 && (
          <>
            <button type="button" onClick={() => show(-1)} aria-label="Previous photo" className={cn(arrow, 'left-3')}>
              <ChevronLeft className="size-6" aria-hidden="true" />
            </button>
            <button type="button" onClick={() => show(1)} aria-label="Next photo" className={cn(arrow, 'right-3')}>
              <ChevronRight className="size-6" aria-hidden="true" />
            </button>
          </>
        )}
      </div>
    </div>,
    document.body,
  )
}

import { ImagePlus, Trash2 } from 'lucide-react'
import { useId, useState, type ChangeEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Product } from '../../../api/types'
import { ImageWithFallback } from '../../../components/ui/ImageWithFallback'
import { buttonClasses } from '../../../components/ui/buttonClasses'
import { cn } from '../../../utils/cn'
import { useProductChanges } from './useVendorProducts'

/** product-service's limits: JPEG, PNG or WEBP, at most 2 MB, at most 5 per product. */
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_BYTES = 2 * 1024 * 1024
const MAX_IMAGES = 5

/** The product's photos: the first one is the one customers see on the cards. Add (upload) and remove. */
export function ProductImagesPanel({ product }: { product: Pick<Product, 'id' | 'name' | 'imageUrls'> }) {
  const inputId = useId()
  const { uploadImage, removeImage } = useProductChanges()
  const [error, setError] = useState<string | null>(null)
  const full = product.imageUrls.length >= MAX_IMAGES

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const image = event.target.files?.[0]
    event.target.value = '' // the same file can be chosen again
    if (!image) return
    const problem = !IMAGE_TYPES.includes(image.type)
      ? 'Choose a JPEG, PNG or WEBP image.'
      : image.size > MAX_BYTES
        ? 'The image is larger than 2 MB. Please choose a smaller one.'
        : null
    setError(problem)
    if (problem) return
    uploadImage.mutate(
      { productId: product.id, image },
      {
        onSuccess: () => toast.success('Photo added'),
        onError: (uploadError) => setError(isApiError(uploadError, 401) ? null : uploadError.message),
      },
    )
  }

  function remove(imageUrl: string) {
    removeImage.mutate(
      { productId: product.id, imageUrl },
      {
        onSuccess: () => toast.success('Photo removed'),
        onError: (removeError) => setError(isApiError(removeError, 401) ? null : removeError.message),
      },
    )
  }

  return (
    <section aria-labelledby="photos-title" className="rounded-2xl border border-line bg-card p-6">
      <h2 id="photos-title" className="mb-1 text-xl">
        Photos
      </h2>
      <p className="mb-4 text-sm">
        {product.imageUrls.length} of {MAX_IMAGES}. The first photo is shown on the product cards. JPEG, PNG or WEBP, at most 2 MB.
      </p>
      {error && (
        <p role="alert" className="mb-3 text-sm text-hot">
          {error}
        </p>
      )}
      {product.imageUrls.length > 0 && (
        <ul aria-label="Photos of the product" className="mb-4 grid grid-cols-3 gap-3">
          {product.imageUrls.map((url, index) => (
            <li key={url} className="relative aspect-square overflow-hidden rounded-xl border border-line bg-surface">
              <ImageWithFallback
                src={url}
                alt={`${product.name}, photo ${index + 1}`}
                fallback={
                  <span aria-hidden="true" className="flex size-full items-center justify-center text-2xl">
                    🖼️
                  </span>
                }
                className="size-full object-contain"
              />
              <button
                type="button"
                onClick={() => remove(url)}
                disabled={removeImage.isPending}
                aria-label={`Remove photo ${index + 1}`}
                className="absolute top-1.5 right-1.5 rounded-full bg-card/90 p-1.5 text-heading shadow hover:text-hot disabled:opacity-50"
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
      <label htmlFor={inputId} className={cn(buttonClasses('outline', 'sm'), (full || uploadImage.isPending) && 'pointer-events-none opacity-50')}>
        <ImagePlus className="size-4" aria-hidden="true" />
        {uploadImage.isPending ? 'Uploading…' : 'Add a photo'}
      </label>
      <input id={inputId} type="file" accept={IMAGE_TYPES.join(',')} onChange={handleFile} disabled={full || uploadImage.isPending} className="sr-only" />
    </section>
  )
}

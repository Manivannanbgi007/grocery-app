import { Camera } from 'lucide-react'
import { useId, useState, type ChangeEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Delivery } from '../../../api/types'
import { Skeleton } from '../../../components/ui/Skeleton'
import { canAddProof } from '../deliveryTexts'
import { useProofPhoto, useTaskActions } from './useTasks'

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_BYTES = 5 * 1024 * 1024

/**
 * The proof of delivery: a photo of the order at the door (optional), added on the way or after delivering. On a phone
 * "Add a photo" opens the camera. The photo is shown once it is saved.
 */
export function ProofPhoto({ delivery }: { delivery: Delivery }) {
  const inputId = useId()
  const { proof } = useTaskActions(delivery.id)
  const photo = useProofPhoto(delivery.proofPhotoUrl)
  const [error, setError] = useState<string | null>(null)
  const canAdd = canAddProof(delivery.status)

  if (!delivery.proofPhotoUrl && !canAdd) return null

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = '' // the same photo can be chosen again
    if (!file) return
    if (!ALLOWED_TYPES.includes(file.type)) {
      setError('Choose a JPEG, PNG or WEBP photo.')
      return
    }
    if (file.size > MAX_BYTES) {
      setError('The photo is bigger than 5 MB.')
      return
    }
    setError(null)
    proof.mutate(file, {
      onSuccess: () => toast.success('Photo saved'),
      onError: (uploadError) => {
        if (!isApiError(uploadError, 401)) setError(uploadError.message)
      },
    })
  }

  return (
    <section aria-labelledby={`${inputId}-title`} className="rounded-2xl border border-line bg-card p-5 shadow-sm sm:p-6">
      <h2 id={`${inputId}-title`} className="mb-2 text-lg">
        Proof of delivery
      </h2>
      {delivery.proofPhotoUrl &&
        (photo.data ? (
          <img src={photo.data} alt={`Proof of delivery of order #${delivery.orderId}`} className="mb-3 max-h-64 rounded-xl object-contain" />
        ) : photo.isError ? (
          <p className="mb-3 text-sm text-hot">The photo could not be loaded.</p>
        ) : (
          <Skeleton className="mb-3 h-40 w-56" />
        ))}
      {canAdd && (
        <>
          <p className="mb-3 text-sm">{delivery.proofPhotoUrl ? 'You can replace the photo.' : 'Optional: a photo of the order at the door.'}</p>
          <label htmlFor={inputId} className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-brand px-4 py-2 font-heading text-sm font-bold text-brand-text transition-colors hover:bg-brand-light has-[+input:focus-visible]:ring-2 has-[+input:focus-visible]:ring-brand">
            <Camera className="size-4" aria-hidden="true" />
            {proof.isPending ? 'Saving the photo…' : delivery.proofPhotoUrl ? 'Replace the photo' : 'Add a photo'}
          </label>
          <input id={inputId} type="file" accept={ALLOWED_TYPES.join(',')} capture="environment" onChange={handleFile} disabled={proof.isPending} className="sr-only" />
          {error && (
            <p role="alert" className="mt-2 text-sm text-hot">
              {error}
            </p>
          )}
        </>
      )}
    </section>
  )
}

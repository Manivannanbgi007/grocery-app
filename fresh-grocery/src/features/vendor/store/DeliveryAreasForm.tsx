import { useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import { PincodeListEditor } from '../../../components/panel/PincodeListEditor'
import { Button } from '../../../components/ui/Button'
import { useStoreChanges } from '../useVendorStore'

const MAX_AREAS = 50

/**
 * The PIN codes the store delivers to: customers with an address elsewhere cannot order from it.
 * Codes are added and removed here, then saved together (the backend replaces the whole list).
 */
export function DeliveryAreasForm({ pincodes }: { pincodes: string[] }) {
  const { deliveryAreas } = useStoreChanges()
  const [areas, setAreas] = useState(pincodes)
  const [error, setError] = useState<string | null>(null)
  const changed = areas.join() !== pincodes.join()

  function save() {
    if (areas.length === 0) {
      setError('Keep at least one PIN code: the store must deliver somewhere.')
      return
    }
    setError(null)
    deliveryAreas.mutate(areas, {
      onSuccess: () => toast.success('Delivery areas saved'),
      onError: (saveError) => setError(isApiError(saveError, 401) ? null : saveError.message),
    })
  }

  return (
    <div className="space-y-4">
      <PincodeListEditor
        pincodes={areas}
        onChange={(next) => {
          setAreas(next)
          setError(null)
        }}
        label="PIN codes you deliver to"
        max={MAX_AREAS}
        maxMessage={`A store can deliver to at most ${MAX_AREAS} PIN codes.`}
      />
      {error && (
        <p role="alert" className="text-sm text-hot">
          {error}
        </p>
      )}
      <Button onClick={save} loading={deliveryAreas.isPending} disabled={!changed}>
        Save delivery areas
      </Button>
    </div>
  )
}

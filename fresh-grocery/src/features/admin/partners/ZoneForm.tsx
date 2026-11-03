import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { ZoneRequest } from '../../../api/adminDelivery'
import type { DeliveryZone } from '../../../api/types'
import { PincodeListEditor } from '../../../components/panel/PincodeListEditor'
import { Button } from '../../../components/ui/Button'
import { TextField } from '../../../components/ui/TextField'
import { useZoneChanges } from './useAdminPartners'

interface ZoneValues {
  name: string
  city: string
  pincodes: string[]
  /** rupees, as typed */
  payout: string
  active: boolean
}

type ZoneErrors = Partial<Record<'name' | 'city' | 'pincodes' | 'payout', string>>

/** The ZoneRequest rules (ZoneRequest.java), checked before asking the server. */
function validateZone(values: ZoneValues): ZoneErrors {
  const errors: ZoneErrors = {}
  if (!values.name.trim()) errors.name = 'Enter a name, e.g. Indiranagar.'
  else if (values.name.trim().length > 100) errors.name = 'Use at most 100 characters.'
  if (!values.city.trim()) errors.city = 'Enter the city.'
  else if (values.city.trim().length > 60) errors.city = 'Use at most 60 characters.'
  if (values.pincodes.length === 0) errors.pincodes = 'Add at least one PIN code.'
  const payout = Number(values.payout)
  if (values.payout.trim() === '' || !Number.isFinite(payout) || payout < 0) errors.payout = 'Enter what a partner earns per delivery, e.g. 40.'
  else if (!/^\d+(\.\d{1,2})?$/.test(values.payout.trim())) errors.payout = 'Use at most two decimals.'
  return errors
}

interface ZoneFormProps {
  /** the zone to change; left out to add a new one */
  zone?: DeliveryZone
  onDone: () => void
}

/** Add or change a delivery zone: its name, city, PIN codes, a partner's payout per delivery, and whether it is in use. */
export function ZoneForm({ zone, onDone }: ZoneFormProps) {
  const { create, update } = useZoneChanges()
  const [values, setValues] = useState<ZoneValues>({
    name: zone?.name ?? '',
    city: zone?.city ?? 'Bengaluru',
    pincodes: zone?.pincodes ?? [],
    payout: zone ? String(zone.partnerPayout) : '40',
    active: zone?.active ?? true,
  })
  const [errors, setErrors] = useState<ZoneErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const saving = create.isPending || update.isPending

  function change<K extends keyof ZoneValues>(field: K, value: ZoneValues[K]) {
    setValues((current) => ({ ...current, [field]: value }))
    if (field !== 'active') setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateZone(values)
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length > 0) return

    const request: ZoneRequest = { name: values.name.trim(), city: values.city.trim(), pincodes: values.pincodes, partnerPayout: Number(values.payout), active: values.active }
    const callbacks = {
      onSuccess: () => {
        toast.success(zone ? `Zone ${request.name} saved` : `Zone ${request.name} added`)
        onDone()
      },
      onError: (error: Error) => {
        if (isApiError(error, 409)) setErrors({ name: error.message })
        else if (!isApiError(error, 401)) setFormError(error.message)
      },
    }
    if (zone) update.mutate({ zoneId: zone.id, request }, callbacks)
    else create.mutate(request, callbacks)
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-4">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="Zone name" value={values.name} onChange={(event) => change('name', event.target.value)} error={errors.name} />
        <TextField label="City" value={values.city} onChange={(event) => change('city', event.target.value)} error={errors.city} />
      </div>
      <div>
        <p className="mb-1.5 font-heading text-sm font-bold text-heading">PIN codes</p>
        <PincodeListEditor pincodes={values.pincodes} onChange={(pincodes) => change('pincodes', pincodes)} label="PIN codes of the zone" maxMessage="A zone has at most 50 PIN codes." />
        {errors.pincodes && <p className="mt-1 text-sm text-hot">{errors.pincodes}</p>}
        <p className="mt-1 text-xs">A delivery belongs to the zone that has the store's PIN code.</p>
      </div>
      <TextField
        label="Partner earns per delivery"
        leading="₹"
        inputMode="decimal"
        value={values.payout}
        onChange={(event) => change('payout', event.target.value)}
        error={errors.payout}
        className="max-w-xs"
      />
      <label className="flex items-center gap-2 text-sm text-heading">
        <input type="checkbox" checked={values.active} onChange={(event) => change('active', event.target.checked)} className="size-4 accent-brand" />
        In use (partners of the zone get deliveries)
      </label>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={saving}>
          {zone ? 'Save zone' : 'Add zone'}
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

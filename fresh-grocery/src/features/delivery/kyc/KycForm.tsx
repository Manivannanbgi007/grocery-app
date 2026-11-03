import { useId, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { DeliveryPartner, VehicleType } from '../../../api/types'
import { Button } from '../../../components/ui/Button'
import { SelectField } from '../../../components/ui/SelectField'
import { TextField } from '../../../components/ui/TextField'
import { cn } from '../../../utils/cn'
import { hasErrors, serverFieldErrors, type FieldErrors } from '../../auth/validation'
import { useSubmitKyc } from '../usePartner'
import { cleanVehicleNumber, KYC_FIELDS, kycValuesOf, toKycRequest, validateKyc, type KycValues } from './kycValidation'
import { KYC_ZONES } from './zones'

const VEHICLES: { type: VehicleType; label: string; emoji: string }[] = [
  { type: 'BICYCLE', label: 'Bicycle', emoji: '🚲' },
  { type: 'SCOOTER', label: 'Scooter', emoji: '🛵' },
  { type: 'MOTORBIKE', label: 'Motorbike', emoji: '🏍️' },
]

interface KycFormProps {
  /** the partner's current details (filled in again after a rejection) */
  partner: DeliveryPartner | undefined
  /** called after a successful send, e.g. to close the form */
  onSent?: () => void
}

/** The KYC details an admin checks: vehicle, its number, driving licence and the zone to deliver in. */
export function KycForm({ partner, onSent }: KycFormProps) {
  const submit = useSubmitKyc()
  const vehicleGroupId = useId()
  const [values, setValues] = useState<KycValues>(() => kycValuesOf(partner))
  const [errors, setErrors] = useState<FieldErrors<keyof KycValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const chosenZone = KYC_ZONES.find((zone) => String(zone.id) === values.zoneId)

  function change(field: keyof KycValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateKyc(values)
    setErrors(found)
    setFormError(null)
    if (hasErrors(found)) return

    submit.mutate(toKycRequest(values), {
      onSuccess: () => {
        toast.success('KYC sent. An admin will review it.')
        onSent?.()
      },
      onError: (error) => {
        const fieldErrors = isApiError(error, 400) ? serverFieldErrors(error, KYC_FIELDS) : {}
        if (hasErrors(fieldErrors)) setErrors(fieldErrors)
        else if (!isApiError(error, 401)) setFormError(error.message)
      },
    })
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5">
      {formError && (
        <p role="alert" className="rounded-lg border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading">
          {formError}
        </p>
      )}
      <fieldset aria-describedby={errors.vehicleType ? `${vehicleGroupId}-error` : undefined}>
        <legend className="mb-1.5 font-heading text-sm font-bold text-heading">Vehicle</legend>
        <div className="grid grid-cols-3 gap-2">
          {VEHICLES.map(({ type, label, emoji }) => (
            <label
              key={type}
              className={cn(
                // The radio itself is hidden: the card shows the keyboard focus instead
                'flex cursor-pointer flex-col items-center gap-1 rounded-xl border p-3 text-sm font-bold text-heading has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-brand',
                values.vehicleType === type ? 'border-brand bg-brand-light' : 'border-line bg-card',
              )}
            >
              <input type="radio" name={vehicleGroupId} value={type} checked={values.vehicleType === type} onChange={() => change('vehicleType', type)} className="sr-only" />
              <span className="text-2xl" aria-hidden="true">
                {emoji}
              </span>
              <span>{label}</span>
            </label>
          ))}
        </div>
        {errors.vehicleType && (
          <p id={`${vehicleGroupId}-error`} className="mt-1 text-sm text-hot">
            {errors.vehicleType}
          </p>
        )}
      </fieldset>
      {/* Side by side from lg: the joining card is wide there, and one field across all of it would be hard to read */}
      <div className="grid items-start gap-5 lg:grid-cols-3">
        <TextField
          label="Vehicle number"
          placeholder="KA01AB1234"
          autoCapitalize="characters"
          value={values.vehicleNumber}
          onChange={(event) => change('vehicleNumber', cleanVehicleNumber(event.target.value))}
          error={errors.vehicleNumber}
        />
        <TextField label="Driving licence number" value={values.drivingLicenseNumber} onChange={(event) => change('drivingLicenseNumber', event.target.value)} error={errors.drivingLicenseNumber} />
        <SelectField
          label="Zone"
          hint={chosenZone ? `PIN codes ${chosenZone.pincodes.join(', ')}: you get the deliveries of the stores there.` : 'You get the deliveries of the stores in this area.'}
          value={values.zoneId}
          onChange={(zoneId) => change('zoneId', zoneId)}
          placeholder="Choose a zone"
          options={KYC_ZONES.map((zone) => ({ value: String(zone.id), label: `${zone.name}, ${zone.city}` }))}
          error={errors.zoneId}
        />
      </div>
      <Button type="submit" size="lg" loading={submit.isPending} className="w-full sm:w-auto">
        Send for review
      </Button>
    </form>
  )
}

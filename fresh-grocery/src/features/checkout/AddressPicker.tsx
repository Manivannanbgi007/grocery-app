import { CircleAlert, CircleCheck, Plus } from 'lucide-react'
import { useState } from 'react'
import type { AddressRequest } from '../../api/customers'
import type { Address } from '../../api/types'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { addressLines, ADDRESS_LABELS } from '../../utils/address'
import { cn } from '../../utils/cn'
import { AddressForm } from '../account/AddressForm'
import { newAddressValues } from '../account/addressValidation'
import { useAddressChanges } from '../account/useAddresses'
import { useAuth } from '../auth/authContext'
import { StepNumber } from './StepNumber'

interface AddressPickerProps {
  addresses: Address[]
  selectedId: number | null
  onSelect: (addressId: number) => void
  /** e.g. "Fresh Mart does not deliver to PIN code 560034 ..." */
  error: string | null
}

/** "Delivery address": the saved addresses as choices, and a form to add a new one (which is then chosen). */
export function AddressPicker({ addresses, selectedId, onSelect, error }: AddressPickerProps) {
  const { user } = useAuth()
  const { add } = useAddressChanges()
  const [adding, setAdding] = useState(false)
  const showForm = adding || addresses.length === 0

  async function saveNewAddress(request: AddressRequest) {
    const created = await add.mutateAsync(request)
    setAdding(false)
    onSelect(created.id)
  }

  return (
    // The look: blue, the colour of delivery. The chosen address has a blue ring and a tick; the others lift a little
    // under the mouse.
    <fieldset className="space-y-4">
      <legend className="mb-4 flex items-center gap-3 font-heading text-xl font-bold text-heading">
        <StepNumber number={1} tone="blue" /> Delivery address
      </legend>

      {error && (
        <p role="alert" className="flex gap-2 rounded-2xl border border-hot/40 bg-hot/10 px-4 py-3 text-sm text-heading motion-safe:animate-shake">
          <CircleAlert className="mt-0.5 size-4 shrink-0 text-hot-text" aria-hidden="true" />
          <span>{error}</span>
        </p>
      )}

      {addresses.length > 0 && (
        <div className="grid gap-3 md:grid-cols-2">
          {addresses.map((address) => {
            const selected = address.id === selectedId
            return (
              <label
                key={address.id}
                className={cn(
                  'relative flex cursor-pointer gap-3 rounded-2xl border-2 p-4 transition duration-200 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-brand',
                  selected ? 'border-sale bg-sale-light/70 shadow-md ring-4 ring-sale/15' : 'border-line bg-card hover:-translate-y-0.5 hover:border-sale/60 hover:shadow-md',
                )}
              >
                {selected && <CircleCheck className="absolute top-3 right-3 size-5 text-sale-text motion-safe:animate-pop" aria-hidden="true" />}
                <input type="radio" name="delivery-address" checked={selected} onChange={() => onSelect(address.id)} className="mt-1 size-4 shrink-0 accent-sale" />
                <span className="min-w-0 text-sm">
                  <span className="mb-1 flex items-center gap-2">
                    <span className="font-heading text-base font-bold text-heading">{ADDRESS_LABELS[address.label]}</span>
                    {address.defaultAddress && <Badge tone="neutral">Default</Badge>}
                  </span>
                  <span className="block text-heading">
                    {address.contactName}, {address.contactPhone}
                  </span>
                  {addressLines(address).map((line) => (
                    <span key={line} className="block">
                      {line}
                    </span>
                  ))}
                </span>
              </label>
            )
          })}
        </div>
      )}

      {showForm ? (
        <div className="rounded-2xl border border-line bg-card p-5">
          <h3 className="mb-4 text-lg">{addresses.length === 0 ? 'Where should we deliver?' : 'New address'}</h3>
          <AddressForm
            initialValues={newAddressValues(user?.fullName, user?.phone)}
            submitLabel="Save and use this address"
            onSave={saveNewAddress}
            onCancel={addresses.length > 0 ? () => setAdding(false) : undefined}
            showDefaultOption={addresses.length > 0}
          />
        </div>
      ) : (
        <Button variant="outline" onClick={() => setAdding(true)}>
          <Plus className="size-4" aria-hidden="true" />
          Add a new address
        </Button>
      )}
    </fieldset>
  )
}

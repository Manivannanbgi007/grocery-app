import { Pencil, Plus, Star, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import type { AddressRequest } from '../../api/customers'
import type { Address } from '../../api/types'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { ErrorState } from '../../components/ui/ErrorState'
import { LoadingState } from '../../components/ui/LoadingState'
import { addressLines, ADDRESS_LABELS } from '../../utils/address'
import { useAuth } from '../auth/authContext'
import { RequireCustomer } from '../auth/RequireCustomer'
import { AccountLayout } from './AccountLayout'
import { AddressForm } from './AddressForm'
import { addressValuesOf, newAddressValues } from './addressValidation'
import { useAddressChanges, useAddresses } from './useAddresses'

/** customer-service keeps at most 10 addresses per customer. */
const MAX_ADDRESSES = 10

/** The address book: add, change, delete addresses and choose the default one (picked first at checkout). */
export function AddressBookPage() {
  return (
    <RequireCustomer>
      <AccountLayout
        title="My addresses"
        crumbs={[
          { label: 'My account', to: '/account' },
          { label: 'Addresses' },
        ]}
      >
        <AddressBook />
      </AccountLayout>
    </RequireCustomer>
  )
}

function AddressCard({ address, onEdit, onDelete }: { address: Address; onEdit: () => void; onDelete: () => void }) {
  const { makeDefault } = useAddressChanges()

  function chooseAsDefault() {
    makeDefault.mutate(address.id, {
      onSuccess: () => toast.success(`${ADDRESS_LABELS[address.label]} address is now your default`),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <div className="flex h-full flex-col gap-3 text-sm">
      <div className="flex items-center gap-2">
        <h2 className="text-lg">{ADDRESS_LABELS[address.label]}</h2>
        {address.defaultAddress && <Badge tone="neutral">Default</Badge>}
      </div>
      <div>
        <p className="font-bold text-heading">
          {address.contactName}, {address.contactPhone}
        </p>
        {addressLines(address).map((line) => (
          <p key={line}>{line}</p>
        ))}
      </div>
      <div className="mt-auto flex flex-wrap gap-2 pt-2">
        <Button variant="light" size="sm" onClick={onEdit}>
          <Pencil className="size-4" aria-hidden="true" />
          Edit
        </Button>
        {!address.defaultAddress && (
          <Button variant="ghost" size="sm" loading={makeDefault.isPending} onClick={chooseAsDefault}>
            <Star className="size-4" aria-hidden="true" />
            Make default
          </Button>
        )}
        <Button variant="ghost" size="sm" onClick={onDelete}>
          <Trash2 className="size-4" aria-hidden="true" />
          Delete
        </Button>
      </div>
    </div>
  )
}

function AddressBook() {
  const { user } = useAuth()
  const { data: addresses, isPending, isError, error, refetch } = useAddresses()
  const { add, update, remove } = useAddressChanges()
  /** the address being changed, 'new' while adding one, null otherwise */
  const [editing, setEditing] = useState<number | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Address | null>(null)

  if (isPending) return <LoadingState className="min-h-64" />
  if (isError) return <ErrorState title="Could not load your addresses" error={error} onRetry={() => void refetch()} />

  const full = addresses.length >= MAX_ADDRESSES

  async function saveNew(request: AddressRequest) {
    await add.mutateAsync(request)
    setEditing(null)
    toast.success('Address saved')
  }

  async function saveChanges(addressId: number, request: AddressRequest) {
    await update.mutateAsync({ addressId, request })
    setEditing(null)
    toast.success('Address updated')
  }

  function confirmDelete() {
    if (!deleting) return
    remove.mutate(deleting.id, {
      onSuccess: () => toast.success('Address deleted'),
      onError: (deleteError) => toast.error(deleteError.message),
      onSettled: () => setDeleting(null),
    })
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <p className="text-sm">
          {addresses.length} of {MAX_ADDRESSES} addresses saved. The default address is chosen first at checkout.
        </p>
        {editing !== 'new' && (
          <Button onClick={() => setEditing('new')} disabled={full} title={full ? `You can save at most ${MAX_ADDRESSES} addresses` : undefined}>
            <Plus className="size-4" aria-hidden="true" />
            Add a new address
          </Button>
        )}
      </div>

      {editing === 'new' && (
        <section aria-labelledby="new-address-title" className="rounded-2xl border-2 border-brand-soft bg-card p-6">
          <h2 id="new-address-title" className="mb-4 text-xl">
            New address
          </h2>
          <AddressForm
            initialValues={newAddressValues(user?.fullName, user?.phone)}
            submitLabel="Save address"
            onSave={saveNew}
            onCancel={() => setEditing(null)}
            showDefaultOption={addresses.length > 0}
          />
        </section>
      )}

      {addresses.length === 0 && editing !== 'new' ? (
        <EmptyState icon="🏠" title="No addresses yet" message="Add the address where your groceries should go; checkout will choose it for you." />
      ) : (
        <ul aria-label="Your addresses" className="grid gap-4 md:grid-cols-2">
          {addresses.map((address) => (
            <li key={address.id} className={editing === address.id ? 'rounded-2xl border-2 border-brand-soft bg-card p-6 md:col-span-2' : 'rounded-2xl border border-line bg-card p-6'}>
              {editing === address.id ? (
                <>
                  <h2 className="mb-4 text-xl">Change address</h2>
                  <AddressForm
                    initialValues={addressValuesOf(address)}
                    submitLabel="Save changes"
                    onSave={(request) => saveChanges(address.id, request)}
                    onCancel={() => setEditing(null)}
                    showDefaultOption={!address.defaultAddress}
                  />
                </>
              ) : (
                <AddressCard address={address} onEdit={() => setEditing(address.id)} onDelete={() => setDeleting(address)} />
              )}
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={deleting !== null}
        title="Delete this address?"
        message={deleting ? `${ADDRESS_LABELS[deleting.label]}: ${addressLines(deleting).join(', ')}` : ''}
        confirmLabel="Delete"
        loading={remove.isPending}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(null)}
      />
    </div>
  )
}

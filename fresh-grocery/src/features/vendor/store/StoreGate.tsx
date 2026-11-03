import { CircleCheck, Clock, OctagonX, Store as StoreIcon } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { refreshSession } from '../../../api/client'
import type { StoreDetails } from '../../../api/types'
import type { StoreDetailsRequest } from '../../../api/vendorStore'
import { Button } from '../../../components/ui/Button'
import { ErrorState } from '../../../components/ui/ErrorState'
import { LoadingState } from '../../../components/ui/LoadingState'
import { usePlatformSettings } from '../../../hooks/usePlatformSettings'
import { useAuth } from '../../auth/authContext'
import { useLogOutToLogin } from '../../auth/loginRedirect'
import { useMyStore, useStoreChanges } from '../useVendorStore'
import type { GateState } from './gateState'
import { StoreDetailsForm } from './StoreDetailsForm'
import { newStoreDetails, storeDetailsOf } from './storeValidation'

function GateCard({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <section className="mx-auto max-w-3xl rounded-2xl border border-line bg-card p-6 md:p-8">
      <div className="mb-4 flex items-center gap-3">
        {icon}
        <h1 className="text-2xl md:text-3xl">{title}</h1>
      </div>
      {children}
    </section>
  )
}

function StoreSummary({ store }: { store: StoreDetails }) {
  return (
    <div className="rounded-xl bg-surface p-4 text-sm">
      <p className="font-heading text-lg font-bold text-heading">{store.name}</p>
      <p>
        {[store.addressLine1, store.addressLine2, `${store.city} ${store.pincode}`].filter(Boolean).join(', ')} · {store.phone}
      </p>
    </div>
  )
}

/** "Register your store": the first thing a new vendor sees. */
function RegisterStore() {
  const { vendor } = useAuth()
  const { register } = useStoreChanges()

  async function save(request: StoreDetailsRequest) {
    await register.mutateAsync(request)
    toast.success('Store registered. It is now waiting for admin approval.')
  }

  return (
    <GateCard icon={<StoreIcon className="size-8 text-brand" aria-hidden="true" />} title="Register your store">
      <p className="mb-6">
        Tell customers who you are and where your store is. An admin checks new stores; once yours is approved you can add
        products and receive orders.
      </p>
      <StoreDetailsForm initialValues={newStoreDetails(vendor?.phone, vendor?.email)} submitLabel="Send for approval" onSave={save} />
    </GateCard>
  )
}

/** Waiting for approval, or rejected (with the reason): the details can be changed; a rejected store is then sent again. */
function StoreUnderReview({ store }: { store: StoreDetails }) {
  const { update } = useStoreChanges()
  const rejected = store.status === 'REJECTED'
  const [editing, setEditing] = useState(rejected)

  async function save(request: StoreDetailsRequest) {
    await update.mutateAsync(request)
    setEditing(false)
    toast.success(rejected ? 'Store sent again for approval' : 'Store details saved')
  }

  return (
    <GateCard
      icon={rejected ? <OctagonX className="size-8 text-hot" aria-hidden="true" /> : <Clock className="size-8 text-new" aria-hidden="true" />}
      title={rejected ? 'Your store was not approved' : 'Your store is waiting for approval'}
    >
      {rejected ? (
        <p className="mb-4 rounded-lg border border-hot/40 bg-hot/5 px-4 py-3 text-heading">
          Reason: {store.statusReason ?? 'no reason given'}. Change the details below and send the store again.
        </p>
      ) : (
        <p className="mb-4">
          An admin checks every new store. You get a notification and an email when yours is approved; then your panel
          opens here.
        </p>
      )}
      {editing ? (
        <StoreDetailsForm
          initialValues={storeDetailsOf(store)}
          submitLabel={rejected ? 'Send again for approval' : 'Save details'}
          onSave={save}
          onCancel={rejected ? undefined : () => setEditing(false)}
        />
      ) : (
        <div className="space-y-4">
          <StoreSummary store={store} />
          <Button variant="outline" onClick={() => setEditing(true)}>
            Change the details
          </Button>
        </div>
      )}
    </GateCard>
  )
}

function StoreDisabled({ store }: { store: StoreDetails }) {
  const { data: settings } = usePlatformSettings()
  return (
    <GateCard icon={<OctagonX className="size-8 text-hot" aria-hidden="true" />} title="Your store is disabled">
      <p className="mb-4">
        The platform disabled {store.name}
        {store.statusReason ? `: ${store.statusReason}` : ''}. Customers cannot see it or order from it.
      </p>
      {settings && (
        <p>
          Please contact support: {settings.supportPhone} or {settings.supportEmail}.
        </p>
      )}
    </GateCard>
  )
}

/**
 * Approved, but the login's token is from before the approval and has no store id yet. A new token (POST /auth/refresh)
 * is made from the current account, which has the store id once auth-service has linked it (StoreApproved, a moment
 * after the approval): then the panel opens by itself, nothing to click. If the store is not linked yet, or the server
 * cannot be reached, the vendor can try again, or log in again as before.
 */
function OpenApprovedStore({ store }: { store: StoreDetails }) {
  const logOutToLogin = useLogOutToLogin()
  const [state, setState] = useState<'opening' | 'not-linked-yet' | 'failed'>('opening')

  // Once, when the card appears
  useEffect(() => {
    void openPanel().then((result) => result && setState(result))
  }, [])

  function tryAgain() {
    setState('opening')
    void openPanel().then((result) => result && setState(result))
  }

  function logInAgain() {
    // After the login the vendor login opens the panel's start page (/vendor) by itself: no ?next= needed
    void logOutToLogin('/vendor/login')
  }

  if (state === 'opening') {
    return (
      <GateCard icon={<CircleCheck className="size-8 text-brand" aria-hidden="true" />} title={`${store.name} is approved!`}>
        <LoadingState label="Opening your vendor panel…" className="min-h-24" />
      </GateCard>
    )
  }
  return (
    <GateCard icon={<CircleCheck className="size-8 text-brand" aria-hidden="true" />} title={`${store.name} is approved!`}>
      <p className="mb-6">
        {state === 'failed'
          ? 'Your vendor panel could not be opened: the server cannot be reached. Please try again in a moment.'
          : 'Your account is being linked to the store, which takes a few seconds after the approval. Please try again in a moment.'}
      </p>
      <div className="flex flex-wrap gap-3">
        <Button onClick={tryAgain}>Try again</Button>
        <Button variant="outline" onClick={logInAgain}>
          Log in again
        </Button>
      </div>
    </GateCard>
  )
}

/**
 * Gets a new token. With the store id in it the gate shows the panel and the card is gone: null. Otherwise what the
 * card shows. (A login that has ended is null too: the page then sends the vendor to the login.)
 */
async function openPanel(): Promise<'not-linked-yet' | 'failed' | null> {
  try {
    const session = await refreshSession()
    return session && !session.user.storeId ? 'not-linked-yet' : null
  } catch {
    return 'failed'
  }
}

/** What the vendor panel shows instead of its pages while the store is not ready (every state except 'ready'). */
export function StoreGate({ state }: { state: Exclude<GateState, 'ready'> }) {
  const query = useMyStore()

  if (state === 'loading') return <LoadingState className="mx-auto min-h-96 max-w-3xl" />
  if (state === 'error') return <ErrorState title="Could not load your store" error={query.error} onRetry={() => void query.refetch()} />
  if (state === 'register') return <RegisterStore />
  const store = query.data!
  if (state === 'pending' || state === 'rejected') return <StoreUnderReview store={store} />
  if (state === 'disabled') return <StoreDisabled store={store} />
  return <OpenApprovedStore store={store} />
}

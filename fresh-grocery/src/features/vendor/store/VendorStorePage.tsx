import type { ReactNode } from 'react'
import { toast } from 'sonner'
import type { StoreDetailsRequest } from '../../../api/vendorStore'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Badge } from '../../../components/ui/Badge'
import { LoadingState } from '../../../components/ui/LoadingState'
import { formatDate } from '../../../utils/format'
import { AcceptingOrdersSwitch } from '../layout/AcceptingOrdersSwitch'
import { useMyStore, useStoreChanges } from '../useVendorStore'
import { DeliveryAreasForm } from './DeliveryAreasForm'
import { OpeningHoursForm } from './OpeningHoursForm'
import { StoreDetailsForm } from './StoreDetailsForm'
import { storeDetailsOf } from './storeValidation'

function Section({ id, title, intro, children }: { id: string; title: string; intro?: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="rounded-2xl border border-line bg-card p-6">
      <h2 id={id} className="text-xl">
        {title}
      </h2>
      {intro && <p className="mt-1 text-sm">{intro}</p>}
      <div className="mt-4">{children}</div>
    </section>
  )
}

/** Store settings: the details customers see, when the store is open, where it delivers, and new-order intake. */
export function VendorStorePage() {
  const { data: store } = useMyStore()
  const { update } = useStoreChanges()

  if (!store) return <LoadingState className="min-h-96" />

  async function saveDetails(request: StoreDetailsRequest) {
    await update.mutateAsync(request)
    toast.success('Store details saved')
  }

  return (
    <>
      <PageTitle title="Store settings" />
      <div className="mb-6 flex flex-wrap items-center gap-x-4 gap-y-2">
        <h1 className="text-3xl">Store settings</h1>
        <Badge tone={store.openNow ? 'brand' : 'neutral'} className="text-sm">
          {store.openNow ? 'Open now' : 'Closed now'}
        </Badge>
        <p className="w-full text-sm">
          {store.name} is approved{store.approvedAt ? ` since ${formatDate(store.approvedAt)}` : ''}. The platform keeps {store.commissionPercentage}% of each order.
        </p>
      </div>
      <div className="grid items-start gap-6 xl:grid-cols-2">
        <div className="space-y-6">
          <Section id="intake-title" title="New orders" intro="Busy? Pause new orders; orders already placed stay as they are.">
            <AcceptingOrdersSwitch store={store} />
          </Section>
          <Section id="details-title" title="Store details" intro="Customers see the name, address and phone on the products and orders.">
            <StoreDetailsForm initialValues={storeDetailsOf(store)} submitLabel="Save details" onSave={saveDetails} />
          </Section>
        </div>
        <div className="space-y-6">
          <Section id="hours-title" title="Opening hours" intro="Customers see the hours on your store's products.">
            <OpeningHoursForm openingHours={store.openingHours} />
          </Section>
          <Section id="areas-title" title="Delivery areas" intro="Customers whose address has another PIN code cannot order from you.">
            <DeliveryAreasForm pincodes={store.deliveryPincodes} />
          </Section>
        </div>
      </div>
    </>
  )
}

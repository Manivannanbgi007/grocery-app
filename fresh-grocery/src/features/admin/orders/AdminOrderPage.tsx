import { ArrowLeft, Bike, FileText, MapPin, Phone, Store, X, type LucideIcon } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Order } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Badge } from '../../../components/ui/Badge'
import { Button, ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { ReasonDialog } from '../../../components/ui/ReasonDialog'
import { LoadingState } from '../../../components/ui/LoadingState'
import { addressLines } from '../../../utils/address'
import { cn } from '../../../utils/cn'
import { formatDateTime, phoneHref } from '../../../utils/format'
import { OrderAmounts } from '../../orders/OrderAmounts'
import { OrderItemsList } from '../../orders/OrderItemsList'
import { OrderProgress } from '../../orders/OrderProgress'
import { hasInvoice, isStopped, ORDER_STATUS_LABELS, ORDER_STATUS_TONES, staffTimelineNote } from '../../orders/orderStatus'
import { OrderTimeline } from '../../orders/OrderTimeline'
import { ADMIN_CHANGED_BY_LABELS, adminPaymentLine, adminStatusMessage } from './adminOrderStatus'
import { useAdminOrder, useCancelAsAdmin, useDownloadOrderInvoice } from './useAdminOrders'

const CANCEL_REASONS = ['The customer asked support to cancel', 'The store cannot deliver it', 'Suspected fraud']

/** A small box in the side column: customer, store, delivery partner. */
function InfoCard({ title, icon: Icon, children }: { title: string; icon: LucideIcon; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-line bg-card p-6 text-sm">
      <h2 className="mb-2 flex items-center gap-2 text-lg">
        <Icon className="size-5 text-brand" aria-hidden="true" />
        {title}
      </h2>
      {children}
    </section>
  )
}

function CallLink({ phone, label }: { phone: string; label: string }) {
  return (
    <a href={phoneHref(phone)} className="mt-2 inline-flex items-center gap-1.5 font-bold text-brand-text hover:underline">
      <Phone className="size-4" aria-hidden="true" />
      {label}: {phone}
    </a>
  )
}

/** "Cancel order": a problem order, while it is still with the store. The customer and the store see the reason. */
function CancelButton({ order }: { order: Order }) {
  const cancel = useCancelAsAdmin(order.id)
  const [open, setOpen] = useState(false)

  function confirm(reason: string) {
    cancel.mutate(reason, {
      onSuccess: () => {
        setOpen(false)
        toast.success(`Order #${order.id} cancelled`)
      },
      onError: (error) => {
        setOpen(false)
        if (!isApiError(error, 401)) toast.error(error.message)
      },
    })
  }

  return (
    <>
      <Button variant="danger-outline" onClick={() => setOpen(true)}>
        <X className="size-4" aria-hidden="true" />
        Cancel order
      </Button>
      <ReasonDialog
        open={open}
        title={`Cancel order #${order.id}?`}
        message={`The customer and ${order.storeName} are told the reason, the items go back into stock${order.paymentStatus === 'PAID' ? ', and the online payment is refunded' : ''}.`}
        commonReasons={CANCEL_REASONS}
        requiredMessage="Please give the reason: the customer and the store see it."
        confirmLabel="Cancel order"
        loading={cancel.isPending}
        onConfirm={confirm}
        onCancel={() => setOpen(false)}
      />
    </>
  )
}

function InvoiceButton({ orderId }: { orderId: number }) {
  const download = useDownloadOrderInvoice(orderId)
  return (
    <Button
      variant="outline"
      loading={download.isPending}
      onClick={() =>
        download.mutate(undefined, {
          onError: (error) => {
            if (!isApiError(error, 401)) toast.error(error.message)
          },
        })
      }
    >
      <FileText className="size-4" aria-hidden="true" />
      Invoice
    </Button>
  )
}

function OrderDetails({ order }: { order: Order }) {
  const message = adminStatusMessage(order)
  const stopped = isStopped(order.status)
  const address = order.deliveryAddress

  return (
    <>
      <div className="mb-6 flex flex-wrap items-center gap-x-3 gap-y-2">
        <h1 className="text-3xl">Order #{order.id}</h1>
        <Badge tone={ORDER_STATUS_TONES[order.status]}>{ORDER_STATUS_LABELS[order.status]}</Badge>
        <p className="w-full text-sm">
          Placed on {formatDateTime(order.placedAt)} at{' '}
          <Link to={`/admin/stores/${order.storeId}`} className="font-bold text-brand-text hover:underline">
            {order.storeName}
          </Link>
        </p>
        {(order.cancellable || hasInvoice(order.status)) && (
          <div className="mt-2 flex w-full flex-wrap gap-3">
            {hasInvoice(order.status) && <InvoiceButton orderId={order.id} />}
            {order.cancellable && <CancelButton order={order} />}
          </div>
        )}
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-6">
          <section aria-labelledby="order-status-title" className={cn('rounded-2xl border p-6', stopped ? 'border-hot/40 bg-hot/5' : 'border-line bg-card')}>
            <h2 id="order-status-title" className="text-xl">
              {message.title}
            </h2>
            <p className="mt-1 mb-5 text-heading">{message.text}</p>
            {!stopped && <OrderProgress status={order.status} timeline={order.timeline} />}
          </section>
          <OrderItemsList items={order.items} />
          <OrderTimeline timeline={order.timeline} changedByLabels={ADMIN_CHANGED_BY_LABELS} noteOf={staffTimelineNote} />
        </div>

        <aside className="space-y-4">
          <OrderAmounts order={order} paymentLine={adminPaymentLine(order)} />
          <InfoCard title="Customer" icon={MapPin}>
            <p className="font-bold text-heading">{address.contactName}</p>
            {addressLines(address).map((line) => (
              <p key={line}>{line}</p>
            ))}
            {order.customerNote && <p className="mt-3 rounded-lg bg-surface px-3 py-2">Note: “{order.customerNote}”</p>}
            <CallLink phone={address.contactPhone} label={`Call ${address.contactName.split(' ')[0]}`} />
            <p className="mt-3 flex flex-wrap gap-x-4 gap-y-1">
              <Link to={`/admin/accounts/${order.customerUserId}`} className="font-bold text-brand-text hover:underline">
                Account #{order.customerUserId}
              </Link>
              <Link to={`/admin/orders?customer=${order.customerUserId}`} className="font-bold text-brand-text hover:underline">
                All their orders
              </Link>
            </p>
          </InfoCard>
          <InfoCard title={order.storeName} icon={Store}>
            <p>{order.storeAddress}</p>
            <CallLink phone={order.storePhone} label="Call the store" />
          </InfoCard>
          {order.deliveryPartnerName && (
            <InfoCard title="Delivery partner" icon={Bike}>
              <p className="font-bold text-heading">{order.deliveryPartnerName}</p>
              {order.deliveryPartnerPhone && <CallLink phone={order.deliveryPartnerPhone} label={`Call ${order.deliveryPartnerName.split(' ')[0]}`} />}
            </InfoCard>
          )}
        </aside>
      </div>
    </>
  )
}

/** Everything about one order, with "Cancel order" for a problem order and the invoice. /admin/orders/:orderId */
export function AdminOrderPage() {
  const orderId = Number(useParams().orderId)
  const { data: order, isPending, isError, error, refetch } = useAdminOrder(orderId)

  let content
  if (isPending) content = <LoadingState className="min-h-96" />
  else if (isError && isApiError(error, 404)) {
    content = <EmptyState icon="📦" title="Order not found" message={`There is no order #${orderId}.`} action={<ButtonLink to="/admin/orders">All orders</ButtonLink>} className="rounded-2xl bg-card" />
  } else if (isError) content = <ErrorState title="Could not load the order" error={error} onRetry={() => void refetch()} />
  else content = <OrderDetails order={order} />

  return (
    <>
      <PageTitle title={`Order #${orderId}`} />
      <Link to="/admin/orders" className="mb-3 inline-flex items-center gap-1 text-sm font-bold text-brand-text hover:underline">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All orders
      </Link>
      {content}
    </>
  )
}

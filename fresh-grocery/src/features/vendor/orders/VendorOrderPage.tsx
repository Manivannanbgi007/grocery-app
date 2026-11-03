import { ArrowLeft, Bike, FileText, MapPin, Phone, Wallet, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { isApiError } from '../../../api/client'
import type { Order } from '../../../api/types'
import { PageTitle } from '../../../components/layout/PageTitle'
import { Badge } from '../../../components/ui/Badge'
import { Button } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { addressLines } from '../../../utils/address'
import { cn } from '../../../utils/cn'
import { formatDateTime, formatPrice, formatUnit, phoneHref } from '../../../utils/format'
import { OrderTimeline } from '../../orders/OrderTimeline'
import { hasInvoice, isStopped, staffTimelineNote } from '../../orders/orderStatus'
import { RejectOrderButton } from './RejectOrderButton'
import { StepButton } from './StepButton'
import { useDownloadStoreInvoice, useStoreOrder } from './useVendorOrders'
import { VENDOR_CHANGED_BY_LABELS, VENDOR_STATUS_LABELS, VENDOR_STATUS_TONES, vendorPaymentLine, vendorStatusMessage } from './vendorOrderStatus'

/** One order of the store: what to pack, for whom, and the store's next step. */
export function VendorOrderPage() {
  const { orderId } = useParams()
  const id = Number(orderId)
  return Number.isInteger(id) && id > 0 ? <OrderContent orderId={id} /> : <OrderNotFound />
}

function OrderNotFound() {
  return (
    <>
      <PageTitle title="Order not found" />
      <EmptyState icon="📦" title="We could not find this order" message="It may belong to another store." className="rounded-2xl bg-card" />
    </>
  )
}

function OrderContent({ orderId }: { orderId: number }) {
  const { data: order, isPending, isError, error, refetch } = useStoreOrder(orderId)

  if (isError && isApiError(error, 404)) return <OrderNotFound />

  return (
    <>
      <PageTitle title={`Order #${orderId}`} />
      <Link to="/vendor/orders" className="mb-4 inline-flex items-center gap-1.5 text-sm font-bold text-brand-text hover:underline">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All orders
      </Link>
      {isPending ? (
        <Skeleton className="h-96" />
      ) : isError ? (
        <ErrorState title="Could not load this order" error={error} onRetry={() => void refetch()} />
      ) : (
        <OrderDetails order={order} />
      )}
    </>
  )
}

function InvoiceButton({ orderId }: { orderId: number }) {
  const download = useDownloadStoreInvoice(orderId)
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

function Card({ title, icon: Icon, children }: { title: string; icon: LucideIcon; children: ReactNode }) {
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

function OrderDetails({ order }: { order: Order }) {
  const message = vendorStatusMessage(order)
  const stopped = isStopped(order.status)
  const address = order.deliveryAddress
  const units = order.items.reduce((sum, item) => sum + item.quantity, 0)
  const { amounts } = order
  const row = 'flex justify-between gap-4 border-b border-line py-2'

  return (
    <>
      <div className="mb-6 flex flex-wrap items-center gap-x-4 gap-y-3">
        <h1 className="text-3xl">Order #{order.id}</h1>
        <Badge tone={VENDOR_STATUS_TONES[order.status]} className="text-sm">
          {VENDOR_STATUS_LABELS[order.status]}
        </Badge>
        <p className="w-full text-sm">Placed on {formatDateTime(order.placedAt)}</p>
        <div className="flex w-full flex-wrap gap-3">
          <StepButton order={order} />
          {order.status === 'CONFIRMED' && <RejectOrderButton orderId={order.id} />}
          {hasInvoice(order.status) && <InvoiceButton orderId={order.id} />}
        </div>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-6">
          <section aria-labelledby="vendor-order-status" className={cn('rounded-2xl p-6', stopped ? 'border border-hot/40 bg-hot/5' : 'border border-brand-soft bg-brand-light')}>
            <h2 id="vendor-order-status" className="text-xl">
              {message.title}
            </h2>
            {message.text && <p className="mt-1 text-heading">{message.text}</p>}
          </section>

          <section aria-labelledby="pick-list-title" className="rounded-2xl border border-line bg-card p-6">
            <h2 id="pick-list-title" className="text-xl">
              Pick list <span className="text-base font-normal text-body">({units} items)</span>
            </h2>
            <ul aria-label="Products to pack" className="mt-2 divide-y divide-line">
              {order.items.map((item) => (
                <li key={item.productId} className="flex items-center gap-4 py-3">
                  <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-brand-light font-heading text-lg font-bold text-brand-text">
                    {item.quantity}×
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="font-heading font-bold text-heading">{item.productName}</p>
                    <p className="text-sm">
                      {formatUnit(item.unit, item.unitQuantity)} · {formatPrice(item.unitPrice)} each
                    </p>
                  </div>
                  <span className="font-heading font-bold text-heading">{formatPrice(item.lineTotal)}</span>
                </li>
              ))}
            </ul>
          </section>

          <OrderTimeline timeline={order.timeline} statusLabels={VENDOR_STATUS_LABELS} changedByLabels={VENDOR_CHANGED_BY_LABELS} noteOf={staffTimelineNote} />
        </div>

        <aside className="space-y-4">
          <Card title="Deliver to" icon={MapPin}>
            <p className="font-bold text-heading">{address.contactName}</p>
            {addressLines(address).map((line) => (
              <p key={line}>{line}</p>
            ))}
            <a href={phoneHref(address.contactPhone)} className="mt-2 inline-flex items-center gap-1.5 font-bold text-brand-text hover:underline">
              <Phone className="size-4" aria-hidden="true" />
              Call {address.contactName.split(' ')[0]}: {address.contactPhone}
            </a>
            {order.customerNote && <p className="mt-3 rounded-lg bg-surface px-3 py-2 text-heading">Note: “{order.customerNote}”</p>}
          </Card>

          <Card title="Money" icon={Wallet}>
            <dl>
              <div className={row}>
                <dt>Items total</dt>
                <dd className="font-bold text-heading">{formatPrice(amounts.itemsTotal)}</dd>
              </div>
              {amounts.couponDiscount > 0 && (
                <div className={row}>
                  <dt>Coupon {amounts.couponCode}</dt>
                  <dd className="font-bold text-heading">−{formatPrice(amounts.couponDiscount)}</dd>
                </div>
              )}
              <div className={row}>
                <dt>Delivery fee</dt>
                <dd className="font-bold text-heading">{amounts.deliveryFee > 0 ? formatPrice(amounts.deliveryFee) : 'Free'}</dd>
              </div>
              <div className="flex justify-between gap-4 py-2">
                <dt className="font-heading font-bold text-heading">Order total</dt>
                <dd className="font-heading text-lg font-bold text-brand-text">{formatPrice(amounts.totalAmount)}</dd>
              </div>
            </dl>
            <p className="text-xs">Includes GST of {formatPrice(amounts.taxIncluded)}.</p>
            <p className="mt-3 rounded-lg bg-surface px-3 py-2 text-heading">{vendorPaymentLine(order)}</p>
          </Card>

          {order.deliveryPartnerName && (
            <Card title="Delivery partner" icon={Bike}>
              <p className="font-bold text-heading">{order.deliveryPartnerName}</p>
              {order.deliveryPartnerPhone && (
                <a href={phoneHref(order.deliveryPartnerPhone)} className="mt-2 inline-flex items-center gap-1.5 font-bold text-brand-text hover:underline">
                  <Phone className="size-4" aria-hidden="true" />
                  {order.deliveryPartnerPhone}
                </a>
              )}
            </Card>
          )}
        </aside>
      </div>
    </>
  )
}

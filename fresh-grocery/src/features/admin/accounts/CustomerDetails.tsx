import { Link } from 'react-router'
import type { Gender, User } from '../../../api/types'
import { Badge } from '../../../components/ui/Badge'
import { ErrorState } from '../../../components/ui/ErrorState'
import { Skeleton } from '../../../components/ui/Skeleton'
import { ADDRESS_LABELS, addressLines } from '../../../utils/address'
import { formatDate, formatDateTime, formatPrice } from '../../../utils/format'
import { ORDER_STATUS_LABELS, ORDER_STATUS_TONES } from '../../orders/orderStatus'
import { useAdminOrders } from '../orders/useAdminOrders'
import { useCustomerAddresses, useCustomerProfile } from './useAdminAccounts'

const GENDER_LABELS: Record<Gender, string> = { MALE: 'Male', FEMALE: 'Female', OTHER: 'Other', PREFER_NOT_TO_SAY: 'Prefers not to say' }
const RECENT_ORDERS = 5

/** A customer account's delivery profile, addresses and latest orders. */
export function CustomerDetails({ account }: { account: User }) {
  const profile = useCustomerProfile(account)
  const addresses = useCustomerAddresses(profile.data?.id)
  const orders = useAdminOrders({ customerUserId: account.id }, 1)

  let profileContent
  if (profile.isPending) profileContent = <Skeleton className="h-24" />
  else if (profile.isError) profileContent = <ErrorState compact title="Could not load the profile" error={profile.error} onRetry={() => void profile.refetch()} />
  else if (!profile.data) profileContent = <p className="text-sm">No delivery profile yet (it is made from the registration a moment later).</p>
  else {
    const { fullName, phone, dateOfBirth, gender } = profile.data
    profileContent = (
      <>
        <dl className="mb-4 grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
          <div>
            <dt className="inline">Delivery name: </dt>
            <dd className="inline font-bold text-heading">{fullName}</dd>
          </div>
          <div>
            <dt className="inline">Delivery phone: </dt>
            <dd className="inline font-bold text-heading">{phone}</dd>
          </div>
          <div>
            <dt className="inline">Birthday: </dt>
            <dd className="inline font-bold text-heading">{dateOfBirth ? formatDate(dateOfBirth) : '–'}</dd>
          </div>
          <div>
            <dt className="inline">Gender: </dt>
            <dd className="inline font-bold text-heading">{gender ? GENDER_LABELS[gender] : '–'}</dd>
          </div>
        </dl>
        <h3 className="mb-2 text-base">Addresses</h3>
        {addresses.isPending ? (
          <Skeleton className="h-16" />
        ) : addresses.isError ? (
          <ErrorState compact title="Could not load the addresses" error={addresses.error} onRetry={() => void addresses.refetch()} />
        ) : addresses.data.length === 0 ? (
          <p className="text-sm">No addresses yet.</p>
        ) : (
          <ul aria-label="Addresses" className="grid gap-3 sm:grid-cols-2">
            {addresses.data.map((address) => (
              <li key={address.id} className="rounded-xl border border-line bg-card p-3 text-sm">
                <p className="mb-1 flex items-center gap-2 font-bold text-heading">
                  {ADDRESS_LABELS[address.label]}
                  {address.defaultAddress && <Badge tone="brand">Default</Badge>}
                </p>
                <p>
                  {address.contactName}, {address.contactPhone}
                </p>
                {addressLines(address).map((line) => (
                  <p key={line}>{line}</p>
                ))}
              </li>
            ))}
          </ul>
        )}
      </>
    )
  }

  return (
    <>
      <section className="rounded-2xl border border-line bg-card p-6">
        <h2 className="mb-3 text-xl">Customer profile</h2>
        {profileContent}
      </section>
      <section className="rounded-2xl border border-line bg-card p-6">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-xl">Latest orders</h2>
          <Link to={`/admin/orders?customer=${account.id}`} className="text-sm font-bold text-brand-text hover:underline">
            All their orders
          </Link>
        </div>
        {orders.isPending ? (
          <Skeleton className="h-24" />
        ) : orders.isError ? (
          <ErrorState compact title="Could not load the orders" error={orders.error} onRetry={() => void orders.refetch()} />
        ) : orders.data.content.length === 0 ? (
          <p className="text-sm">No orders yet.</p>
        ) : (
          <ul aria-label="Latest orders" className="divide-y divide-line text-sm">
            {orders.data.content.slice(0, RECENT_ORDERS).map((order) => (
              <li key={order.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2">
                <Link to={`/admin/orders/${order.id}`} className="font-bold text-brand-text hover:underline">
                  Order #{order.id}
                </Link>
                <Badge tone={ORDER_STATUS_TONES[order.status]}>{ORDER_STATUS_LABELS[order.status]}</Badge>
                <span>{order.storeName}</span>
                <span className="ml-auto text-xs">{formatDateTime(order.placedAt)}</span>
                <span className="w-20 text-right font-bold text-heading">{formatPrice(order.totalAmount)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}

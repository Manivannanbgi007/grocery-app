import { RequireCustomer } from '../auth/RequireCustomer'
import { NotificationsList } from '../notifications/NotificationsList'
import { AccountLayout } from './AccountLayout'

/** /account/notifications: every notification of the customer, in the account area. */
export function AccountNotificationsPage() {
  return (
    <RequireCustomer>
      <AccountLayout title="Notifications" crumbs={[{ label: 'My account', to: '/account' }, { label: 'Notifications' }]}>
        <NotificationsList role="CUSTOMER" />
      </AccountLayout>
    </RequireCustomer>
  )
}

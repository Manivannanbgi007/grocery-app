import { PageTitle } from '../../components/layout/PageTitle'
import { useAuth } from '../auth/authContext'
import { NotificationsList } from './NotificationsList'

/** /vendor/notifications and /delivery/notifications: every notification, inside the panel (its layout checks the login). */
export function PanelNotificationsPage() {
  const { user } = useAuth()
  return (
    <>
      <PageTitle title="Notifications" />
      <h1 className="mb-6 text-3xl">Notifications</h1>
      {user && <NotificationsList role={user.role} />}
    </>
  )
}

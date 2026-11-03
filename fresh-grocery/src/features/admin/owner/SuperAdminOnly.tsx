import { Outlet } from 'react-router'
import { PageTitle } from '../../../components/layout/PageTitle'
import { ButtonLink } from '../../../components/ui/Button'
import { EmptyState } from '../../../components/ui/EmptyState'
import { useAuth } from '../../auth/authContext'

/**
 * Route element around the Owner pages (/admin/admins, /admin/settings ...): the super admin sees the page; an admin
 * is told it belongs to the super admin (the backend would answer 403 anyway).
 */
export function SuperAdminOnly() {
  const { admin } = useAuth()

  if (admin?.role !== 'SUPER_ADMIN') {
    return (
      <>
        <PageTitle title="For the super admin" />
        <EmptyState
          icon="🔒"
          title="This page is for the super admin"
          message="Admins, platform settings, feature flags, commission, the audit log and the system status are managed by the platform owner."
          action={<ButtonLink to="/admin">Go to the dashboard</ButtonLink>}
          className="rounded-2xl bg-card"
        />
      </>
    )
  }
  return <Outlet />
}

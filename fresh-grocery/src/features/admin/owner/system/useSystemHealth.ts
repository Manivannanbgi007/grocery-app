import { useQuery } from '@tanstack/react-query'
import { getSystemHealth } from '../../../../api/monitoring'
import { queryKeys } from '../../../../api/queryKeys'
import { useAuth } from '../../../auth/authContext'

/** Prometheus scrapes every 15 s; the page asks again every 30 s while it is open. */
const CHECK_EVERY_MS = 30_000

export function useSystemHealth() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminSystemHealth(admin?.id ?? 0),
    queryFn: ({ signal }) => getSystemHealth(signal),
    enabled: admin?.role === 'SUPER_ADMIN',
    refetchInterval: CHECK_EVERY_MS,
  })
}

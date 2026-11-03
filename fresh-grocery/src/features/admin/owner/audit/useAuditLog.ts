import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { queryKeys } from '../../../../api/queryKeys'
import { searchAuditLog, type AuditLogFilter } from '../../../../api/superAdmin'
import { useAuth } from '../../../auth/authContext'

export const AUDIT_ENTRIES_PER_PAGE = 20

/** One page (1-based) of the audit log, newest first. */
export function useAuditLog(filter: Omit<AuditLogFilter, 'page'>, page: number, size = AUDIT_ENTRIES_PER_PAGE) {
  const { admin } = useAuth()
  const query = { ...filter, page: page - 1 }
  return useQuery({
    queryKey: queryKeys.adminAuditLog(admin?.id ?? 0, query, size),
    queryFn: ({ signal }) => searchAuditLog(query, size, signal),
    enabled: admin?.role === 'SUPER_ADMIN',
    placeholderData: keepPreviousData,
  })
}

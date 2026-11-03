import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { getDashboard, getPlatformSales, getPlatformTopProducts, getTopStores } from '../../../api/adminReports'
import { queryKeys } from '../../../api/queryKeys'
import { useAuth } from '../../auth/authContext'

/** The dashboard's numbers, asked for again every minute while the page is open. */
export function useDashboard() {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminDashboard(admin?.id ?? 0),
    queryFn: ({ signal }) => getDashboard(signal),
    enabled: admin !== null,
    refetchInterval: 60_000,
  })
}

/** Sales per day of all stores (no storeId) or of one store. */
export function usePlatformSales(from: string, to: string, storeId?: number) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminSales(admin?.id ?? 0, from, to, storeId),
    queryFn: ({ signal }) => getPlatformSales(from, to, storeId, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

export function useTopStores(from: string, to: string, limit: number) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminTopStores(admin?.id ?? 0, from, to, limit),
    queryFn: ({ signal }) => getTopStores(from, to, limit, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

export function usePlatformTopProducts(from: string, to: string, limit: number, storeId?: number) {
  const { admin } = useAuth()
  return useQuery({
    queryKey: queryKeys.adminTopProducts(admin?.id ?? 0, from, to, limit, storeId),
    queryFn: ({ signal }) => getPlatformTopProducts(from, to, limit, storeId, signal),
    enabled: admin !== null,
    placeholderData: keepPreviousData,
  })
}

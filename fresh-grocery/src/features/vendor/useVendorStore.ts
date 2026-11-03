import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../api/queryKeys'
import type { StoreDetails } from '../../api/types'
import {
  getMyStore,
  registerStore,
  setAcceptingOrders,
  updateDeliveryAreas,
  updateMyStore,
  updateOpeningHours,
  type OpeningHoursRequest,
  type StoreDetailsRequest,
} from '../../api/vendorStore'
import { useAuth } from '../auth/authContext'

/** The logged-in vendor's store; 404 (an ApiError) while they have not registered one. */
export function useMyStore() {
  const { vendor } = useAuth()
  return useQuery({
    queryKey: queryKeys.vendorStore(vendor?.id ?? 0),
    queryFn: ({ signal }) => getMyStore(signal),
    enabled: vendor !== null,
  })
}

/**
 * Changes to the store. Every change returns the whole store, which replaces the cached one (like the cart), so the
 * menu, the top bar and the settings page always agree. The callers show the errors.
 */
export function useStoreChanges() {
  const { vendor } = useAuth()
  const queryClient = useQueryClient()
  const storeStore = (store: StoreDetails) => queryClient.setQueryData(queryKeys.vendorStore(vendor?.id ?? 0), store)

  return {
    register: useMutation({ mutationFn: (request: StoreDetailsRequest) => registerStore(request), onSuccess: storeStore }),
    update: useMutation({ mutationFn: (request: StoreDetailsRequest) => updateMyStore(request), onSuccess: storeStore }),
    openingHours: useMutation({ mutationFn: (days: OpeningHoursRequest[]) => updateOpeningHours(days), onSuccess: storeStore }),
    deliveryAreas: useMutation({ mutationFn: (pincodes: string[]) => updateDeliveryAreas(pincodes), onSuccess: storeStore }),
    acceptingOrders: useMutation({ mutationFn: (accepting: boolean) => setAcceptingOrders(accepting), onSuccess: storeStore }),
  }
}

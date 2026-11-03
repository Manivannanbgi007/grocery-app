import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { addAddress, deleteAddress, getMyAddresses, setDefaultAddress, updateAddress, type AddressRequest } from '../../api/customers'
import { queryKeys } from '../../api/queryKeys'
import { retryWhileProfileIsCreated } from '../../api/retry'
import { useAuth } from '../auth/authContext'

/** The logged-in customer's delivery addresses (nothing is loaded for a guest). */
export function useAddresses() {
  const { customer } = useAuth()
  return useQuery({
    queryKey: queryKeys.addresses(customer?.id ?? 0),
    queryFn: ({ signal }) => getMyAddresses(signal),
    enabled: customer !== null,
    ...retryWhileProfileIsCreated,
  })
}

/**
 * Adding, changing and deleting addresses. A change can move the "default" mark to another address,
 * so after every change the whole list is loaded again (one small request).
 * The callers handle the errors (the address form shows them next to the fields).
 */
export function useAddressChanges() {
  const { customer } = useAuth()
  const queryClient = useQueryClient()
  const reloadAddresses = () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses(customer?.id ?? 0) })

  return {
    add: useMutation({ mutationFn: (request: AddressRequest) => addAddress(request), onSuccess: reloadAddresses }),
    update: useMutation({
      mutationFn: ({ addressId, request }: { addressId: number; request: AddressRequest }) => updateAddress(addressId, request),
      onSuccess: reloadAddresses,
    }),
    remove: useMutation({ mutationFn: (addressId: number) => deleteAddress(addressId), onSuccess: reloadAddresses }),
    makeDefault: useMutation({ mutationFn: (addressId: number) => setDefaultAddress(addressId), onSuccess: reloadAddresses }),
  }
}

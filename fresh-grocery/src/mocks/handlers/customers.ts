import { http } from 'msw'
import type { AddressRequest, UpdateProfileRequest } from '../../api/customers'
import { addAddress, deleteAddress, getAddresses, getProfile, setDefaultAddress, updateAddress, updateProfile } from '../mockCustomers'
import { asCustomer } from './withRole'

/* Mock of customer-service: the logged-in customer's profile and addresses. The rules are in mockCustomers.ts. */

export const customerHandlers = [
  http.get(
    '*/api/v1/customers/me',
    asCustomer(async (customer) => ({ data: getProfile(customer), message: 'Profile fetched successfully' })),
  ),

  http.put(
    '*/api/v1/customers/me',
    asCustomer(async (customer, request) => {
      const body = (await request.json()) as Partial<UpdateProfileRequest>
      return { data: updateProfile(customer, body), message: 'Profile updated successfully' }
    }),
  ),

  http.get(
    '*/api/v1/customers/me/addresses',
    asCustomer(async (customer) => ({ data: getAddresses(customer.id), message: 'Addresses fetched successfully' })),
  ),

  http.post(
    '*/api/v1/customers/me/addresses',
    asCustomer(async (customer, request) => {
      const body = (await request.json()) as AddressRequest
      return { data: addAddress(customer.id, body), message: 'Address added successfully', status: 201 }
    }),
  ),

  http.put(
    '*/api/v1/customers/me/addresses/:addressId',
    asCustomer(async (customer, request, params) => {
      const body = (await request.json()) as AddressRequest
      return { data: updateAddress(customer.id, Number(params.addressId), body), message: 'Address updated successfully' }
    }),
  ),

  http.delete(
    '*/api/v1/customers/me/addresses/:addressId',
    asCustomer(async (customer, _request, params) => {
      deleteAddress(customer.id, Number(params.addressId))
      return { data: null, message: 'Address deleted successfully' }
    }),
  ),

  http.patch(
    '*/api/v1/customers/me/addresses/:addressId/default',
    asCustomer(async (customer, _request, params) => ({ data: setDefaultAddress(customer.id, Number(params.addressId)), message: 'Default address updated' })),
  ),
]

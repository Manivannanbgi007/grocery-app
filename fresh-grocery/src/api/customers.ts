import { apiRequest } from './client'
import type { Address, AddressLabel, CustomerProfile, Gender } from './types'

/** UpdateProfileRequest: what a customer can change in the profile (not the login email). */
export interface UpdateProfileRequest {
  fullName: string
  /** 10-digit Indian mobile number, used for deliveries */
  phone: string
  /** "1994-05-17", in the past, or null */
  dateOfBirth: string | null
  gender: Gender | null
}

/**
 * The profile is created from the registration in the background: right after registering it can answer 404
 * "Customer profile not found. It is created right after registration - please try again in a moment."
 */
export function getMyProfile(signal?: AbortSignal): Promise<CustomerProfile> {
  return apiRequest<CustomerProfile>('/api/v1/customers/me', { signal })
}

/** Fails with 400 and field errors (phone, dateOfBirth "must be a past date" ...). */
export function updateMyProfile(request: UpdateProfileRequest): Promise<CustomerProfile> {
  return apiRequest<CustomerProfile>('/api/v1/customers/me', { method: 'PUT', body: request })
}

/** AddressRequest: what the customer fills in to add or change an address. */
export interface AddressRequest {
  label: AddressLabel
  contactName: string
  /** 10-digit Indian mobile number */
  contactPhone: string
  addressLine1: string
  addressLine2: string | null
  landmark: string | null
  city: string
  state: string
  /** 6 digits, not starting with 0 */
  pincode: string
  /** make this the default delivery address */
  setAsDefault: boolean
}

/*
 * The logged-in customer's delivery addresses (customer-service). The first address always becomes the default;
 * deleting the default makes the oldest remaining address the default.
 */

export function getMyAddresses(signal?: AbortSignal): Promise<Address[]> {
  return apiRequest<Address[]>('/api/v1/customers/me/addresses', { signal })
}

/** Fails with 400 (field errors, or "You can save at most 10 addresses"). */
export function addAddress(request: AddressRequest): Promise<Address> {
  return apiRequest<Address>('/api/v1/customers/me/addresses', { method: 'POST', body: request })
}

export function updateAddress(addressId: number, request: AddressRequest): Promise<Address> {
  return apiRequest<Address>(`/api/v1/customers/me/addresses/${addressId}`, { method: 'PUT', body: request })
}

export function deleteAddress(addressId: number): Promise<void> {
  return apiRequest<void>(`/api/v1/customers/me/addresses/${addressId}`, { method: 'DELETE' })
}

export function setDefaultAddress(addressId: number): Promise<Address> {
  return apiRequest<Address>(`/api/v1/customers/me/addresses/${addressId}/default`, { method: 'PATCH' })
}

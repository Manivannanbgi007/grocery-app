import type { StoreDetails } from '../../../api/types'
import type { StoreDetailsRequest } from '../../../api/vendorStore'
import { emailError, mobileError, removeEmpty, type FieldErrors } from '../../auth/validation'

/* The store details form: what the vendor types, and the rules of store-service's StoreDetailsRequest. */

export interface StoreDetailsValues {
  name: string
  description: string
  phone: string
  email: string
  addressLine1: string
  addressLine2: string
  city: string
  state: string
  pincode: string
}

export const STORE_FIELDS: readonly (keyof StoreDetailsValues)[] = ['name', 'description', 'phone', 'email', 'addressLine1', 'addressLine2', 'city', 'state', 'pincode']

/** A new store, with the vendor's own mobile number and email filled in. */
export function newStoreDetails(phone = '', email = ''): StoreDetailsValues {
  return { name: '', description: '', phone, email, addressLine1: '', addressLine2: '', city: '', state: '', pincode: '' }
}

export function storeDetailsOf(store: StoreDetails): StoreDetailsValues {
  return {
    name: store.name,
    description: store.description ?? '',
    phone: store.phone,
    email: store.email ?? '',
    addressLine1: store.addressLine1,
    addressLine2: store.addressLine2 ?? '',
    city: store.city,
    state: store.state,
    pincode: store.pincode,
  }
}

function textError(value: string, label: string, max: number, required: boolean): string | undefined {
  if (required && !value.trim()) return `Please enter the ${label}.`
  if (value.trim().length > max) return `Please use at most ${max} characters.`
  return undefined
}

export function validateStoreDetails(values: StoreDetailsValues): FieldErrors<keyof StoreDetailsValues> {
  return removeEmpty({
    name: textError(values.name, "store's name", 100, true),
    description: textError(values.description, 'description', 500, false),
    phone: mobileError(values.phone),
    // The email is optional; when given it must look like one
    email: values.email.trim() ? (emailError(values.email) ?? textError(values.email, 'email', 150, false)) : undefined,
    addressLine1: textError(values.addressLine1, 'street address', 150, true),
    addressLine2: textError(values.addressLine2, 'area', 150, false),
    city: textError(values.city, 'city', 60, true),
    state: textError(values.state, 'state', 60, true),
    pincode: /^[1-9]\d{5}$/.test(values.pincode) ? undefined : 'Please enter the 6-digit PIN code.',
  })
}

/** Trimmed, empty optional parts sent as null. */
export function toStoreDetailsRequest(values: StoreDetailsValues): StoreDetailsRequest {
  return {
    name: values.name.trim(),
    description: values.description.trim() || null,
    phone: values.phone,
    email: values.email.trim() || null,
    addressLine1: values.addressLine1.trim(),
    addressLine2: values.addressLine2.trim() || null,
    city: values.city.trim(),
    state: values.state.trim(),
    pincode: values.pincode,
  }
}

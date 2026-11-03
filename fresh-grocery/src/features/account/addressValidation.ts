import type { AddressRequest } from '../../api/customers'
import type { Address, AddressLabel } from '../../api/types'
import { INDIAN_MOBILE, type FieldErrors } from '../auth/validation'

/*
 * The address form: what the customer types, and the same rules as customer-service's AddressRequest,
 * checked in the browser first so mistakes show next to the field at once.
 */

export interface AddressValues {
  label: AddressLabel
  contactName: string
  contactPhone: string
  addressLine1: string
  addressLine2: string
  landmark: string
  city: string
  state: string
  pincode: string
  setAsDefault: boolean
}

export type AddressField = Exclude<keyof AddressValues, 'setAsDefault'>

export const ADDRESS_FIELDS: readonly AddressField[] = ['label', 'contactName', 'contactPhone', 'addressLine1', 'addressLine2', 'landmark', 'city', 'state', 'pincode']

/** 6 digits, not starting with 0 */
const PINCODE = /^[1-9]\d{5}$/

/** A new address, with the customer's own name and number filled in. */
export function newAddressValues(contactName = '', contactPhone = ''): AddressValues {
  return { label: 'HOME', contactName, contactPhone, addressLine1: '', addressLine2: '', landmark: '', city: '', state: '', pincode: '', setAsDefault: false }
}

/** A saved address in the form, to change it. */
export function addressValuesOf(address: Address): AddressValues {
  return {
    label: address.label,
    contactName: address.contactName,
    contactPhone: address.contactPhone,
    addressLine1: address.addressLine1,
    addressLine2: address.addressLine2 ?? '',
    landmark: address.landmark ?? '',
    city: address.city,
    state: address.state,
    pincode: address.pincode,
    setAsDefault: address.defaultAddress,
  }
}

function textError(value: string, label: string, max: number, required: boolean): string | undefined {
  if (required && !value.trim()) return `Please enter the ${label}.`
  if (value.trim().length > max) return `Please use at most ${max} characters.`
  return undefined
}

export function validateAddress(values: AddressValues): FieldErrors<AddressField> {
  const errors: FieldErrors<AddressField> = {
    contactName: textError(values.contactName, 'name of the person receiving the order', 100, true),
    contactPhone: INDIAN_MOBILE.test(values.contactPhone) ? undefined : 'Please enter a 10-digit Indian mobile number (starting with 6, 7, 8 or 9).',
    addressLine1: textError(values.addressLine1, 'flat, house or building', 150, true),
    addressLine2: textError(values.addressLine2, 'street or area', 150, false),
    landmark: textError(values.landmark, 'landmark', 100, false),
    city: textError(values.city, 'city', 60, true),
    state: textError(values.state, 'state', 60, true),
    pincode: PINCODE.test(values.pincode) ? undefined : 'Please enter the 6-digit PIN code.',
  }
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => message))
}

/** The request for the backend: trimmed, empty optional parts sent as null. */
export function toAddressRequest(values: AddressValues): AddressRequest {
  return {
    label: values.label,
    contactName: values.contactName.trim(),
    contactPhone: values.contactPhone,
    addressLine1: values.addressLine1.trim(),
    addressLine2: values.addressLine2.trim() || null,
    landmark: values.landmark.trim() || null,
    city: values.city.trim(),
    state: values.state.trim(),
    pincode: values.pincode,
    setAsDefault: values.setAsDefault,
  }
}

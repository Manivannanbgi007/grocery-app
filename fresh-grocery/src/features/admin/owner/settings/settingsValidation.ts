import type { SettingsRequest } from '../../../../api/superAdmin'
import type { PlatformSettings } from '../../../../api/types'
import { removeEmpty, type FieldErrors } from '../../../auth/validation'

/*
 * The platform settings form. The amounts are kept as typed (text) and checked with the rules of SettingsRequest in
 * platform-service before saving; the backend checks again.
 */

export interface SettingsValues {
  deliveryFee: string
  freeDeliveryFrom: string
  minimumOrderValue: string
  supportEmail: string
  supportPhone: string
}

export const SETTINGS_FIELDS = ['deliveryFee', 'freeDeliveryFrom', 'minimumOrderValue', 'supportEmail', 'supportPhone'] as const

const MAX_DELIVERY_FEE = 500
const AMOUNT = /^\d+(\.\d{1,2})?$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function settingsValuesOf(settings: PlatformSettings): SettingsValues {
  return {
    deliveryFee: String(settings.deliveryFee),
    freeDeliveryFrom: String(settings.freeDeliveryFrom),
    minimumOrderValue: String(settings.minimumOrderValue),
    supportEmail: settings.supportEmail,
    supportPhone: settings.supportPhone,
  }
}

/** A rupee amount of 0 or more with at most two decimals, e.g. "30" or "29.50". */
function amountError(value: string, example: string): string | undefined {
  if (!value.trim()) return `Enter an amount, e.g. ${example}.`
  if (!AMOUNT.test(value.trim())) return 'Use a number of 0 or more, with at most two decimals.'
  return undefined
}

export function validateSettings(values: SettingsValues): FieldErrors<keyof SettingsValues> {
  const errors: FieldErrors<keyof SettingsValues> = {}
  errors.deliveryFee = amountError(values.deliveryFee, '30')
  if (!errors.deliveryFee && Number(values.deliveryFee) > MAX_DELIVERY_FEE) errors.deliveryFee = `The delivery fee is at most ₹${MAX_DELIVERY_FEE}.`
  errors.freeDeliveryFrom = amountError(values.freeDeliveryFrom, '299')
  errors.minimumOrderValue = amountError(values.minimumOrderValue, '99')
  if (!errors.minimumOrderValue && !errors.freeDeliveryFrom && Number(values.minimumOrderValue) > Number(values.freeDeliveryFrom)) {
    errors.minimumOrderValue = 'The minimum order cannot be higher than the free delivery amount.'
  }
  if (!values.supportEmail.trim()) errors.supportEmail = 'Enter the support email, e.g. support@grocery.com.'
  else if (!EMAIL.test(values.supportEmail.trim()) || values.supportEmail.trim().length > 120) errors.supportEmail = 'Enter a valid email address of at most 120 characters.'
  if (!values.supportPhone.trim()) errors.supportPhone = 'Enter the support phone number, e.g. 1800-123-4567.'
  else if (values.supportPhone.trim().length > 20) errors.supportPhone = 'Use at most 20 characters.'
  return removeEmpty(errors)
}

/** The request: the amounts as numbers; the currency is sent back unchanged (the website shows every price in ₹). */
export function toSettingsRequest(values: SettingsValues, currency: string): SettingsRequest {
  return {
    currency,
    deliveryFee: Number(values.deliveryFee),
    freeDeliveryFrom: Number(values.freeDeliveryFrom),
    minimumOrderValue: Number(values.minimumOrderValue),
    supportEmail: values.supportEmail.trim(),
    supportPhone: values.supportPhone.trim(),
  }
}

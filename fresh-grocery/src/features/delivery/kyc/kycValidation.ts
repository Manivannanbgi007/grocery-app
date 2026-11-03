import type { KycRequest } from '../../../api/delivery'
import type { DeliveryPartner, VehicleType } from '../../../api/types'
import type { FieldErrors } from '../../auth/validation'

/** What the KYC form holds (as typed). */
export interface KycValues {
  vehicleType: VehicleType | ''
  vehicleNumber: string
  drivingLicenseNumber: string
  /** the zone id as text, '' = not chosen */
  zoneId: string
}

export const KYC_FIELDS: readonly (keyof KycValues)[] = ['vehicleType', 'vehicleNumber', 'drivingLicenseNumber', 'zoneId']

/** KycRequest's pattern: two letters, 1 or 2 digits, up to 3 letters, 4 digits ("KA01AB1234"). */
const VEHICLE_NUMBER = /^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{4}$/

/** "ka 01 ab 1234" -> "KA01AB1234": capitals, no spaces or dashes. */
export const cleanVehicleNumber = (value: string) => value.toUpperCase().replace(/[\s-]/g, '')

/** The form filled in from the partner's details (a rejected or waiting KYC), or empty. */
export function kycValuesOf(partner: DeliveryPartner | undefined): KycValues {
  return {
    vehicleType: partner?.vehicleType ?? '',
    vehicleNumber: partner?.vehicleNumber ?? '',
    drivingLicenseNumber: partner?.drivingLicenseNumber ?? '',
    zoneId: partner?.zoneId ? String(partner.zoneId) : '',
  }
}

/** The KycRequest rules, checked before asking the server. */
export function validateKyc(values: KycValues): FieldErrors<keyof KycValues> {
  const errors: FieldErrors<keyof KycValues> = {}
  if (!values.vehicleType) errors.vehicleType = 'Choose your vehicle.'
  if (!VEHICLE_NUMBER.test(cleanVehicleNumber(values.vehicleNumber))) errors.vehicleNumber = 'Enter the number as on the plate, e.g. KA01AB1234.'
  const licence = values.drivingLicenseNumber.trim()
  if (licence.length < 5 || licence.length > 30) errors.drivingLicenseNumber = 'Enter your driving licence number (5 to 30 characters).'
  if (!values.zoneId) errors.zoneId = 'Choose the area where you want to deliver.'
  return errors
}

export function toKycRequest(values: KycValues): KycRequest {
  return {
    vehicleType: values.vehicleType as VehicleType,
    vehicleNumber: cleanVehicleNumber(values.vehicleNumber),
    drivingLicenseNumber: values.drivingLicenseNumber.trim(),
    zoneId: Number(values.zoneId),
  }
}

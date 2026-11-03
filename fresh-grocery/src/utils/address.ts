import type { AddressLabel, DeliveryAddress } from '../api/types'

/** The parts of an address that are shown; both saved addresses and the copy on an order have them. */
type AddressParts = Pick<DeliveryAddress, 'addressLine1' | 'addressLine2' | 'landmark' | 'city' | 'state' | 'pincode'>

/**
 * An address as lines to show under the name, empty parts left out:
 *   ["Flat 302, Lakeview Apartments", "2nd Main Road, HAL 2nd Stage", "Near Indiranagar Metro", "Bengaluru, Karnataka 560038"]
 */
export function addressLines(address: AddressParts): string[] {
  return [address.addressLine1, address.addressLine2, address.landmark, `${address.city}, ${address.state} ${address.pincode}`].filter(
    (line): line is string => Boolean(line?.trim()),
  )
}

export const ADDRESS_LABELS: Record<AddressLabel, string> = {
  HOME: 'Home',
  WORK: 'Work',
  OTHER: 'Other',
}

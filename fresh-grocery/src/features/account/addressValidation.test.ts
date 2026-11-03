import { describe, expect, it } from 'vitest'
import { newAddressValues, toAddressRequest, validateAddress, type AddressValues } from './addressValidation'

const valid: AddressValues = {
  ...newAddressValues('Priya Sharma', '9000000004'),
  addressLine1: 'Flat 302, Lakeview Apartments',
  city: 'Bengaluru',
  state: 'Karnataka',
  pincode: '560038',
}

describe('validateAddress', () => {
  it('accepts a complete address (street and landmark are optional)', () => {
    expect(validateAddress(valid)).toEqual({})
  })

  it('uses the backend rules for the mobile number and the PIN code', () => {
    expect(validateAddress({ ...valid, contactPhone: '5000000000' }).contactPhone).toMatch(/10-digit Indian mobile/)
    expect(validateAddress({ ...valid, pincode: '060038' }).pincode).toBe('Please enter the 6-digit PIN code.')
    expect(validateAddress({ ...valid, pincode: '56003' }).pincode).toBe('Please enter the 6-digit PIN code.')
  })

  it('needs the name, building, city and state, within the backend lengths', () => {
    const errors = validateAddress({ ...valid, contactName: ' ', addressLine1: '', city: '', state: '' })
    expect(Object.keys(errors).sort()).toEqual(['addressLine1', 'city', 'contactName', 'state'])
    expect(validateAddress({ ...valid, landmark: 'x'.repeat(101) }).landmark).toBe('Please use at most 100 characters.')
  })
})

describe('toAddressRequest', () => {
  it('trims the text and sends empty optional parts as null', () => {
    expect(toAddressRequest({ ...valid, contactName: ' Priya Sharma ', addressLine2: '  ', landmark: ' Near Metro ' })).toMatchObject({
      contactName: 'Priya Sharma',
      addressLine2: null,
      landmark: 'Near Metro',
      setAsDefault: false,
    })
  })
})

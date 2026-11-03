import type { AddressRequest, UpdateProfileRequest } from '../api/customers'
import type { Address, ApiErrorDetail, CustomerProfile, Gender } from '../api/types'
import { demoAddresses } from './data/addresses'
import type { DemoUser } from './data/users'
import { MockApiError, validationFailed } from './mockApiError'
import { allUsers, signUpRoleOf } from './mockAuth'

/*
 * The customers' profiles and address books for the mock API, kept in memory (they reset when the page reloads).
 * The rules copy customer-service (Customer.addAddress / removeAddress / makeDefault): at most 10 addresses,
 * exactly one default, the first address is always the default.
 */

const MAX_ADDRESSES = 10

/** The profile fields a customer can change (the rest comes from the account). */
type ProfileDetails = Pick<CustomerProfile, 'fullName' | 'phone' | 'dateOfBirth' | 'gender'>

/** The seed profiles of customer-service; users registered in the mock get one the first time it is asked for. */
function seedProfiles(): Map<number, ProfileDetails> {
  return new Map<number, ProfileDetails>([
    [4, { fullName: 'Priya Sharma', phone: '9000000004', dateOfBirth: '1994-05-17', gender: 'FEMALE' }],
    [7, { fullName: 'Rahul Verma', phone: '9000000007', dateOfBirth: '1990-11-02', gender: 'MALE' }],
  ])
}

let profiles = seedProfiles()
let addressBooks = new Map<number, Address[]>()
let nextAddressId = 100

function seedAddressBooks(): Map<number, Address[]> {
  return new Map(Object.entries(demoAddresses).map(([userId, addresses]) => [Number(userId), addresses.map((address) => ({ ...address }))]))
}

addressBooks = seedAddressBooks()

/** For tests: back to the seed profiles and addresses. */
export function resetMockCustomers(): void {
  profiles = seedProfiles()
  addressBooks = seedAddressBooks()
  nextAddressId = 100
}

// ---------- Profile ----------

export function getProfile(user: DemoUser): CustomerProfile {
  let details = profiles.get(user.id)
  if (!details) {
    details = { fullName: user.fullName, phone: user.phone, dateOfBirth: null, gender: null }
    profiles.set(user.id, details)
  }
  return { id: user.id, userId: user.id, email: user.email, ...details, addressCount: addressBook(user.id).length, createdAt: user.createdAt }
}

const GENDERS: Gender[] = ['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY']

/** The @Valid rules of UpdateProfileRequest. */
export function updateProfile(user: DemoUser, body: Partial<UpdateProfileRequest>): CustomerProfile {
  const errors: ApiErrorDetail[] = []
  if (!body.fullName?.trim()) errors.push({ field: 'fullName', message: 'must not be blank' })
  else if (body.fullName.length > 100) errors.push({ field: 'fullName', message: 'size must be between 0 and 100' })
  if (!/^[6-9]\d{9}$/.test(body.phone ?? '')) errors.push({ field: 'phone', message: 'must be a valid 10-digit Indian mobile number' })
  if (body.dateOfBirth && !(Date.parse(body.dateOfBirth) < Date.now())) errors.push({ field: 'dateOfBirth', message: 'must be a past date' })
  if (body.gender && !GENDERS.includes(body.gender)) errors.push({ field: 'gender', message: 'must be one of MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY' })
  if (errors.length > 0) throw validationFailed(errors)
  profiles.set(user.id, { fullName: body.fullName!.trim(), phone: body.phone!, dateOfBirth: body.dateOfBirth || null, gender: body.gender ?? null })
  return getProfile(user)
}

// ---------- Admin (AdminCustomerServiceImpl) ----------

/** Customer profiles whose name, email or phone contains the keyword, oldest first. In the mock a profile's id is its user id. */
export function searchCustomerProfiles(keyword: string | null): CustomerProfile[] {
  const word = keyword?.trim().toLowerCase()
  // customer-service only has profiles of accounts that signed up as customers (a later role change makes none)
  return allUsers()
    .filter((user) => signUpRoleOf(user.id) === 'CUSTOMER')
    .map(getProfile)
    .filter((profile) => !word || profile.fullName.toLowerCase().includes(word) || profile.email.toLowerCase().includes(word) || profile.phone.includes(word))
    .sort((a, b) => a.id - b.id)
}

export function customerAddresses(customerId: number): Address[] {
  const customer = allUsers().find((user) => user.id === customerId && signUpRoleOf(user.id) === 'CUSTOMER')
  if (!customer) throw new MockApiError(404, `Customer not found with id: ${customerId}`)
  return addressBook(customer.id)
}

// ---------- Addresses ----------

function addressBook(userId: number): Address[] {
  let addresses = addressBooks.get(userId)
  if (!addresses) {
    addresses = []
    addressBooks.set(userId, addresses)
  }
  return addresses
}

export function getAddresses(userId: number): Address[] {
  return addressBook(userId)
}

/** Only this customer's addresses: someone else's address looks like one that does not exist. */
export function findAddress(userId: number, addressId: number): Address {
  const address = addressBook(userId).find((candidate) => candidate.id === addressId)
  if (!address) throw new MockApiError(404, `Address not found with id: ${addressId}`)
  return address
}

function makeDefault(userId: number, newDefault: Address): void {
  for (const address of addressBook(userId)) address.defaultAddress = address === newDefault
}

/** The @Valid rules of AddressRequest, with Bean Validation's default messages. */
export function checkAddressRequest(body: Partial<AddressRequest>): void {
  const errors: ApiErrorDetail[] = []
  const required = (field: keyof AddressRequest, max: number) => {
    const value = body[field]
    if (typeof value !== 'string' || !value.trim()) errors.push({ field, message: 'must not be blank' })
    else if (value.length > max) errors.push({ field, message: `size must be between 0 and ${max}` })
  }
  const optional = (field: keyof AddressRequest, max: number) => {
    const value = body[field]
    if (typeof value === 'string' && value.length > max) errors.push({ field, message: `size must be between 0 and ${max}` })
  }
  if (!body.label) errors.push({ field: 'label', message: 'must not be null' })
  required('contactName', 100)
  if (!/^[6-9]\d{9}$/.test(body.contactPhone ?? '')) errors.push({ field: 'contactPhone', message: 'must be a valid 10-digit Indian mobile number' })
  required('addressLine1', 150)
  optional('addressLine2', 150)
  optional('landmark', 100)
  required('city', 60)
  required('state', 60)
  if (!/^[1-9]\d{5}$/.test(body.pincode ?? '')) errors.push({ field: 'pincode', message: 'must be a valid 6-digit PIN code' })
  if (errors.length > 0) throw validationFailed(errors)
}

/** The address fields of a request, trimmed; empty optional fields become null. */
function addressFields(request: AddressRequest) {
  return {
    label: request.label,
    contactName: request.contactName.trim(),
    contactPhone: request.contactPhone,
    addressLine1: request.addressLine1.trim(),
    addressLine2: request.addressLine2?.trim() || null,
    landmark: request.landmark?.trim() || null,
    city: request.city.trim(),
    state: request.state.trim(),
    pincode: request.pincode,
  }
}

export function addAddress(userId: number, request: AddressRequest): Address {
  checkAddressRequest(request)
  const addresses = addressBook(userId)
  if (addresses.length >= MAX_ADDRESSES) throw new MockApiError(400, `You can save at most ${MAX_ADDRESSES} addresses`)
  const address: Address = { id: nextAddressId++, ...addressFields(request), latitude: null, longitude: null, defaultAddress: false }
  addresses.push(address)
  if (request.setAsDefault || addresses.length === 1) makeDefault(userId, address)
  return address
}

export function updateAddress(userId: number, addressId: number, request: AddressRequest): Address {
  checkAddressRequest(request)
  const address = findAddress(userId, addressId)
  Object.assign(address, addressFields(request))
  if (request.setAsDefault) makeDefault(userId, address)
  return address
}

/** Deleting the default address makes the oldest remaining one the default. */
export function deleteAddress(userId: number, addressId: number): void {
  const address = findAddress(userId, addressId)
  const remaining = addressBook(userId).filter((candidate) => candidate !== address)
  addressBooks.set(userId, remaining)
  if (address.defaultAddress && remaining.length > 0) remaining[0].defaultAddress = true
}

export function setDefaultAddress(userId: number, addressId: number): Address {
  const address = findAddress(userId, addressId)
  makeDefault(userId, address)
  return address
}

import { describe, expect, it } from 'vitest'
import { ApiError } from '../../api/client'
import { safeNextPath, serverFieldErrors, validateLogin, validateRegistration } from './validation'

const valid = { fullName: 'Neha Gupta', email: 'neha@example.com', phone: '9876543210', password: 'Fresh123' }

describe('validateRegistration', () => {
  it('accepts valid values', () => {
    expect(validateRegistration(valid)).toEqual({})
  })

  it('uses the backend rules', () => {
    expect(validateRegistration({ fullName: ' ', email: 'neha@', phone: '5876543210', password: 'short1' })).toEqual({
      fullName: 'Please enter your name.',
      email: 'Please enter a valid email address, like name@example.com.',
      phone: 'Please enter a 10-digit Indian mobile number (starting with 6, 7, 8 or 9).',
      password: 'Use 8 to 64 characters.',
    })
    expect(validateRegistration({ ...valid, password: 'onlyletters' }).password).toBe('Use at least one letter and one digit.')
    expect(validateRegistration({ ...valid, password: '12345678' }).password).toBe('Use at least one letter and one digit.')
  })
})

describe('validateLogin', () => {
  it('needs both fields', () => {
    expect(validateLogin({ email: '', password: '' })).toEqual({ email: 'Please enter your email address.', password: 'Please enter your password.' })
    expect(validateLogin({ email: 'customer1@grocery.com', password: 'x' })).toEqual({})
  })
})

describe('serverFieldErrors', () => {
  const fields = ['fullName', 'email', 'phone', 'password'] as const

  it('puts the backend field errors next to the fields', () => {
    const error = new ApiError(400, 'Validation failed', [{ field: 'phone', message: 'must be a valid 10-digit Indian mobile number' }])
    expect(serverFieldErrors(error, fields)).toEqual({ phone: 'Must be a valid 10-digit Indian mobile number.' })
  })

  it('explains a duplicate email or phone (409)', () => {
    expect(serverFieldErrors(new ApiError(409, 'Email is already registered: a@b.co'), fields)).toEqual({ email: 'This email address is already registered. Log in instead?' })
    expect(serverFieldErrors(new ApiError(409, 'Phone number is already registered: 9000000004'), fields)).toEqual({ phone: 'This mobile number is already registered.' })
  })
})

describe('safeNextPath', () => {
  it('only allows pages of this app', () => {
    expect(safeNextPath('/cart')).toBe('/cart')
    expect(safeNextPath('/shop?q=milk')).toBe('/shop?q=milk')
    expect(safeNextPath(null)).toBe('/')
    expect(safeNextPath('https://evil.example')).toBe('/')
    expect(safeNextPath('//evil.example')).toBe('/')
  })

  it('refuses a backslash and control characters (browsers read "/\\evil.com" as "//evil.com")', () => {
    expect(safeNextPath('/\\evil.example')).toBe('/')
    expect(safeNextPath('/\tcart')).toBe('/')
    expect(new URL(safeNextPath('/\\evil.example'), 'http://localhost:5173').host).toBe('localhost:5173')
  })
})

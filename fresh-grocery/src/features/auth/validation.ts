import type { ApiError } from '../../api/client'

/*
 * The same rules as the backend (auth-service RegisterRequest / LoginRequest), checked in the browser first,
 * so mistakes are shown next to the field at once. The backend checks again, of course.
 */

export type FieldErrors<Field extends string> = Partial<Record<Field, string>>

export interface LoginValues {
  email: string
  password: string
}

export interface RegisterValues {
  fullName: string
  email: string
  phone: string
  password: string
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
/** 10 digits, starting with 6, 7, 8 or 9 */
export const INDIAN_MOBILE = /^[6-9]\d{9}$/
const LETTER_AND_DIGIT = /^(?=.*[A-Za-z])(?=.*\d).+$/

export function emailError(email: string): string | undefined {
  if (!email.trim()) return 'Please enter your email address.'
  if (!EMAIL.test(email.trim())) return 'Please enter a valid email address, like name@example.com.'
  return undefined
}

export function validateLogin(values: LoginValues): FieldErrors<keyof LoginValues> {
  const errors: FieldErrors<keyof LoginValues> = {}
  errors.email = emailError(values.email)
  if (!values.password) errors.password = 'Please enter your password.'
  return removeEmpty(errors)
}

export function validateRegistration(values: RegisterValues): FieldErrors<keyof RegisterValues> {
  const errors: FieldErrors<keyof RegisterValues> = {}
  errors.fullName = nameError(values.fullName)
  errors.email = emailError(values.email)
  errors.phone = mobileError(values.phone)
  errors.password = passwordError(values.password)
  return removeEmpty(errors)
}

export function nameError(name: string): string | undefined {
  if (!name.trim()) return 'Please enter your name.'
  if (name.trim().length > 100) return 'Please use at most 100 characters.'
  return undefined
}

export function mobileError(phone: string): string | undefined {
  return INDIAN_MOBILE.test(phone) ? undefined : 'Please enter a 10-digit Indian mobile number (starting with 6, 7, 8 or 9).'
}

/** The password rules of auth-service: 8 to 64 characters with a letter and a digit (registration, change, reset). */
export function passwordError(password: string): string | undefined {
  if (password.length < 8 || password.length > 64) return 'Use 8 to 64 characters.'
  if (!LETTER_AND_DIGIT.test(password)) return 'Use at least one letter and one digit.'
  return undefined
}

/** The same rules one by one, ticked off under the password box while typing (PasswordRules). */
export const PASSWORD_RULES: { label: string; met: (password: string) => boolean }[] = [
  { label: '8 to 64 characters', met: (password) => password.length >= 8 && password.length <= 64 },
  { label: 'At least one letter', met: (password) => /[A-Za-z]/.test(password) },
  { label: 'At least one digit', met: (password) => /\d/.test(password) },
]

/**
 * The backend's answer as field errors:
 * - 400 "Validation failed" with details: [{ field: "phone", message: "must be a valid ..." }]
 * - 409 "Email is already registered: ..." / "Phone number is already registered: ..."
 */
export function serverFieldErrors<Field extends string>(error: ApiError, fields: readonly Field[]): FieldErrors<Field> {
  const errors: FieldErrors<Field> = {}
  for (const detail of error.details) {
    if (detail.field && (fields as readonly string[]).includes(detail.field)) {
      errors[detail.field as Field] = detail.message.charAt(0).toUpperCase() + detail.message.slice(1) + '.'
    }
  }
  if (error.status === 409 && error.message.startsWith('Email') && fields.includes('email' as Field)) {
    errors['email' as Field] = 'This email address is already registered. Log in instead?'
  }
  if (error.status === 409 && error.message.startsWith('Phone') && fields.includes('phone' as Field)) {
    errors['phone' as Field] = 'This mobile number is already registered.'
  }
  return errors
}

export function hasErrors(errors: object): boolean {
  return Object.keys(errors).length > 0
}

export function removeEmpty<T extends object>(errors: T): T {
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => message)) as T
}

/** Only addresses inside this app are used after logging in (?next=/cart), never another website. */
export function safeNextPath(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') && !hasBackslashOrControl(next) ? next : '/'
}

/** Browsers read "/\evil.com" as "//evil.com" (another website), and drop tabs and line breaks from addresses. */
function hasBackslashOrControl(path: string): boolean {
  return [...path].some((char) => char === '\\' || char.charCodeAt(0) < 0x20)
}

// Client-side checks that mirror the staff API's rules. The server stays the authority; these checks only
// keep a request from being sent when it is known to fail.

const PLAYER_NAME = /^[A-Za-z0-9_]{1,16}$/
const UUID = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
const CURRENCY_CODE = /^[A-Z]{3,8}$/

export function isPlayerName(value: string): boolean {
  return PLAYER_NAME.test(value)
}

export function isUuid(value: string): boolean {
  return UUID.test(value)
}

export function isCurrencyCode(value: string): boolean {
  return CURRENCY_CODE.test(value)
}

/** A reason is 1 to 255 characters and not blank after trimming. */
export function checkReason(value: string): 'REASON_REQUIRED' | 'REASON_TOO_LONG' | null {
  const trimmed = value.trim()
  if (trimmed.length === 0) {
    return 'REASON_REQUIRED'
  }
  if (trimmed.length > 255) {
    return 'REASON_TOO_LONG'
  }
  return null
}

/** Display names of clients: 1 to 128 characters, not blank after trimming. */
export function checkDisplayName(value: string): boolean {
  const trimmed = value.trim()
  return trimmed.length >= 1 && trimmed.length <= 128
}

// Money is always a plain decimal string ("12.50", "-0.10", "0"). Nothing in this module converts an
// amount to a JavaScript number: formatting and validation work on the digits of the string.

const PLAIN_DECIMAL = /^-?\d+(\.\d+)?$/

export function isPlainDecimal(value: string): boolean {
  return PLAIN_DECIMAL.test(value)
}

export function fractionLength(value: string): number {
  const dot = value.indexOf('.')
  return dot < 0 ? 0 : value.length - dot - 1
}

/**
 * Formats a plain decimal string for German readers: comma as decimal separator, dot between thousand
 * groups, a leading minus for negative values. The fraction digits of a value are shown as sent; the API
 * always sends exactly the fraction digits of the currency, and a whole number gets the currency's digits.
 * A value that is not a plain decimal is returned unchanged rather than guessed at.
 */
export function formatMoney(amount: string, fractionDigits: number): string {
  if (!isPlainDecimal(amount)) {
    return amount
  }
  const negative = amount.startsWith('-')
  const unsigned = negative ? amount.slice(1) : amount
  const dot = unsigned.indexOf('.')
  const integerPart = dot < 0 ? unsigned : unsigned.slice(0, dot)
  const fractionPart = dot < 0 ? '' : unsigned.slice(dot + 1)
  const paddedFraction = dot < 0 ? '0'.repeat(fractionDigits) : fractionPart
  const isZero = /^0*$/.test(integerPart) && /^0*$/.test(paddedFraction)
  const grouped = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  const sign = negative && !isZero ? '-' : ''
  return paddedFraction.length > 0 ? `${sign}${grouped},${paddedFraction}` : `${sign}${grouped}`
}

export type AmountProblem = 'AMOUNT_INVALID' | 'SCALE_EXCEEDED'

/**
 * Checks an amount typed into a form before any request is sent. The user may type a comma or a dot as the
 * decimal separator; the result is the plain decimal string the API expects. Returns the problem code of the
 * staff API (`AMOUNT_INVALID`, `SCALE_EXCEEDED`) or the normalized amount.
 */
export function checkAmountInput(input: string, fractionDigits: number):
  | { ok: true, amount: string }
  | { ok: false, problem: AmountProblem } {
  const normalized = input.trim().replace(',', '.')
  if (!/^\d+(\.\d+)?$/.test(normalized) || /^0*(\.0*)?$/.test(normalized)) {
    return { ok: false, problem: 'AMOUNT_INVALID' }
  }
  if (fractionLength(normalized) > fractionDigits) {
    return { ok: false, problem: 'SCALE_EXCEEDED' }
  }
  return { ok: true, amount: normalized }
}

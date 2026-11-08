import { describe, expect, it } from 'vitest'
import { checkAmountInput, formatMoney } from '~/utils/money'

describe('formatMoney', () => {
  it('shows a negative value with a comma and two fraction digits', () => {
    expect(formatMoney('-0.10', 2)).toBe('-0,10')
  })

  it('shows the fraction digits of a value as sent', () => {
    expect(formatMoney('12.5', 2)).toBe('12,5')
  })

  it('gives a whole number the fraction digits of the currency', () => {
    expect(formatMoney('12', 2)).toBe('12,00')
  })

  it('shows eight fraction digits exactly', () => {
    expect(formatMoney('12.345678', 8)).toBe('12,345678')
  })

  it('shows zero with the currency fraction digits', () => {
    expect(formatMoney('0', 2)).toBe('0,00')
  })

  it('shows a negative zero without a sign', () => {
    expect(formatMoney('-0.00', 2)).toBe('0,00')
  })

  it('groups the integer digits in thousands with a dot', () => {
    expect(formatMoney('1234567.50', 2)).toBe('1.234.567,50')
  })

  it('returns a value that is not a plain decimal unchanged instead of guessing', () => {
    expect(formatMoney('1e3', 2)).toBe('1e3')
  })
})

describe('checkAmountInput', () => {
  it('accepts a comma as decimal separator and returns a plain decimal', () => {
    expect(checkAmountInput('12,5', 2)).toEqual({ ok: true, amount: '12.5' })
  })

  it('accepts an amount with exactly the fraction digits of the currency', () => {
    expect(checkAmountInput('1.25', 2)).toEqual({ ok: true, amount: '1.25' })
  })

  it('refuses an amount with more fraction digits than the currency has', () => {
    expect(checkAmountInput('1.005', 2)).toEqual({ ok: false, problem: 'SCALE_EXCEEDED' })
  })

  it('refuses zero as an adjust amount', () => {
    expect(checkAmountInput('0.00', 2)).toEqual({ ok: false, problem: 'AMOUNT_INVALID' })
  })

  it('refuses a negative amount, because the direction is chosen by the field', () => {
    expect(checkAmountInput('-1', 2)).toEqual({ ok: false, problem: 'AMOUNT_INVALID' })
  })

  it('refuses two separators', () => {
    expect(checkAmountInput('1.2.3', 2)).toEqual({ ok: false, problem: 'AMOUNT_INVALID' })
  })

  it('refuses an empty input', () => {
    expect(checkAmountInput('   ', 2)).toEqual({ ok: false, problem: 'AMOUNT_INVALID' })
  })
})

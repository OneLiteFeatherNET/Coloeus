import { describe, expect, it } from 'vitest'
import { formatDay, formatInstant } from '~/utils/time'

// The time zone is passed in, never read from the machine running the tests.
describe('formatInstant', () => {
  it('shows a UTC instant in the time zone of the user', () => {
    expect(formatInstant('2026-11-08T10:15:30Z', 'Europe/Berlin')).toBe('08.11.2026, 11:15')
  })

  it('keeps the UTC clock when the time zone is UTC', () => {
    expect(formatInstant('2026-11-08T10:15:30Z', 'UTC')).toBe('08.11.2026, 10:15')
  })

  it('moves a late UTC instant to the next day in a zone ahead of UTC', () => {
    expect(formatInstant('2026-11-07T23:30:00Z', 'Europe/Berlin')).toBe('08.11.2026, 00:30')
  })

  it('returns an unparseable value unchanged', () => {
    expect(formatInstant('kein Datum', 'UTC')).toBe('kein Datum')
  })
})

describe('formatDay', () => {
  it('shows a series day in German order', () => {
    expect(formatDay('2026-11-07')).toBe('07.11.2026')
  })

  it('returns a value that is not a day unchanged', () => {
    expect(formatDay('07/11/2026')).toBe('07/11/2026')
  })
})

import { describe, expect, it } from 'vitest'
import { checkDisplayName, checkReason, isCurrencyCode, isPlayerName, isUuid } from '~/utils/validation'
import { validReturnPath } from '#shared/utils/returnPath'

describe('player name', () => {
  it('accepts letters, digits and underscore up to 16 characters', () => {
    expect(isPlayerName('Steve_99')).toBe(true)
  })

  it('refuses a name longer than 16 characters', () => {
    expect(isPlayerName('a'.repeat(17))).toBe(false)
  })

  it('refuses a character outside the player name alphabet', () => {
    expect(isPlayerName('Steve!')).toBe(false)
  })
})

describe('uuid', () => {
  it('accepts the canonical form', () => {
    expect(isUuid('5f0c2a6e-1b2d-4c3e-8f4a-9b0c1d2e3f40')).toBe(true)
  })

  it('refuses a value without hyphens', () => {
    expect(isUuid('5f0c2a6e1b2d4c3e8f4a9b0c1d2e3f40')).toBe(false)
  })
})

describe('currency code', () => {
  it('accepts three upper-case letters', () => {
    expect(isCurrencyCode('COI')).toBe(true)
  })

  it('refuses lower-case letters', () => {
    expect(isCurrencyCode('coins')).toBe(false)
  })
})

describe('reason', () => {
  it('refuses a blank reason after trimming', () => {
    expect(checkReason('   ')).toBe('REASON_REQUIRED')
  })

  it('refuses a reason longer than 255 characters', () => {
    expect(checkReason('x'.repeat(256))).toBe('REASON_TOO_LONG')
  })

  it('accepts a reason of exactly 255 characters', () => {
    expect(checkReason('x'.repeat(255))).toBeNull()
  })
})

describe('display name of a client', () => {
  it('refuses a blank name', () => {
    expect(checkDisplayName('  ')).toBe(false)
  })

  it('accepts a name of 128 characters', () => {
    expect(checkDisplayName('x'.repeat(128))).toBe(true)
  })
})

describe('return path', () => {
  it('keeps a path under /admin/', () => {
    expect(validReturnPath('/admin/konten?x=1')).toBe('/admin/konten?x=1')
  })

  it('replaces a foreign address with the start page', () => {
    expect(validReturnPath('https://example.org/')).toBe('/admin/')
  })

  it('replaces a protocol-relative path with the start page', () => {
    expect(validReturnPath('//example.org/admin/')).toBe('/admin/')
  })

  it('replaces a path outside /admin/ with the start page', () => {
    expect(validReturnPath('/v1/accounts')).toBe('/admin/')
  })

  it('replaces a path longer than 512 characters with the start page', () => {
    expect(validReturnPath(`/admin/${'a'.repeat(506)}`)).toBe('/admin/')
  })

  it('replaces a missing return path with the start page', () => {
    expect(validReturnPath(null)).toBe('/admin/')
  })
})

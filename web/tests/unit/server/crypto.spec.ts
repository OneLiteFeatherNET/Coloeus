import { describe, expect, it } from 'vitest'
import { constantTimeEquals, cookieDigest, createSealer, pkceChallenge, randomToken } from '../../../server/lib/crypto'

describe('createSealer', () => {
  it('opens what it sealed', () => {
    const sealer = createSealer('s'.repeat(32))

    expect(sealer.open(sealer.seal('access-token-value'))).toBe('access-token-value')
  })

  it('does not reveal the plain text in the sealed value', () => {
    const sealer = createSealer('s'.repeat(32))

    expect(sealer.seal('refresh-token-value')).not.toContain('refresh-token-value')
  })

  it('refuses a sealed value that was changed', () => {
    const sealer = createSealer('s'.repeat(32))
    const sealed = Buffer.from(sealer.seal('id-token-value'), 'base64url')
    sealed[sealed.length - 1] = (sealed[sealed.length - 1] ?? 0) ^ 0xff

    expect(() => sealer.open(sealed.toString('base64url'))).toThrow()
  })

  it('refuses a value sealed under another session secret', () => {
    const sealed = createSealer('a'.repeat(32)).seal('secret-token')

    expect(() => createSealer('b'.repeat(32)).open(sealed)).toThrow()
  })

  it('uses a fresh nonce for every seal of the same plain text', () => {
    const sealer = createSealer('s'.repeat(32))

    expect(sealer.seal('same')).not.toBe(sealer.seal('same'))
  })
})

describe('cookieDigest', () => {
  it('is a keyed digest that differs from the cookie value', () => {
    const value = randomToken(32)

    expect(cookieDigest('secret-one-that-is-long-enough-x', value)).not.toContain(value)
  })

  it('depends on the session secret', () => {
    expect(cookieDigest('secret-one-that-is-long-enough-x', 'cookie'))
      .not.toBe(cookieDigest('secret-two-that-is-long-enough-y', 'cookie'))
  })
})

describe('pkceChallenge', () => {
  it('computes the S256 challenge of the RFC 7636 example verifier', () => {
    expect(pkceChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'))
      .toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')
  })
})

describe('constantTimeEquals', () => {
  it('accepts equal strings', () => {
    expect(constantTimeEquals('token-1', 'token-1')).toBe(true)
  })

  it('refuses strings of another length without comparing their content', () => {
    expect(constantTimeEquals('token-1', 'token-10')).toBe(false)
  })
})

describe('randomToken', () => {
  it('carries 256 bits when asked for 32 bytes', () => {
    expect(Buffer.from(randomToken(32), 'base64url')).toHaveLength(32)
  })
})

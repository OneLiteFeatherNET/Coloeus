import { createCipheriv, createDecipheriv, createHash, createHmac, hkdfSync, randomBytes, timingSafeEqual } from 'node:crypto'

// Primitives of the session store: random tokens, the keyed hash of the cookie value, and AES-256-GCM for the
// tokens at rest. Every key is derived from the session secret, so a copy of Dragonfly alone reveals no cookie.

export function randomToken(bytes = 32): string {
  return randomBytes(bytes).toString('base64url')
}

/** The store key part of a cookie value: HMAC-SHA256 with the session secret, hex encoded. */
export function cookieDigest(sessionSecret: string, cookieValue: string): string {
  return createHmac('sha256', sessionSecret).update(cookieValue).digest('hex')
}

/** The PKCE S256 challenge of a verifier. */
export function pkceChallenge(verifier: string): string {
  return createHash('sha256').update(verifier).digest('base64url')
}

export function constantTimeEquals(a: string, b: string): boolean {
  const left = Buffer.from(a, 'utf8')
  const right = Buffer.from(b, 'utf8')
  if (left.length !== right.length) {
    return false
  }
  return timingSafeEqual(left, right)
}

export interface Sealer {
  seal: (plaintext: string) => string
  open: (sealed: string) => string
}

/** AES-256-GCM with a key derived from the session secret. `open` throws when the value was changed. */
export function createSealer(sessionSecret: string): Sealer {
  const key = Buffer.from(hkdfSync('sha256', sessionSecret, 'coloeus-staff', 'coloeus-staff-token-key-v1', 32))
  return {
    seal(plaintext) {
      const iv = randomBytes(12)
      const cipher = createCipheriv('aes-256-gcm', key, iv)
      const ciphertext = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()])
      const tag = cipher.getAuthTag()
      return Buffer.concat([iv, tag, ciphertext]).toString('base64url')
    },
    open(sealed) {
      const data = Buffer.from(sealed, 'base64url')
      if (data.length < 12 + 16) {
        throw new Error('sealed value is too short')
      }
      const decipher = createDecipheriv('aes-256-gcm', key, data.subarray(0, 12))
      decipher.setAuthTag(data.subarray(12, 28))
      return Buffer.concat([decipher.update(data.subarray(28)), decipher.final()]).toString('utf8')
    }
  }
}

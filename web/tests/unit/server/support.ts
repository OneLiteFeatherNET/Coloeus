// Fixtures of the BFF tests. Every test builds its own dependencies: an in-memory storage, a clock the test moves
// by hand, a fetch that fails unless the test replaces it, and locally generated ES256 keys (no network, no timers).

import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT, type CryptoKey, type JWTVerifyGetKey } from 'jose'
import { createStorage } from 'unstorage'
import { loadConfig, type WebConfig } from '../../../server/lib/config'
import { createSealer } from '../../../server/lib/crypto'
import type { Log, StaffDeps } from '../../../server/lib/deps'
import type { CoreRequest } from '../../../server/lib/http'
import { tenantEndpoints, type TokenSet } from '../../../server/lib/oidc'
import { SessionStore } from '../../../server/lib/store'

export const TENANT = '11111111-2222-3333-4444-555555555555'
export const CLIENT_ID = 'aaaaaaaa-0000-4000-8000-000000000001'
export const AUDIENCE = 'e6ba3e14-7b03-4792-b6aa-d963f776bd0c'
export const SESSION_SECRET = 'test-secret-with-more-than-32-bytes-of-entropy'
export const BASE_URL = 'https://coloeus.test'
export const BACKEND_URL = 'https://backend.test'
export const ISSUER = tenantEndpoints(TENANT).issuer
/** 2026-11-08T10:00:00Z, the fixed start of every test clock. */
export const START = Date.UTC(2026, 10, 8, 10, 0, 0)
export const HOUR = 60 * 60 * 1000
export const MINUTE = 60 * 1000

export function testConfig(): WebConfig {
  return loadConfig({
    NODE_ENV: 'test',
    COLOEUS_WEB_CLIENT_ID: CLIENT_ID,
    COLOEUS_WEB_CLIENT_SECRET: 'client-secret-value',
    COLOEUS_WEB_BASE_URL: BASE_URL,
    COLOEUS_WEB_SESSION_SECRET: SESSION_SECRET,
    COLOEUS_WEB_STORAGE_URL: 'redis://unused.test:6379',
    COLOEUS_TENANT_ID: TENANT,
    COLOEUS_AUDIENCE: AUDIENCE,
    COLOEUS_BACKEND_URL: BACKEND_URL
  })
}

export interface Keys {
  privateKey: CryptoKey
  jwks: JWTVerifyGetKey
}

export async function createKeys(): Promise<Keys> {
  const { publicKey, privateKey } = await generateKeyPair('ES256', { extractable: true })
  const jwk = { ...await exportJWK(publicKey), kid: 'test-key', alg: 'ES256', use: 'sig' }
  return { privateKey, jwks: createLocalJWKSet({ keys: [jwk] }) }
}

export interface LogLine {
  event: string
  fields: Record<string, string | number | null> | undefined
}

export interface Fixture {
  deps: StaffDeps
  clock: { now: number }
  storage: ReturnType<typeof createStorage>
  logs: LogLine[]
  keys: Keys
}

export function fixture(keys: Keys, options: { fetch?: typeof fetch } = {}): Fixture {
  const clock = { now: START }
  const storage = createStorage()
  const logs: LogLine[] = []
  const log: Log = (event, fields) => {
    logs.push({ event, fields })
  }
  const deps: StaffDeps = {
    config: testConfig(),
    now: () => clock.now,
    fetch: options.fetch ?? (async () => {
      throw new Error('the unit test has no network')
    }),
    signal: () => new AbortController().signal,
    jwks: keys.jwks,
    store: new SessionStore(storage),
    sealer: createSealer(SESSION_SECRET),
    log
  }
  return { deps, clock, storage, logs, keys }
}

async function sign(keys: Keys, payload: Record<string, unknown>, audience: string, expiresAt: number): Promise<string> {
  return new SignJWT(payload)
    .setProtectedHeader({ alg: 'ES256', kid: 'test-key' })
    .setIssuer(ISSUER)
    .setAudience(audience)
    .setIssuedAt(Math.floor(START / 1000))
    .setExpirationTime(Math.floor(expiresAt / 1000))
    .sign(keys.privateKey)
}

export interface IdTokenOptions {
  oid?: string
  sid?: string
  nonce: string
  name?: string
  audience?: string
  expiresAt?: number
  omit?: 'sid' | 'oid'
}

export async function idToken(keys: Keys, options: IdTokenOptions): Promise<string> {
  const claims: Record<string, unknown> = {
    oid: options.oid ?? 'user-oid-1',
    sid: options.sid ?? 'entra-session-1',
    nonce: options.nonce,
    name: options.name ?? 'Alex'
  }
  const payload = Object.fromEntries(Object.entries(claims).filter(([claim]) => claim !== options.omit))
  return sign(keys, payload, options.audience ?? CLIENT_ID, options.expiresAt ?? START + HOUR)
}

export interface AccessTokenOptions {
  oid?: string
  name?: string
  roles?: string[]
  scope?: string | null
  audience?: string
  expiresAt?: number
}

export async function accessToken(keys: Keys, options: AccessTokenOptions = {}): Promise<string> {
  const payload: Record<string, unknown> = {
    oid: options.oid ?? 'user-oid-1',
    name: options.name ?? 'Alex',
    roles: options.roles ?? ['Coloeus.Admin']
  }
  if (options.scope !== null) {
    payload.scp = options.scope ?? 'Coloeus.Access'
  }
  return sign(keys, payload, options.audience ?? AUDIENCE, options.expiresAt ?? START + HOUR)
}

export function tokenSet(overrides: Partial<TokenSet> = {}): TokenSet {
  return {
    idToken: 'id-token-value',
    accessToken: 'access-token-value',
    refreshToken: 'refresh-token-value',
    accessExpiresAt: START + HOUR,
    ...overrides
  }
}

export function makeRequest(method: string, path: string, options: {
  cookie?: string
  headers?: Record<string, string>
  body?: string
} = {}): { req: CoreRequest, reads: () => number } {
  const headers = new Headers(options.headers)
  if (options.cookie !== undefined) {
    headers.set('cookie', `__Host-coloeus_staff=${options.cookie}`)
  }
  let reads = 0
  return {
    req: {
      method,
      url: new URL(path, BASE_URL),
      headers,
      readBody: async () => {
        reads += 1
        return options.body ?? ''
      }
    },
    reads: () => reads
  }
}

export function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
}

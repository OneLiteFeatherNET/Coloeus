import { randomToken, cookieDigest } from './crypto'
import type { StaffDeps } from './deps'
import { refreshTokens, verifyAccessToken, type TokenSet } from './oidc'
import type { StaffRole } from '../../shared/me'

// The server-side session. The cookie holds 256 random bits; Dragonfly holds the record under the keyed digest of
// the cookie value. The Entra tokens are stored sealed (AES-256-GCM), never in clear text and never sent out.

export const ABSOLUTE_LIFETIME_MS = 8 * 60 * 60 * 1000
export const IDLE_TIMEOUT_MS = 30 * 60 * 1000
export const REFRESH_MARGIN_MS = 2 * 60 * 1000

export interface SessionRecord {
  oid: string
  name: string
  roles: StaffRole[]
  sid: string
  csrfToken: string
  createdAt: number
  lastSeenAt: number
  absoluteExpiresAt: number
  idleExpiresAt: number
  accessExpiresAt: number
  sealedAccessToken: string
  sealedRefreshToken: string
  sealedIdToken: string
}

export interface Session {
  /** The store key of the record (relative to the mount). */
  key: string
  record: SessionRecord
}

export interface SessionStart {
  oid: string
  name: string
  roles: StaffRole[]
  sid: string
  tokens: TokenSet
}

function ttlSeconds(deps: StaffDeps, record: SessionRecord): number {
  const expiry = Math.min(record.idleExpiresAt, record.absoluteExpiresAt)
  return Math.max(1, Math.ceil((expiry - deps.now()) / 1000))
}

function keyOf(deps: StaffDeps, cookieValue: string): string {
  return `session:${cookieDigest(deps.config.sessionSecret, cookieValue)}`
}

async function save(deps: StaffDeps, key: string, record: SessionRecord): Promise<void> {
  await deps.store.putJson(key, record, ttlSeconds(deps, record))
}

/** Creates the session of a validated login and returns the cookie value that identifies it. */
export async function createSession(deps: StaffDeps, start: SessionStart): Promise<{ cookieValue: string, session: Session }> {
  const now = deps.now()
  const cookieValue = randomToken(32)
  const key = keyOf(deps, cookieValue)
  const record: SessionRecord = {
    oid: start.oid,
    name: start.name,
    roles: start.roles,
    sid: start.sid,
    csrfToken: randomToken(32),
    createdAt: now,
    lastSeenAt: now,
    absoluteExpiresAt: now + ABSOLUTE_LIFETIME_MS,
    idleExpiresAt: now + IDLE_TIMEOUT_MS,
    accessExpiresAt: start.tokens.accessExpiresAt,
    sealedAccessToken: deps.sealer.seal(start.tokens.accessToken),
    sealedRefreshToken: deps.sealer.seal(start.tokens.refreshToken),
    sealedIdToken: deps.sealer.seal(start.tokens.idToken)
  }
  await save(deps, key, record)
  await deps.store.indexSession(start.sid, key, ttlSeconds(deps, record))
  return { cookieValue, session: { key, record } }
}

/** Loads the session of a cookie value. An expired record is deleted and answered as absent. */
export async function loadSession(deps: StaffDeps, cookieValue: string | undefined): Promise<Session | null> {
  if (!cookieValue) {
    return null
  }
  const key = keyOf(deps, cookieValue)
  const record = await deps.store.getJson<SessionRecord>(key)
  if (!record) {
    return null
  }
  const now = deps.now()
  if (now >= record.absoluteExpiresAt || now >= record.idleExpiresAt) {
    await deps.store.remove(key)
    return null
  }
  return { key, record }
}

/** Every call under /admin/api moves the idle expiry forward, never past the absolute expiry. */
export async function touchSession(deps: StaffDeps, session: Session): Promise<void> {
  const now = deps.now()
  session.record.lastSeenAt = now
  session.record.idleExpiresAt = Math.min(now + IDLE_TIMEOUT_MS, session.record.absoluteExpiresAt)
  await save(deps, session.key, session.record)
}

export async function destroySession(deps: StaffDeps, session: Session): Promise<void> {
  await deps.store.remove(session.key)
}

/**
 * The access token to forward with, refreshed first when fewer than two minutes of its life remain. A refresh
 * that Entra refuses deletes the session and answers null.
 */
export async function freshAccessToken(deps: StaffDeps, session: Session): Promise<string | null> {
  const { record } = session
  if (record.accessExpiresAt - deps.now() >= REFRESH_MARGIN_MS) {
    return deps.sealer.open(record.sealedAccessToken)
  }
  try {
    const tokens = await refreshTokens(deps, deps.sealer.open(record.sealedRefreshToken), deps.sealer.open(record.sealedIdToken))
    const claims = await verifyAccessToken(deps, tokens.accessToken)
    if (claims.oid !== record.oid) {
      throw new Error('refreshed token belongs to another user')
    }
    record.accessExpiresAt = tokens.accessExpiresAt
    record.sealedAccessToken = deps.sealer.seal(tokens.accessToken)
    record.sealedRefreshToken = deps.sealer.seal(tokens.refreshToken)
    record.sealedIdToken = deps.sealer.seal(tokens.idToken)
    await save(deps, session.key, record)
    return tokens.accessToken
  } catch {
    deps.log('session.refresh_failed', { oid: record.oid })
    await destroySession(deps, session)
    return null
  }
}

export function openIdToken(deps: StaffDeps, session: Session): string {
  return deps.sealer.open(session.record.sealedIdToken)
}

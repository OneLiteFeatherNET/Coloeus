import { beforeEach, describe, expect, it } from 'vitest'
import { ABSOLUTE_LIFETIME_MS, createSession, freshAccessToken, IDLE_TIMEOUT_MS, loadSession, touchSession, type Session } from '../../../server/lib/session'
import { accessToken, createKeys, fixture, HOUR, jsonResponse, MINUTE, START, tokenSet, type Fixture } from './support'

let fx: Fixture

beforeEach(async () => {
  fx = fixture(await createKeys())
})

/** Uses the session at every step from `from` to `until`, so that the idle timeout never runs out in between. */
async function useEvery(cookieValue: string, from: number, until: number, step = 20 * MINUTE): Promise<void> {
  for (let now = from; now <= until; now += step) {
    fx.clock.now = now
    const session = await loadSession(fx.deps, cookieValue)
    if (!session) {
      throw new Error(`the session should still be there at ${now}`)
    }
    await touchSession(fx.deps, session)
  }
}

async function startSession(): Promise<{ cookieValue: string, session: Session }> {
  return createSession(fx.deps, {
    oid: 'oid-1',
    name: 'Alex',
    roles: ['Coloeus.Admin'],
    sid: 'sid-1',
    tokens: tokenSet()
  })
}

describe('createSession', () => {
  it('sets the absolute expiry eight hours after the login and the idle expiry thirty minutes after it', async () => {
    const { session } = await startSession()

    expect(session.record.absoluteExpiresAt).toBe(START + ABSOLUTE_LIFETIME_MS)
    expect(session.record.idleExpiresAt).toBe(START + IDLE_TIMEOUT_MS)
  })

  it('stores the record under a key that holds no cookie value in clear text', async () => {
    const { cookieValue } = await startSession()

    const keys = await fx.storage.getKeys()
    expect(keys.length).toBeGreaterThan(0)
    for (const key of keys) {
      expect(key).not.toContain(cookieValue)
      const value = await fx.storage.getItem(key)
      expect(JSON.stringify(value)).not.toContain(cookieValue)
    }
  })

  it('stores the Entra tokens only sealed, never in clear text', async () => {
    await startSession()

    const stored = JSON.stringify(await fx.storage.getItems(await fx.storage.getKeys()))
    expect(stored).not.toContain('access-token-value')
    expect(stored).not.toContain('refresh-token-value')
    expect(stored).not.toContain('id-token-value')
  })

  it('indexes the session under its sid for front-channel logout', async () => {
    const { session } = await startSession()

    expect(await fx.deps.store.sessionKeysOf('sid-1')).toEqual([session.key])
  })
})

describe('loadSession', () => {
  it('finds the session of its cookie value', async () => {
    const { cookieValue, session } = await startSession()

    expect((await loadSession(fx.deps, cookieValue))?.key).toBe(session.key)
  })

  it('finds no session for an unknown cookie value', async () => {
    await startSession()

    expect(await loadSession(fx.deps, 'unknown-cookie')).toBeNull()
  })

  it('finds no session without a cookie', async () => {
    expect(await loadSession(fx.deps, undefined)).toBeNull()
  })

  it('treats the session as expired exactly at the idle boundary', async () => {
    const { cookieValue } = await startSession()
    fx.clock.now = START + IDLE_TIMEOUT_MS

    expect(await loadSession(fx.deps, cookieValue)).toBeNull()
  })

  it('keeps the session one millisecond before the idle boundary', async () => {
    const { cookieValue } = await startSession()
    fx.clock.now = START + IDLE_TIMEOUT_MS - 1

    expect(await loadSession(fx.deps, cookieValue)).not.toBeNull()
  })

  it('treats the session as expired at the absolute boundary even when it was used a minute before', async () => {
    const { cookieValue } = await startSession()
    await useEvery(cookieValue, START + 20 * MINUTE, START + ABSOLUTE_LIFETIME_MS - MINUTE)
    fx.clock.now = START + ABSOLUTE_LIFETIME_MS

    expect(await loadSession(fx.deps, cookieValue)).toBeNull()
  })
})

describe('touchSession', () => {
  it('moves the idle expiry forward on every call', async () => {
    const { cookieValue } = await startSession()
    fx.clock.now = START + 20 * MINUTE
    const session = await loadSession(fx.deps, cookieValue)
    if (!session) {
      throw new Error('the session should still be there')
    }

    await touchSession(fx.deps, session)

    expect(session.record.idleExpiresAt).toBe(START + 20 * MINUTE + IDLE_TIMEOUT_MS)
  })

  it('never moves the idle expiry past the absolute expiry', async () => {
    const { cookieValue } = await startSession()
    await useEvery(cookieValue, START + 20 * MINUTE, START + ABSOLUTE_LIFETIME_MS - MINUTE)
    fx.clock.now = START + ABSOLUTE_LIFETIME_MS - MINUTE
    const session = await loadSession(fx.deps, cookieValue)
    if (!session) {
      throw new Error('the session should still be there')
    }

    await touchSession(fx.deps, session)

    expect(session.record.idleExpiresAt).toBe(START + ABSOLUTE_LIFETIME_MS)
  })
})

describe('freshAccessToken', () => {
  it('returns the stored access token without a call while it has more than two minutes left', async () => {
    const { session } = await startSession()

    expect(await freshAccessToken(fx.deps, session)).toBe('access-token-value')
  })

  it('refreshes the token when fewer than two minutes remain and stores the new tokens sealed', async () => {
    const newAccess = await accessToken(fx.keys, { expiresAt: START + 2 * HOUR })
    const refreshing = fixture(fx.keys, {
      fetch: async () => jsonResponse(200, { access_token: newAccess, refresh_token: 'refresh-2', expires_in: 7200 })
    })
    const { session } = await createSession(refreshing.deps, {
      oid: 'user-oid-1', name: 'Alex', roles: ['Coloeus.Admin'], sid: 'sid-2',
      tokens: tokenSet({ accessExpiresAt: START + MINUTE })
    })

    expect(await freshAccessToken(refreshing.deps, session)).toBe(newAccess)
    expect(refreshing.deps.sealer.open(session.record.sealedRefreshToken)).toBe('refresh-2')
  })

  it('deletes the session when Entra refuses the refresh', async () => {
    const refused = fixture(fx.keys, { fetch: async () => jsonResponse(400, { error: 'invalid_grant' }) })
    const { cookieValue, session } = await createSession(refused.deps, {
      oid: 'user-oid-1', name: 'Alex', roles: ['Coloeus.Admin'], sid: 'sid-3',
      tokens: tokenSet({ accessExpiresAt: START + MINUTE })
    })

    expect(await freshAccessToken(refused.deps, session)).toBeNull()
    expect(await loadSession(refused.deps, cookieValue)).toBeNull()
  })

  it('refuses a refreshed token that belongs to another user and deletes the session', async () => {
    const other = await accessToken(fx.keys, { oid: 'someone-else', expiresAt: START + 2 * HOUR })
    const mixed = fixture(fx.keys, {
      fetch: async () => jsonResponse(200, { access_token: other, refresh_token: 'r', expires_in: 7200 })
    })
    const { cookieValue, session } = await createSession(mixed.deps, {
      oid: 'user-oid-1', name: 'Alex', roles: ['Coloeus.Admin'], sid: 'sid-4',
      tokens: tokenSet({ accessExpiresAt: START + MINUTE })
    })

    expect(await freshAccessToken(mixed.deps, session)).toBeNull()
    expect(await loadSession(mixed.deps, cookieValue)).toBeNull()
  })
})

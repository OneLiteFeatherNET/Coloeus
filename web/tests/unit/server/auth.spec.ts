import { beforeEach, describe, expect, it } from 'vitest'
import { completeLogin, frontchannelLogout, logout, startLogin } from '../../../server/lib/auth'
import type { CoreResponse } from '../../../server/lib/http'
import { createSession, loadSession } from '../../../server/lib/session'
import {
  accessToken, BASE_URL, createKeys, fixture, idToken, ISSUER, jsonResponse, makeRequest, tokenSet, type Fixture
} from './support'

let fx: Fixture

beforeEach(async () => {
  fx = fixture(await createKeys())
})

async function sessionKeys(): Promise<string[]> {
  return (await fx.storage.getKeys()).filter(key => key.startsWith('session:'))
}

/** Starts a login and returns the state and nonce the browser would carry through Entra. */
async function startedLogin(path = '/admin/auth/login'): Promise<{ state: string, nonce: string, location: string }> {
  const { req } = makeRequest('GET', path)
  const response = await startLogin(req, fx.deps)
  const location = response.headers.get('Location') ?? ''
  const state = new URL(location).searchParams.get('state') ?? ''
  const stored = await fx.deps.store.getJson<{ nonce: string }>(`login:${state}`)
  return { state, nonce: stored?.nonce ?? '', location }
}

/** A fake token endpoint that answers the given tokens for the code exchange. */
function tokenEndpoint(tokens: { id: string, access: string }): typeof fetch {
  return async () => jsonResponse(200, {
    id_token: tokens.id,
    access_token: tokens.access,
    refresh_token: 'refresh-token-value',
    expires_in: 3600
  })
}

async function callback(state: string, code = 'code-1'): Promise<CoreResponse> {
  const { req } = makeRequest('GET', `/admin/auth/callback?code=${code}&state=${state}`)
  return completeLogin(req, fx.deps)
}

describe('startLogin', () => {
  it('redirects to the tenant authorization endpoint with S256 and the web client', async () => {
    const { location } = await startedLogin()

    const url = new URL(location)
    expect(url.hostname).toBe('login.microsoftonline.com')
    expect(url.searchParams.get('code_challenge_method')).toBe('S256')
    expect(url.searchParams.get('client_id')).toBe(fx.deps.config.clientId)
    expect(url.searchParams.get('redirect_uri')).toBe(`${BASE_URL}/admin/auth/callback`)
  })

  it('stores the login state with the return path under the state', async () => {
    const { state } = await startedLogin('/admin/auth/login?returnTo=/admin/konten')

    expect(await fx.deps.store.getJson<{ returnTo: string }>(`login:${state}`)).toMatchObject({ returnTo: '/admin/konten' })
  })

  it('replaces a return path that leaves the admin area with the start page', async () => {
    const { state } = await startedLogin('/admin/auth/login?returnTo=https://example.org/')

    expect(await fx.deps.store.getJson<{ returnTo: string }>(`login:${state}`)).toMatchObject({ returnTo: '/admin/' })
  })
})

describe('completeLogin', () => {
  it('creates a session, sets the cookie with its attributes and returns to the stored path', async () => {
    const login = await startedLogin('/admin/auth/login?returnTo=/admin/konten')
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce }),
      access: await accessToken(fx.keys, { roles: ['Coloeus.Admin'] })
    })

    const response = await callback(login.state)

    expect(response.status).toBe(302)
    expect(response.headers.get('Location')).toBe('/admin/konten')
    const cookie = response.headers.get('Set-Cookie') ?? ''
    expect(cookie).toMatch(/^__Host-coloeus_staff=[A-Za-z0-9_-]{43};/)
    expect(cookie).toContain('Path=/')
    expect(cookie).toContain('HttpOnly')
    expect(cookie).toContain('Secure')
    expect(cookie).toContain('SameSite=Lax')
    expect(cookie).not.toContain('Domain')
    expect(await sessionKeys()).toHaveLength(1)
  })

  it('refuses a replayed state: the second callback creates no session', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce }),
      access: await accessToken(fx.keys)
    })
    await callback(login.state)

    const replay = await callback(login.state)

    expect(replay.headers.get('Location')).toBe('/admin/?error=login')
    expect(await sessionKeys()).toHaveLength(1)
  })

  it('refuses a callback without the code and redirects with error=login', async () => {
    const { req } = makeRequest('GET', '/admin/auth/callback?error=access_denied&state=x')

    const result = await completeLogin(req, fx.deps)

    expect(result.headers.get('Location')).toBe('/admin/?error=login')
  })

  it('refuses an ID token with another nonce', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: 'another-nonce' }),
      access: await accessToken(fx.keys)
    })

    expect((await callback(login.state)).headers.get('Location')).toBe('/admin/?error=login')
    expect(await sessionKeys()).toHaveLength(0)
  })

  it('refuses an application token, which has no scope claim', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce }),
      access: await accessToken(fx.keys, { scope: null })
    })

    expect((await callback(login.state)).headers.get('Location')).toBe('/admin/?error=login')
    expect(await sessionKeys()).toHaveLength(0)
  })

  it('refuses an access token for another audience', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce }),
      access: await accessToken(fx.keys, { audience: fx.deps.config.clientId })
    })

    expect((await callback(login.state)).headers.get('Location')).toBe('/admin/?error=login')
  })

  it('refuses a login whose ID token and access token belong to different users', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce, oid: 'user-a' }),
      access: await accessToken(fx.keys, { oid: 'user-b' })
    })

    expect((await callback(login.state)).headers.get('Location')).toBe('/admin/?error=login')
  })

  it('sends a user with neither staff role to the forbidden page without a session', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce }),
      access: await accessToken(fx.keys, { roles: ['Some.Other.Role'] })
    })

    const response = await callback(login.state)

    expect(response.headers.get('Location')).toBe('/admin/?error=forbidden')
    expect(response.headers.get('Set-Cookie')).toBeNull()
    expect(await sessionKeys()).toHaveLength(0)
  })

  it('gives a moderator a session with the moderator role only', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce }),
      access: await accessToken(fx.keys, { roles: ['Coloeus.Moderator'] })
    })

    const response = await callback(login.state)
    const cookie = response.headers.get('Set-Cookie') ?? ''
    const value = /__Host-coloeus_staff=([^;]+)/.exec(cookie)?.[1] ?? ''
    const session = await loadSession(fx.deps, value)

    expect(session?.record.roles).toEqual(['Coloeus.Moderator'])
  })

  it('refuses the callback when Entra refuses the code exchange', async () => {
    const login = await startedLogin()
    fx.deps.fetch = async () => jsonResponse(400, { error: 'invalid_grant' })

    expect((await callback(login.state)).headers.get('Location')).toBe('/admin/?error=login')
  })

  it('writes no token into the log lines', async () => {
    const login = await startedLogin()
    fx.deps.fetch = tokenEndpoint({
      id: await idToken(fx.keys, { nonce: login.nonce }),
      access: await accessToken(fx.keys)
    })

    await callback(login.state)

    const written = JSON.stringify(fx.logs)
    expect(written).not.toContain('refresh-token-value')
    expect(written).not.toContain('access-token')
  })
})

describe('logout', () => {
  async function loggedIn(): Promise<{ cookie: string, csrf: string }> {
    const { cookieValue, session } = await createSession(fx.deps, {
      oid: 'user-oid-1', name: 'Alex', roles: ['Coloeus.Admin'], sid: 'sid-logout', tokens: tokenSet()
    })
    return { cookie: cookieValue, csrf: session.record.csrfToken }
  }

  it('deletes the session and answers the Entra end-session URL with the ID token hint', async () => {
    const { cookie, csrf } = await loggedIn()
    const { req } = makeRequest('POST', '/admin/auth/logout', {
      cookie,
      headers: { 'X-CSRF-Token': csrf, Origin: BASE_URL }
    })

    const response = await logout(req, fx.deps)

    expect(response.status).toBe(200)
    const body = JSON.parse(response.body) as { logoutUrl: string }
    expect(new URL(body.logoutUrl).searchParams.get('id_token_hint')).toBe('id-token-value')
    expect(response.headers.get('Set-Cookie')).toContain('Max-Age=0')
    expect(await loadSession(fx.deps, cookie)).toBeNull()
  })

  it('refuses a logout without the CSRF token and keeps the session', async () => {
    const { cookie } = await loggedIn()
    const { req } = makeRequest('POST', '/admin/auth/logout', { cookie, headers: { Origin: BASE_URL } })

    const response = await logout(req, fx.deps)

    expect(response.status).toBe(403)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'CSRF_INVALID' })
    expect(await loadSession(fx.deps, cookie)).not.toBeNull()
  })

  it('refuses a logout from another origin even with the right token', async () => {
    const { cookie, csrf } = await loggedIn()
    const { req } = makeRequest('POST', '/admin/auth/logout', {
      cookie,
      headers: { 'X-CSRF-Token': csrf, Origin: 'https://evil.example' }
    })

    expect((await logout(req, fx.deps)).status).toBe(403)
  })

  it('answers 401 SESSION_REQUIRED without a session', async () => {
    const { req } = makeRequest('POST', '/admin/auth/logout', { headers: { Origin: BASE_URL } })

    const response = await logout(req, fx.deps)

    expect(response.status).toBe(401)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'SESSION_REQUIRED' })
  })
})

describe('frontchannelLogout', () => {
  it('deletes exactly the sessions of the given sid and keeps the others', async () => {
    const first = await createSession(fx.deps, { oid: 'u', name: 'A', roles: ['Coloeus.Admin'], sid: 'shared', tokens: tokenSet() })
    const second = await createSession(fx.deps, { oid: 'u', name: 'A', roles: ['Coloeus.Admin'], sid: 'shared', tokens: tokenSet() })
    const other = await createSession(fx.deps, { oid: 'v', name: 'B', roles: ['Coloeus.Moderator'], sid: 'other', tokens: tokenSet() })
    const { req } = makeRequest('GET', `/admin/auth/frontchannel-logout?iss=${encodeURIComponent(ISSUER)}&sid=shared`)

    const response = await frontchannelLogout(req, fx.deps)

    expect(response.status).toBe(200)
    expect(response.headers.get('Cache-Control')).toBe('no-store')
    expect(await loadSession(fx.deps, first.cookieValue)).toBeNull()
    expect(await loadSession(fx.deps, second.cookieValue)).toBeNull()
    expect(await loadSession(fx.deps, other.cookieValue)).not.toBeNull()
  })

  it('refuses an issuer that is not the tenant and deletes nothing', async () => {
    const kept = await createSession(fx.deps, { oid: 'u', name: 'A', roles: ['Coloeus.Admin'], sid: 'shared', tokens: tokenSet() })
    const { req } = makeRequest('GET', '/admin/auth/frontchannel-logout?iss=https://evil.example/v2.0&sid=shared')

    const response = await frontchannelLogout(req, fx.deps)

    expect(response.status).toBe(400)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'REQUEST_INVALID' })
    expect(await loadSession(fx.deps, kept.cookieValue)).not.toBeNull()
  })
})

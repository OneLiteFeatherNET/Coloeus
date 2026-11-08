import { describe, expect, it, beforeEach } from 'vitest'
import { authorizationUrl, endSessionUrl, LOGIN_SCOPES, OidcError, verifyAccessToken, verifyIdToken, exchangeCode, refreshTokens, tenantEndpoints } from '../../../server/lib/oidc'
import {
  accessToken, AUDIENCE, BASE_URL, CLIENT_ID, createKeys, fixture, idToken, jsonResponse, START, TENANT, type Fixture
} from './support'

let fx: Fixture

beforeEach(async () => {
  fx = fixture(await createKeys())
})

describe('authorizationUrl', () => {
  it('requests the code flow with PKCE S256, the login scopes and the given state and nonce', () => {
    const url = new URL(authorizationUrl(fx.deps.config, {
      state: 'state-1',
      nonce: 'nonce-1',
      challenge: 'challenge-1',
      redirectUri: `${BASE_URL}/admin/auth/callback`
    }))

    expect(url.origin + url.pathname).toBe(tenantEndpoints(TENANT).authorize)
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('code_challenge_method')).toBe('S256')
    expect(url.searchParams.get('scope')).toBe(LOGIN_SCOPES)
    expect(url.searchParams.get('client_id')).toBe(CLIENT_ID)
    expect(url.searchParams.get('redirect_uri')).toBe(`${BASE_URL}/admin/auth/callback`)
  })

  it('asks for offline_access and the Coloeus.Access scope of the API', () => {
    expect(LOGIN_SCOPES.split(' ')).toEqual(expect.arrayContaining([
      'openid',
      'offline_access',
      `api://${AUDIENCE}/Coloeus.Access`
    ]))
  })
})

describe('endSessionUrl', () => {
  it('names the stored ID token as hint and the admin start page as the return target', () => {
    const url = new URL(endSessionUrl(fx.deps.config, 'id-token-1'))

    expect(url.searchParams.get('id_token_hint')).toBe('id-token-1')
    expect(url.searchParams.get('post_logout_redirect_uri')).toBe(`${BASE_URL}/admin/`)
    expect(url.pathname).toBe(new URL(tenantEndpoints(TENANT).logout).pathname)
  })
})

describe('verifyIdToken', () => {
  it('accepts a token with the nonce, sid and oid of the login', async () => {
    const token = await idToken(fx.keys, { nonce: 'nonce-1', oid: 'oid-7', sid: 'sid-7' })

    const claims = await verifyIdToken(fx.deps, token, 'nonce-1')

    expect(claims).toEqual({ oid: 'oid-7', sid: 'sid-7', name: 'Alex' })
  })

  it('refuses a token whose nonce is another one', async () => {
    const token = await idToken(fx.keys, { nonce: 'other-nonce' })

    await expect(verifyIdToken(fx.deps, token, 'nonce-1')).rejects.toThrow(OidcError)
  })

  it('refuses a token issued to another client', async () => {
    const token = await idToken(fx.keys, { nonce: 'nonce-1', audience: 'another-client' })

    await expect(verifyIdToken(fx.deps, token, 'nonce-1')).rejects.toThrow('ID token is not valid')
  })

  it('refuses a token without sid', async () => {
    const token = await idToken(fx.keys, { nonce: 'nonce-1', omit: 'sid' })

    await expect(verifyIdToken(fx.deps, token, 'nonce-1')).rejects.toThrow('ID token lacks sid or oid')
  })

  it('refuses an expired token when the injected clock is past its expiry', async () => {
    const token = await idToken(fx.keys, { nonce: 'nonce-1', expiresAt: START + 60_000 })
    fx.clock.now = START + 120_000

    await expect(verifyIdToken(fx.deps, token, 'nonce-1')).rejects.toThrow('ID token is not valid')
  })
})

describe('verifyAccessToken', () => {
  it('returns the oid, name and roles of a delegated staff token', async () => {
    const token = await accessToken(fx.keys, { oid: 'oid-2', name: 'Sam', roles: ['Coloeus.Moderator'] })

    expect(await verifyAccessToken(fx.deps, token)).toEqual({ oid: 'oid-2', name: 'Sam', roles: ['Coloeus.Moderator'] })
  })

  it('refuses an application token, which has no scope claim', async () => {
    const token = await accessToken(fx.keys, { scope: null, roles: ['Coloeus.Admin'] })

    await expect(verifyAccessToken(fx.deps, token)).rejects.toThrow('lacks the scope Coloeus.Access')
  })

  it('refuses a token for another audience', async () => {
    const token = await accessToken(fx.keys, { audience: CLIENT_ID })

    await expect(verifyAccessToken(fx.deps, token)).rejects.toThrow('access token is not valid')
  })

  it('refuses a token with the scope of another API', async () => {
    const token = await accessToken(fx.keys, { scope: 'Other.Scope' })

    await expect(verifyAccessToken(fx.deps, token)).rejects.toThrow('lacks the scope Coloeus.Access')
  })
})

describe('exchangeCode', () => {
  it('posts the code, the PKCE verifier and the client secret to the tenant token endpoint', async () => {
    const seen: { url: string, body: string }[] = []
    const withFetch = fixture(fx.keys, {
      fetch: async (input, init) => {
        seen.push({ url: String(input), body: String(init?.body) })
        return jsonResponse(200, { id_token: 'i', access_token: 'a', refresh_token: 'r', expires_in: 3600 })
      }
    })

    const tokens = await exchangeCode(withFetch.deps, { code: 'code-1', verifier: 'verifier-1', redirectUri: `${BASE_URL}/admin/auth/callback` })

    expect(seen[0]?.url).toBe(tenantEndpoints(TENANT).token)
    const form = new URLSearchParams(seen[0]?.body)
    expect(form.get('code_verifier')).toBe('verifier-1')
    expect(form.get('client_secret')).toBe('client-secret-value')
    expect(tokens.accessExpiresAt).toBe(START + 3600 * 1000)
  })

  it('refuses an answer without a refresh token', async () => {
    const failing = fixture(fx.keys, {
      fetch: async () => jsonResponse(200, { id_token: 'i', access_token: 'a', expires_in: 3600 })
    })

    await expect(exchangeCode(failing.deps, { code: 'c', verifier: 'v', redirectUri: 'x' }))
      .rejects.toThrow('token response lacks a token')
  })

  it('refuses when the token endpoint is not reachable', async () => {
    const unreachable = fixture(fx.keys, {
      fetch: async () => {
        throw new TypeError('fetch failed')
      }
    })

    await expect(exchangeCode(unreachable.deps, { code: 'c', verifier: 'v', redirectUri: 'x' }))
      .rejects.toThrow('token endpoint not reachable')
  })
})

describe('refreshTokens', () => {
  it('keeps the old refresh token when Entra does not rotate it', async () => {
    const refreshing = fixture(fx.keys, {
      fetch: async () => jsonResponse(200, { access_token: 'new-access', expires_in: 3600 })
    })

    const tokens = await refreshTokens(refreshing.deps, 'old-refresh', 'old-id')

    expect(tokens).toMatchObject({ accessToken: 'new-access', refreshToken: 'old-refresh', idToken: 'old-id' })
  })
})

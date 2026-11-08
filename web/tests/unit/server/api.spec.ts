import { beforeEach, describe, expect, it } from 'vitest'
import { handleApi } from '../../../server/lib/api'
import { createSession, loadSession } from '../../../server/lib/session'
import { tokenSet, BACKEND_URL, BASE_URL, createKeys, fixture, HOUR, jsonResponse, makeRequest, MINUTE, START, type Fixture, accessToken } from './support'

interface BackendCall {
  url: string
  method: string
  headers: Headers
  body: string | undefined
}

let fx: Fixture
let backend: BackendCall[]
let answer: () => Response
let refreshAnswer: () => Response

beforeEach(async () => {
  backend = []
  answer = () => jsonResponse(200, { items: [] })
  refreshAnswer = () => jsonResponse(400, { error: 'invalid_grant' })
  fx = fixture(await createKeys(), {
    fetch: async (input, init) => {
      const url = String(input)
      if (url.startsWith(BACKEND_URL)) {
        backend.push({ url, method: String(init?.method), headers: new Headers(init?.headers), body: init?.body as string | undefined })
        return answer()
      }
      return refreshAnswer()
    }
  })
})

async function sessionWith(roles: string[], accessExpiresAt = START + HOUR) {
  return createSession(fx.deps, {
    oid: 'user-oid-1',
    name: 'Alex',
    roles: roles as never,
    sid: 'sid-api',
    tokens: tokenSet({ accessToken: 'access-1', accessExpiresAt })
  })
}

describe('handleApi: session and routing', () => {
  it('answers 401 SESSION_REQUIRED without a session cookie and calls nothing', async () => {
    const { req } = makeRequest('GET', '/admin/api/clients')

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(401)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'SESSION_REQUIRED' })
    expect(backend).toHaveLength(0)
  })

  it('answers the identity of the session without a backend call', async () => {
    const { cookieValue, session } = await sessionWith(['Coloeus.Admin'])
    const { req } = makeRequest('GET', '/admin/api/me', { cookie: cookieValue })

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(200)
    expect(JSON.parse(response.body)).toEqual({
      oid: 'user-oid-1',
      name: 'Alex',
      roles: ['Coloeus.Admin'],
      canWrite: true,
      csrfToken: session.record.csrfToken,
      sessionExpiresAt: new Date(session.record.absoluteExpiresAt).toISOString(),
      idleExpiresAt: new Date(session.record.idleExpiresAt).toISOString()
    })
    expect(backend).toHaveLength(0)
  })

  it('answers 404 NOT_FOUND for a path of the staff API that does not exist, without forwarding', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Admin'])
    const { req } = makeRequest('GET', '/admin/api/unknown', { cookie: cookieValue })

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(404)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'NOT_FOUND' })
    expect(backend).toHaveLength(0)
  })

  it('answers 404 NOT_FOUND for a method the path does not have, without forwarding', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Admin'])
    const { req } = makeRequest('DELETE', '/admin/api/clients', { cookie: cookieValue, headers: { 'X-CSRF-Token': 'x', Origin: BASE_URL } })

    expect((await handleApi(req, fx.deps)).status).toBe(404)
    expect(backend).toHaveLength(0)
  })

  it('moves the idle expiry of the session on every call', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Admin'])
    fx.clock.now = START + 20 * MINUTE
    const { req } = makeRequest('GET', '/admin/api/me', { cookie: cookieValue })

    await handleApi(req, fx.deps)

    expect((await loadSession(fx.deps, cookieValue))?.record.idleExpiresAt).toBe(START + 50 * MINUTE)
  })
})

describe('handleApi: forwarding', () => {
  it('forwards a read to the same path below /v1/staff with the query string', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Moderator'])
    const { req } = makeRequest('GET', '/admin/api/accounts/search?name=Steve', { cookie: cookieValue })

    await handleApi(req, fx.deps)

    expect(backend[0]?.url).toBe(`${BACKEND_URL}/v1/staff/accounts/search?name=Steve`)
    expect(backend[0]?.method).toBe('GET')
  })

  it('sends the delegated access token as a bearer token', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Moderator'])
    const { req } = makeRequest('GET', '/admin/api/clients', { cookie: cookieValue })

    await handleApi(req, fx.deps)

    expect(backend[0]?.headers.get('Authorization')).toBe('Bearer access-1')
  })

  it('drops the cookie and the CSRF token and forwards nothing else of the browser', async () => {
    const { cookieValue, session } = await sessionWith(['Coloeus.Admin'])
    const { req } = makeRequest('POST', '/admin/api/clients', {
      cookie: cookieValue,
      headers: { 'X-CSRF-Token': session.record.csrfToken, Origin: BASE_URL, 'Content-Type': 'application/json', 'Idempotency-Key': 'key-1' },
      body: '{"clientId":"x"}'
    })

    await handleApi(req, fx.deps)

    expect(backend[0]?.headers.get('cookie')).toBeNull()
    expect(backend[0]?.headers.get('x-csrf-token')).toBeNull()
    expect(backend[0]?.headers.get('origin')).toBeNull()
  })

  it('forwards the Idempotency-Key and the body of a write unchanged', async () => {
    const { cookieValue, session } = await sessionWith(['Coloeus.Admin'])
    const body = '{"currency":"COINS","direction":"CREDIT","amount":"10.00","reason":"Event prize"}'
    const { req } = makeRequest('POST', '/admin/api/accounts/acc-1/adjust', {
      cookie: cookieValue,
      headers: { 'X-CSRF-Token': session.record.csrfToken, Origin: BASE_URL, 'Content-Type': 'application/json', 'Idempotency-Key': 'K-1' },
      body
    })

    await handleApi(req, fx.deps)

    expect(backend[0]?.url).toBe(`${BACKEND_URL}/v1/staff/accounts/acc-1/adjust`)
    expect(backend[0]?.headers.get('Idempotency-Key')).toBe('K-1')
    expect(backend[0]?.headers.get('Content-Type')).toBe('application/json')
    expect(backend[0]?.body).toBe(body)
  })

  it('passes the status and the problem document of a backend refusal through unchanged', async () => {
    const { cookieValue, session } = await sessionWith(['Coloeus.Admin'])
    const problem = '{"type":"urn:coloeus:problem:INSUFFICIENT_FUNDS","title":"x","status":422,"detail":"d","code":"INSUFFICIENT_FUNDS"}'
    answer = () => new Response(problem, { status: 422, headers: { 'Content-Type': 'application/problem+json' } })
    const { req } = makeRequest('POST', '/admin/api/accounts/acc-1/adjust', {
      cookie: cookieValue,
      headers: { 'X-CSRF-Token': session.record.csrfToken, Origin: BASE_URL, 'Idempotency-Key': 'K-2' },
      body: '{}'
    })

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(422)
    expect(response.body).toBe(problem)
    expect(response.headers.get('Content-Type')).toBe('application/problem+json')
  })

  it('answers 500 INTERNAL without the backend host when the backend cannot be reached', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Moderator'])
    answer = () => {
      throw new TypeError(`fetch failed for ${BACKEND_URL}`)
    }
    const { req } = makeRequest('GET', '/admin/api/clients', { cookie: cookieValue })

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(500)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'INTERNAL' })
    expect(response.body).not.toContain('backend.test')
  })

  it('answers 500 INTERNAL when the backend does not answer in time', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Moderator'])
    answer = () => {
      throw new DOMException('The operation was aborted due to timeout', 'TimeoutError')
    }
    const { req } = makeRequest('GET', '/admin/api/clients', { cookie: cookieValue })

    expect((await handleApi(req, fx.deps)).status).toBe(500)
  })
})

describe('handleApi: CSRF and roles', () => {
  it('refuses a write without the CSRF token before reading its body', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Admin'])
    const { req, reads } = makeRequest('POST', '/admin/api/clients', { cookie: cookieValue, headers: { Origin: BASE_URL } })

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(403)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'CSRF_INVALID' })
    expect(reads()).toBe(0)
    expect(backend).toHaveLength(0)
  })

  it('refuses a write from a foreign origin even with the right token', async () => {
    const { cookieValue, session } = await sessionWith(['Coloeus.Admin'])
    const { req } = makeRequest('POST', '/admin/api/clients', {
      cookie: cookieValue,
      headers: { 'X-CSRF-Token': session.record.csrfToken, Origin: 'https://evil.example' }
    })

    expect((await handleApi(req, fx.deps)).status).toBe(403)
    expect(backend).toHaveLength(0)
  })

  it('refuses a moderator write with FORBIDDEN_ROLE before reading the body or calling the backend', async () => {
    const { cookieValue, session } = await sessionWith(['Coloeus.Moderator'])
    const { req, reads } = makeRequest('POST', '/admin/api/clients', {
      cookie: cookieValue,
      headers: { 'X-CSRF-Token': session.record.csrfToken, Origin: BASE_URL, 'Idempotency-Key': 'k' },
      body: '{"clientId":"x","displayName":"y"}'
    })

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(403)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'FORBIDDEN_ROLE' })
    expect(reads()).toBe(0)
    expect(backend).toHaveLength(0)
  })

  it('lets a moderator read', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Moderator'])
    const { req } = makeRequest('GET', '/admin/api/money-flow?window=7D', { cookie: cookieValue })

    expect((await handleApi(req, fx.deps)).status).toBe(200)
    expect(backend).toHaveLength(1)
  })

  it('lets an admin write and answers the backend status', async () => {
    const { cookieValue, session } = await sessionWith(['Coloeus.Admin'])
    answer = () => jsonResponse(201, { transactionId: 't-1' })
    const { req } = makeRequest('POST', '/admin/api/accounts/acc-1/reset', {
      cookie: cookieValue,
      headers: { 'X-CSRF-Token': session.record.csrfToken, Origin: BASE_URL, 'Idempotency-Key': 'k-9' },
      body: '{"currency":null,"reason":"Ban cleanup"}'
    })

    expect((await handleApi(req, fx.deps)).status).toBe(201)
  })
})

describe('handleApi: token refresh', () => {
  it('refreshes the access token before forwarding when it is about to expire', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Admin'], START + MINUTE)
    const fresh = await accessToken(fx.keys, { expiresAt: START + 2 * HOUR })
    refreshAnswer = () => jsonResponse(200, { access_token: fresh, refresh_token: 'r-2', expires_in: 7200 })
    const { req } = makeRequest('GET', '/admin/api/clients', { cookie: cookieValue })

    await handleApi(req, fx.deps)

    expect(backend[0]?.headers.get('Authorization')).toBe(`Bearer ${fresh}`)
  })

  it('answers 401 SESSION_REQUIRED and deletes the session when the refresh is refused', async () => {
    const { cookieValue } = await sessionWith(['Coloeus.Admin'], START + MINUTE)
    const { req } = makeRequest('GET', '/admin/api/clients', { cookie: cookieValue })

    const response = await handleApi(req, fx.deps)

    expect(response.status).toBe(401)
    expect(JSON.parse(response.body)).toMatchObject({ code: 'SESSION_REQUIRED' })
    expect(backend).toHaveLength(0)
    expect(await loadSession(fx.deps, cookieValue)).toBeNull()
  })
})

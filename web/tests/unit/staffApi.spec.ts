import { describe, expect, it, vi } from 'vitest'
import { ApiError, createStaffApi, type FetchLike } from '~/utils/staffApi'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

function fakeFetch(answer: () => Response | Promise<Response>) {
  const calls: { url: string, init: RequestInit | undefined }[] = []
  const fetchFn: FetchLike = vi.fn(async (url: string, init?: RequestInit) => {
    calls.push({ url, init })
    return answer()
  })
  return { fetchFn, calls }
}

function headersOf(init: RequestInit | undefined): Record<string, string> {
  return (init?.headers ?? {}) as Record<string, string>
}

const CSRF = 'csrf-token-value'

describe('staff API client', () => {
  it('sends the CSRF token on a write', async () => {
    const { fetchFn, calls } = fakeFetch(() => jsonResponse(201, { transactionId: 't' }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await api.reset('acc-1', { currency: null, reason: 'Check' }, 'key-1')

    expect(headersOf(calls[0]?.init)['X-CSRF-Token']).toBe(CSRF)
  })

  it('sends no CSRF token on a read', async () => {
    const { fetchFn, calls } = fakeFetch(() => jsonResponse(200, { items: [] }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await api.clients()

    expect(headersOf(calls[0]?.init)['X-CSRF-Token']).toBeUndefined()
  })

  it('sends the idempotency key of a write', async () => {
    const { fetchFn, calls } = fakeFetch(() => jsonResponse(201, { items: [] }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await api.registerClient({ clientId: 'c', displayName: 'Lobby' }, 'key-42')

    expect(headersOf(calls[0]?.init)['Idempotency-Key']).toBe('key-42')
  })

  it('sends the same idempotency key on a retry of the same write', async () => {
    let attempt = 0
    const { fetchFn, calls } = fakeFetch(() => {
      attempt += 1
      return attempt === 1 ? new Response(null, { status: 503 }) : jsonResponse(201, { transactionId: 't' })
    })
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await api.adjust('acc-1', { currency: 'COINS', direction: 'CREDIT', amount: '1.00', reason: 'x' }, 'key-7').catch(() => undefined)
    await api.adjust('acc-1', { currency: 'COINS', direction: 'CREDIT', amount: '1.00', reason: 'x' }, 'key-7')

    expect(headersOf(calls[0]?.init)['Idempotency-Key']).toBe('key-7')
    expect(headersOf(calls[1]?.init)['Idempotency-Key']).toBe('key-7')
  })

  it('maps a problem document to an error with its staff code', async () => {
    const { fetchFn } = fakeFetch(() => jsonResponse(422, {
      type: 'urn:coloeus:problem:INSUFFICIENT_FUNDS',
      title: 'Insufficient funds',
      status: 422,
      code: 'INSUFFICIENT_FUNDS'
    }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    const failure = await api.adjust('acc-1', { currency: 'COINS', direction: 'DEBIT', amount: '6', reason: 'x' }, 'k').catch(error => error)

    expect(failure).toBeInstanceOf(ApiError)
    expect(failure).toMatchObject({ status: 422, code: 'INSUFFICIENT_FUNDS' })
  })

  it('reports an expired session to the callback before the error is thrown', async () => {
    const onSessionRequired = vi.fn()
    const { fetchFn } = fakeFetch(() => jsonResponse(401, { code: 'SESSION_REQUIRED', status: 401 }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF, onSessionRequired })

    await expect(api.me()).rejects.toMatchObject({ code: 'SESSION_REQUIRED' })

    expect(onSessionRequired).toHaveBeenCalledTimes(1)
  })

  it('does not report other errors as an expired session', async () => {
    const onSessionRequired = vi.fn()
    const { fetchFn } = fakeFetch(() => jsonResponse(500, { code: 'INTERNAL', status: 500 }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF, onSessionRequired })

    await expect(api.me()).rejects.toMatchObject({ code: 'INTERNAL' })

    expect(onSessionRequired).not.toHaveBeenCalled()
  })

  it('maps a failed connection to the NETWORK code', async () => {
    const { fetchFn } = fakeFetch(() => Promise.reject(new TypeError('offline')))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await expect(api.me()).rejects.toMatchObject({ code: 'NETWORK', status: 0 })
  })

  it('maps an answer that is not a problem document to the UNKNOWN code', async () => {
    const { fetchFn } = fakeFetch(() => new Response('<html>Bad gateway</html>', { status: 502 }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await expect(api.me()).rejects.toMatchObject({ code: 'UNKNOWN', status: 502 })
  })

  it('sends the money flow window as a query parameter', async () => {
    const { fetchFn, calls } = fakeFetch(() => jsonResponse(200, { window: 'ALL' }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await api.moneyFlow('ALL')

    expect(calls[0]?.url).toBe('/admin/api/money-flow?window=ALL')
  })

  it('sends the cursor and limit of the postings and leaves out an empty cursor', async () => {
    const { fetchFn, calls } = fakeFetch(() => jsonResponse(200, { items: [], nextCursor: null }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    await api.postings('acc-1', { limit: 50, cursor: 'Y3Vyc29y' })
    await api.postings('acc-1', { cursor: null })

    expect(calls[0]?.url).toBe('/admin/api/accounts/acc-1/postings?limit=50&cursor=Y3Vyc29y')
    expect(calls[1]?.url).toBe('/admin/api/accounts/acc-1/postings')
  })

  it('sends the logout to the auth base with the CSRF token', async () => {
    const { fetchFn, calls } = fakeFetch(() => jsonResponse(200, { logoutUrl: 'https://login.example/logout' }))
    const api = createStaffApi({ fetch: fetchFn, csrfToken: () => CSRF })

    const answer = await api.logout()

    expect(calls[0]?.url).toBe('/admin/auth/logout')
    expect(headersOf(calls[0]?.init)['X-CSRF-Token']).toBe(CSRF)
    expect(answer.logoutUrl).toBe('https://login.example/logout')
  })
})

// Typed client of the staff API. It takes its `fetch` from outside, so tests replace it with a fake and the
// application passes the browser's fetch. Requests go to the same origin; the session is an HttpOnly cookie
// the browser sends by itself, and the browser sets the `Origin` header on writes, which a script cannot set.

import type {
  AccountDetail,
  AccountHit,
  AdjustRequest,
  ClientView,
  Currency,
  CreateCurrencyRequest,
  Grant,
  Me,
  MoneyFlow,
  MoneySeries,
  Page,
  Problem,
  RegisterClientRequest,
  ResetRequest,
  StaffAction,
  StaffBooking,
  StaffPosting
} from '~/types/api'

export const API_BASE = '/admin/api'
export const AUTH_BASE = '/admin/auth'

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>

/** A failed call. `code` is the Problem Details code of the staff API, or `NETWORK` / `UNKNOWN`. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly detail: string | undefined

  constructor(status: number, code: string, detail?: string) {
    super(code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.detail = detail
  }
}

export interface StaffApiOptions {
  fetch: FetchLike
  /** Token of the current session, sent as `X-CSRF-Token` on every write. */
  csrfToken: () => string | undefined
  /** Called once a call answers 401 `SESSION_REQUIRED`, before the error is thrown. */
  onSessionRequired?: () => void
  apiBase?: string
  authBase?: string
}

type Query = object

interface Call {
  method: 'GET' | 'POST' | 'PUT'
  path: string
  query?: Query
  body?: unknown
  /** Required for every staff write; the same value must be sent again when a submit is retried. */
  idempotencyKey?: string
  base?: string
}

export interface AccountSearch {
  name?: string
  uuid?: string
}

export interface PageRequest {
  limit?: number
  cursor?: string | null
}

export function createStaffApi(options: StaffApiOptions) {
  const apiBase = options.apiBase ?? API_BASE
  const authBase = options.authBase ?? AUTH_BASE

  async function call<T>(request: Call): Promise<T> {
    const isWrite = request.method !== 'GET'
    const headers: Record<string, string> = { Accept: 'application/json' }
    if (isWrite) {
      const token = options.csrfToken()
      if (token) {
        headers['X-CSRF-Token'] = token
      }
    }
    if (request.idempotencyKey !== undefined) {
      headers['Idempotency-Key'] = request.idempotencyKey
    }
    if (request.body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }
    const url = withQuery(`${request.base ?? apiBase}${request.path}`, request.query)
    let response: Response
    try {
      response = await options.fetch(url, {
        method: request.method,
        headers,
        credentials: 'same-origin',
        body: request.body === undefined ? undefined : JSON.stringify(request.body)
      })
    } catch {
      throw new ApiError(0, 'NETWORK')
    }
    if (response.ok) {
      if (response.status === 204) {
        return undefined as T
      }
      return await response.json() as T
    }
    const problem = await readProblem(response)
    if (problem.code === 'SESSION_REQUIRED') {
      options.onSessionRequired?.()
    }
    throw new ApiError(response.status, problem.code, problem.detail)
  }

  return {
    me: () => call<Me>({ method: 'GET', path: '/me' }),

    searchAccounts: (search: AccountSearch) =>
      call<Page<AccountHit>>({ method: 'GET', path: '/accounts/search', query: search }),

    account: (id: string) =>
      call<AccountDetail>({ method: 'GET', path: `/accounts/${encodeURIComponent(id)}` }),

    postings: (id: string, page: PageRequest = {}) =>
      call<Page<StaffPosting>>({
        method: 'GET',
        path: `/accounts/${encodeURIComponent(id)}/postings`,
        query: { limit: page.limit, cursor: page.cursor ?? undefined }
      }),

    reset: (id: string, body: ResetRequest, idempotencyKey: string) =>
      call<StaffBooking>({
        method: 'POST',
        path: `/accounts/${encodeURIComponent(id)}/reset`,
        body,
        idempotencyKey
      }),

    adjust: (id: string, body: AdjustRequest, idempotencyKey: string) =>
      call<StaffBooking>({
        method: 'POST',
        path: `/accounts/${encodeURIComponent(id)}/adjust`,
        body,
        idempotencyKey
      }),

    moneyFlow: (flowWindow: '7D' | 'ALL') =>
      call<MoneyFlow>({ method: 'GET', path: '/money-flow', query: { window: flowWindow } }),

    moneySeries: (currency: string, days: number) =>
      call<MoneySeries>({ method: 'GET', path: '/money-flow/series', query: { currency, days } }),

    clients: () => call<Page<ClientView>>({ method: 'GET', path: '/clients' }),

    registerClient: (body: RegisterClientRequest, idempotencyKey: string) =>
      call<ClientView>({ method: 'POST', path: '/clients', body, idempotencyKey }),

    replaceGrants: (clientId: string, grants: Grant[], idempotencyKey: string) =>
      call<ClientView>({
        method: 'PUT',
        path: `/clients/${encodeURIComponent(clientId)}/grants`,
        body: { grants },
        idempotencyKey
      }),

    currencies: () => call<Page<Currency>>({ method: 'GET', path: '/currencies' }),

    createCurrency: (body: CreateCurrencyRequest, idempotencyKey: string) =>
      call<Currency>({ method: 'POST', path: '/currencies', body, idempotencyKey }),

    setDefaultCurrency: (code: string, idempotencyKey: string) =>
      call<Currency>({
        method: 'PUT',
        path: `/currencies/${encodeURIComponent(code)}/default`,
        idempotencyKey
      }),

    staffActions: (page: PageRequest = {}) =>
      call<Page<StaffAction>>({
        method: 'GET',
        path: '/staff-actions',
        query: { limit: page.limit, cursor: page.cursor ?? undefined }
      }),

    logout: () => call<{ logoutUrl: string }>({ method: 'POST', path: '/logout', base: authBase })
  }
}

export type StaffApi = ReturnType<typeof createStaffApi>

function withQuery(url: string, query: Query | undefined): string {
  if (!query) {
    return url
  }
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query as Record<string, unknown>)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value))
    }
  }
  const text = params.toString()
  return text ? `${url}?${text}` : url
}

async function readProblem(response: Response): Promise<Pick<Problem, 'code' | 'detail'>> {
  try {
    const body = await response.json() as Partial<Problem>
    if (typeof body.code === 'string') {
      return { code: body.code, detail: body.detail }
    }
  } catch {
    // Not a problem document; fall through to the generic code.
  }
  return { code: 'UNKNOWN', detail: undefined }
}

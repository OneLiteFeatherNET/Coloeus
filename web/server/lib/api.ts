import { canWrite, type Me } from '../../shared/me'
import { csrfRefusal } from './auth'
import { matchEndpoint, isWrite, type Endpoint } from './endpoints'
import type { StaffDeps } from './deps'
import { instant, json, readCookie, SESSION_COOKIE, type CoreRequest, type CoreResponse } from './http'
import { internalError, forbiddenRole, notFound, sessionRequired } from './problems'
import { freshAccessToken, loadSession, touchSession, type Session } from './session'

// The browser API under /admin/api: session check, CSRF and roles, then forwarding to the backend's /v1/staff with
// the delegated access token (openspec staff-api, staff-login, problem-details).

export const BACKEND_TIMEOUT_MS = 15_000
const API_PREFIX = '/admin/api'

export function identityOf(session: Session): Me {
  const { record } = session
  return {
    oid: record.oid,
    name: record.name,
    roles: record.roles,
    canWrite: canWrite(record.roles),
    csrfToken: record.csrfToken,
    sessionExpiresAt: instant(record.absoluteExpiresAt),
    idleExpiresAt: instant(record.idleExpiresAt)
  }
}

/** Splits the path below /admin/api into segments; a trailing or doubled slash yields an empty segment. */
export function segmentsOf(pathname: string): string[] {
  return pathname.slice(API_PREFIX.length).replace(/^\//, '').split('/')
}

export async function handleApi(req: CoreRequest, deps: StaffDeps): Promise<CoreResponse> {
  const session = await loadSession(deps, readCookie(req.headers, SESSION_COOKIE))
  if (!session) {
    return sessionRequired()
  }
  await touchSession(deps, session)

  const endpoint = matchEndpoint(req.method, segmentsOf(req.url.pathname))
  if (!endpoint) {
    return notFound()
  }
  if (endpoint.local) {
    return json(200, identityOf(session))
  }
  return forward(req, deps, session, endpoint)
}

async function forward(req: CoreRequest, deps: StaffDeps, session: Session, endpoint: Endpoint): Promise<CoreResponse> {
  const write = isWrite(req.method)
  if (write) {
    const refusal = csrfRefusal(req, deps, session)
    if (refusal) {
      return refusal
    }
  }
  const roles = session.record.roles
  if (endpoint.access === 'admin' && !roles.includes('Coloeus.Admin')) {
    deps.log('role.refused', { oid: session.record.oid, method: req.method })
    return forbiddenRole()
  }
  const accessToken = await freshAccessToken(deps, session)
  if (accessToken === null) {
    return sessionRequired()
  }
  // Read only after the checks above, so that a refused write never reads its body.
  const body = write ? await req.readBody() : undefined

  const path = req.url.pathname.slice(API_PREFIX.length)
  const target = `${deps.config.backendUrl}/v1/staff${path}${req.url.search}`
  const headers = new Headers({ Authorization: `Bearer ${accessToken}`, Accept: 'application/json' })
  const contentType = req.headers.get('content-type')
  if (contentType && body !== undefined) {
    headers.set('Content-Type', contentType)
  }
  const idempotencyKey = req.headers.get('idempotency-key')
  if (idempotencyKey !== null) {
    headers.set('Idempotency-Key', idempotencyKey)
  }

  let response: Response
  let text: string
  try {
    response = await deps.fetch(target, {
      method: req.method,
      headers,
      body,
      redirect: 'manual',
      signal: deps.signal(BACKEND_TIMEOUT_MS)
    })
    text = await response.text()
  } catch {
    deps.log('backend.unavailable', { method: req.method, path: endpoint.pattern.join('/') })
    return internalError()
  }
  const result = new Headers({ 'Cache-Control': 'no-store' })
  const answerType = response.headers.get('content-type')
  if (answerType) {
    result.set('Content-Type', answerType)
  }
  return { status: response.status, headers: result, body: text }
}

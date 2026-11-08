import { validReturnPath } from '../../shared/utils/returnPath'
import { pkceChallenge, randomToken, constantTimeEquals } from './crypto'
import type { StaffDeps } from './deps'
import { authorizationUrl, exchangeCode, endSessionUrl, tenantEndpoints, verifyAccessToken, verifyIdToken } from './oidc'
import { empty, expiredSessionCookie, json, readCookie, redirect, sessionCookie, SESSION_COOKIE, type CoreRequest, type CoreResponse } from './http'
import { createSession, destroySession, loadSession, openIdToken, type Session } from './session'
import { staffRoles } from '../../shared/me'
import { csrfInvalid, requestInvalid, sessionRequired } from './problems'

// The session routes of the BFF: login, callback, logout and front-channel logout (openspec staff-login).

export const LOGIN_TTL_SECONDS = 600

interface LoginState {
  nonce: string
  verifier: string
  returnTo: string
}

function callbackUri(deps: StaffDeps): string {
  return `${deps.config.baseUrl}/admin/auth/callback`
}

/** GET /admin/auth/login: stores the login state for ten minutes and redirects to Entra with PKCE. */
export async function startLogin(req: CoreRequest, deps: StaffDeps): Promise<CoreResponse> {
  const returnTo = validReturnPath(req.url.searchParams.get('returnTo'))
  const state = randomToken(32)
  const nonce = randomToken(32)
  const verifier = randomToken(48)
  const login: LoginState = { nonce, verifier, returnTo }
  await deps.store.putJson(`login:${state}`, login, LOGIN_TTL_SECONDS)
  deps.log('login.started')
  return redirect(authorizationUrl(deps.config, {
    state,
    nonce,
    challenge: pkceChallenge(verifier),
    redirectUri: callbackUri(deps)
  }))
}

function refused(deps: StaffDeps, reason: string): CoreResponse {
  deps.log('login.refused', { reason })
  return redirect('/admin/?error=login')
}

/** GET /admin/auth/callback: validates the login, creates the session and sets the cookie. */
export async function completeLogin(req: CoreRequest, deps: StaffDeps): Promise<CoreResponse> {
  const code = req.url.searchParams.get('code')
  const state = req.url.searchParams.get('state')
  if (!code || !state || req.url.searchParams.has('error')) {
    return refused(deps, 'callback_error')
  }
  const stored = await deps.store.takeOnce(`login:${state}`)
  if (!stored) {
    return refused(deps, 'state_unknown_or_used')
  }
  const login = JSON.parse(stored) as LoginState
  let tokens
  try {
    tokens = await exchangeCode(deps, { code, verifier: login.verifier, redirectUri: callbackUri(deps) })
  } catch {
    return refused(deps, 'token_exchange_failed')
  }
  let identity
  let access
  try {
    identity = await verifyIdToken(deps, tokens.idToken, login.nonce)
    access = await verifyAccessToken(deps, tokens.accessToken)
  } catch {
    return refused(deps, 'token_invalid')
  }
  if (identity.oid !== access.oid) {
    return refused(deps, 'subject_mismatch')
  }
  const roles = staffRoles(access.roles)
  if (roles.length === 0) {
    deps.log('login.forbidden', { oid: identity.oid })
    return redirect('/admin/?error=forbidden')
  }
  const { cookieValue } = await createSession(deps, {
    oid: identity.oid,
    name: access.name,
    roles,
    sid: identity.sid,
    tokens
  })
  deps.log('login.succeeded', { oid: identity.oid, roles: roles.join(',') })
  return redirect(validReturnPath(login.returnTo), { 'Set-Cookie': sessionCookie(cookieValue) })
}

/** The CSRF check of a write: the synchronizer token and the Origin, before the body is read. */
export function csrfRefusal(req: CoreRequest, deps: StaffDeps, session: Session): CoreResponse | null {
  const token = req.headers.get('x-csrf-token') ?? ''
  const origin = req.headers.get('origin') ?? ''
  if (token === '' || !constantTimeEquals(token, session.record.csrfToken) || origin !== deps.config.origin) {
    deps.log('csrf.refused', { oid: session.record.oid })
    return csrfInvalid()
  }
  return null
}

/** POST /admin/auth/logout: deletes the session and answers the Entra end-session URL. */
export async function logout(req: CoreRequest, deps: StaffDeps): Promise<CoreResponse> {
  const session = await loadSession(deps, readCookie(req.headers, SESSION_COOKIE))
  if (!session) {
    return sessionRequired()
  }
  const refusal = csrfRefusal(req, deps, session)
  if (refusal) {
    return refusal
  }
  const idToken = openIdToken(deps, session)
  await destroySession(deps, session)
  deps.log('logout', { oid: session.record.oid })
  return json(200, { logoutUrl: endSessionUrl(deps.config, idToken) }, { 'Set-Cookie': expiredSessionCookie() })
}

/**
 * GET /admin/auth/frontchannel-logout?iss&sid: called by Entra in an iframe on single sign-out. The `sid` is the only
 * key, so no other user's session can be reached. No cookie is needed, because the iframe is third-party.
 */
export async function frontchannelLogout(req: CoreRequest, deps: StaffDeps): Promise<CoreResponse> {
  const issuer = req.url.searchParams.get('iss')
  const sid = req.url.searchParams.get('sid')
  if (issuer !== tenantEndpoints(deps.config.tenantId).issuer || !sid) {
    return requestInvalid('iss must be the issuer of the tenant and sid must be present.')
  }
  const keys = await deps.store.sessionKeysOf(sid)
  for (const key of keys) {
    await deps.store.remove(key)
  }
  await deps.store.dropIndex(sid)
  deps.log('frontchannel.logout', { sessions: keys.length })
  return empty(200)
}

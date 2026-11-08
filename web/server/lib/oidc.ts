import { jwtVerify, type JWTVerifyGetKey } from 'jose'
import type { WebConfig } from './config'

// Entra ID as seen by the BFF: the tenant endpoints, the code exchange and the validation of the ID token and the
// access token. The fetch function and the key set are injected; tests sign tokens with local keys.

export const ACCESS_SCOPE = 'Coloeus.Access'
export const LOGIN_SCOPES = `openid profile offline_access api://e6ba3e14-7b03-4792-b6aa-d963f776bd0c/${ACCESS_SCOPE}`

export interface TenantEndpoints {
  authorize: string
  token: string
  logout: string
  issuer: string
  jwks: string
}

export function tenantEndpoints(tenantId: string): TenantEndpoints {
  const authority = `https://login.microsoftonline.com/${tenantId}`
  return {
    authorize: `${authority}/oauth2/v2.0/authorize`,
    token: `${authority}/oauth2/v2.0/token`,
    logout: `${authority}/oauth2/v2.0/logout`,
    issuer: `${authority}/v2.0`,
    jwks: `${authority}/discovery/v2.0/keys`
  }
}

export interface TokenSet {
  idToken: string
  accessToken: string
  refreshToken: string
  /** Epoch milliseconds at which the access token expires. */
  accessExpiresAt: number
}

export interface IdentityClaims {
  oid: string
  sid: string
  name: string | undefined
}

export interface AccessClaims {
  oid: string
  name: string
  roles: string[]
}

export class OidcError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'OidcError'
  }
}

export interface OidcDeps {
  config: WebConfig
  fetch: typeof fetch
  now: () => number
  jwks: JWTVerifyGetKey
  signal: (milliseconds: number) => AbortSignal
}

export const TOKEN_TIMEOUT_MS = 10_000

export function authorizationUrl(config: WebConfig, input: {
  state: string
  nonce: string
  challenge: string
  redirectUri: string
}): string {
  const url = new URL(tenantEndpoints(config.tenantId).authorize)
  url.search = new URLSearchParams({
    client_id: config.clientId,
    response_type: 'code',
    redirect_uri: input.redirectUri,
    response_mode: 'query',
    scope: LOGIN_SCOPES,
    state: input.state,
    nonce: input.nonce,
    code_challenge: input.challenge,
    code_challenge_method: 'S256'
  }).toString()
  return url.toString()
}

interface TokenAnswer {
  idToken?: string
  accessToken: string
  refreshToken?: string
  accessExpiresAt: number
}

async function requestTokens(deps: OidcDeps, form: Record<string, string>): Promise<TokenAnswer> {
  const { config } = deps
  const body = new URLSearchParams({ client_id: config.clientId, client_secret: config.clientSecret, ...form })
  let response: Response
  try {
    response = await deps.fetch(tenantEndpoints(config.tenantId).token, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: body.toString(),
      signal: deps.signal(TOKEN_TIMEOUT_MS)
    })
  } catch {
    throw new OidcError('token endpoint not reachable')
  }
  if (!response.ok) {
    throw new OidcError(`token endpoint answered ${response.status}`)
  }
  const data = await response.json() as Record<string, unknown>
  if (typeof data.access_token !== 'string') {
    throw new OidcError('token response lacks an access token')
  }
  const expiresIn = typeof data.expires_in === 'number' ? data.expires_in : Number(data.expires_in)
  if (!Number.isFinite(expiresIn) || expiresIn <= 0) {
    throw new OidcError('token response lacks a lifetime')
  }
  return {
    idToken: typeof data.id_token === 'string' ? data.id_token : undefined,
    accessToken: data.access_token,
    refreshToken: typeof data.refresh_token === 'string' ? data.refresh_token : undefined,
    accessExpiresAt: deps.now() + expiresIn * 1000
  }
}

/** Exchanges the code of a login. The answer must carry an ID token and a refresh token (offline_access). */
export async function exchangeCode(deps: OidcDeps, input: { code: string, verifier: string, redirectUri: string }): Promise<TokenSet> {
  const answer = await requestTokens(deps, {
    grant_type: 'authorization_code',
    code: input.code,
    code_verifier: input.verifier,
    redirect_uri: input.redirectUri
  })
  if (answer.idToken === undefined || answer.refreshToken === undefined) {
    throw new OidcError('token response lacks a token')
  }
  return { idToken: answer.idToken, accessToken: answer.accessToken, refreshToken: answer.refreshToken, accessExpiresAt: answer.accessExpiresAt }
}

/** Refreshes the access token. The refresh token is kept when Entra does not rotate it. */
export async function refreshTokens(deps: OidcDeps, refreshToken: string, previousIdToken: string): Promise<TokenSet> {
  const answer = await requestTokens(deps, { grant_type: 'refresh_token', refresh_token: refreshToken, scope: LOGIN_SCOPES })
  return {
    idToken: answer.idToken ?? previousIdToken,
    accessToken: answer.accessToken,
    refreshToken: answer.refreshToken ?? refreshToken,
    accessExpiresAt: answer.accessExpiresAt
  }
}

/** Validates an ID token: signature, issuer, audience (the web client), expiry, nonce, `sid` and `oid`. */
export async function verifyIdToken(deps: OidcDeps, token: string, nonce: string): Promise<IdentityClaims> {
  const { payload } = await jwtVerify(token, deps.jwks, {
    issuer: tenantEndpoints(deps.config.tenantId).issuer,
    audience: deps.config.clientId,
    currentDate: new Date(deps.now())
  }).catch(() => {
    throw new OidcError('ID token is not valid')
  })
  if (payload.nonce !== nonce) {
    throw new OidcError('ID token nonce does not match')
  }
  if (typeof payload.sid !== 'string' || payload.sid === '' || typeof payload.oid !== 'string' || payload.oid === '') {
    throw new OidcError('ID token lacks sid or oid')
  }
  return {
    oid: payload.oid,
    sid: payload.sid,
    name: typeof payload.name === 'string' ? payload.name : undefined
  }
}

/** Validates an access token of the API: signature, issuer, audience, expiry, scope `Coloeus.Access`, `oid`. */
export async function verifyAccessToken(deps: OidcDeps, token: string): Promise<AccessClaims> {
  const { payload } = await jwtVerify(token, deps.jwks, {
    issuer: tenantEndpoints(deps.config.tenantId).issuer,
    audience: deps.config.audience,
    currentDate: new Date(deps.now())
  }).catch(() => {
    throw new OidcError('access token is not valid')
  })
  const scopes = typeof payload.scp === 'string' ? payload.scp.split(' ') : []
  if (!scopes.includes(ACCESS_SCOPE)) {
    throw new OidcError('access token lacks the scope Coloeus.Access')
  }
  if (typeof payload.oid !== 'string' || payload.oid === '') {
    throw new OidcError('access token lacks oid')
  }
  const roles = Array.isArray(payload.roles) ? payload.roles.filter((role): role is string => typeof role === 'string') : []
  return {
    oid: payload.oid,
    name: typeof payload.name === 'string' && payload.name !== '' ? payload.name : payload.oid,
    roles
  }
}

export function endSessionUrl(config: WebConfig, idToken: string): string {
  const url = new URL(tenantEndpoints(config.tenantId).logout)
  url.search = new URLSearchParams({
    id_token_hint: idToken,
    post_logout_redirect_uri: `${config.baseUrl}/admin/`
  }).toString()
  return url.toString()
}

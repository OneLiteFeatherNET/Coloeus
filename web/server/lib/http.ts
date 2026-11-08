// Framework-free HTTP model of the BFF. The routes under server/routes translate the Nitro event into a
// CoreRequest and the CoreResponse back into an answer, so every rule below is testable with plain objects.

export interface CoreRequest {
  method: string
  url: URL
  headers: Headers
  /** Reads the raw body. Called only after the CSRF and role checks, so a refused write never reads its body. */
  readBody: () => Promise<string>
}

export interface CoreResponse {
  status: number
  headers: Headers
  body: string
}

export const SESSION_COOKIE = '__Host-coloeus_staff'

const NO_STORE = 'no-store'

export function json(status: number, data: unknown, headers: HeadersInit = {}): CoreResponse {
  const result = new Headers(headers)
  result.set('Content-Type', 'application/json; charset=utf-8')
  result.set('Cache-Control', NO_STORE)
  return { status, headers: result, body: JSON.stringify(data) }
}

export function redirect(location: string, headers: HeadersInit = {}): CoreResponse {
  const result = new Headers(headers)
  result.set('Location', location)
  result.set('Cache-Control', NO_STORE)
  return { status: 302, headers: result, body: '' }
}

/** An RFC 9457 problem document with `application/problem+json`. */
export function problem(status: number, code: string, title: string, detail: string, headers: HeadersInit = {}): CoreResponse {
  const result = new Headers(headers)
  result.set('Content-Type', 'application/problem+json')
  result.set('Cache-Control', NO_STORE)
  const body = { type: `urn:coloeus:problem:${code}`, title, status, detail, code }
  return { status, headers: result, body: JSON.stringify(body) }
}

export function empty(status: number, headers: HeadersInit = {}): CoreResponse {
  const result = new Headers(headers)
  result.set('Cache-Control', NO_STORE)
  return { status, headers: result, body: '' }
}

/** Reads one cookie value from the `Cookie` header, or undefined. */
export function readCookie(headers: Headers, name: string): string | undefined {
  const header = headers.get('cookie')
  if (!header) {
    return undefined
  }
  for (const part of header.split(';')) {
    const index = part.indexOf('=')
    if (index < 0) {
      continue
    }
    if (part.slice(0, index).trim() === name) {
      return part.slice(index + 1).trim()
    }
  }
  return undefined
}

export function sessionCookie(value: string): string {
  return `${SESSION_COOKIE}=${value}; Path=/; HttpOnly; Secure; SameSite=Lax`
}

export function expiredSessionCookie(): string {
  return `${SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax`
}

export function instant(epochMillis: number): string {
  return new Date(epochMillis).toISOString()
}

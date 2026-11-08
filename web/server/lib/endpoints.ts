// The browser-facing endpoints under /admin/api and their roles (openspec staff-api, "Endpoints"). A path that is not
// in this table is answered 404 NOT_FOUND without forwarding. `me` is answered by the BFF and is not forwarded.

export type Method = 'GET' | 'POST' | 'PUT'

/** `read`: Coloeus.Moderator or Coloeus.Admin. `admin`: Coloeus.Admin only. */
export type Access = 'read' | 'admin'

export interface Endpoint {
  method: Method
  /** Segments below /admin/api. A segment starting with `:` takes any one non-empty segment. */
  pattern: string[]
  access: Access
  /** Answered by the BFF from the session; never forwarded. */
  local?: true
}

export const ENDPOINTS: readonly Endpoint[] = [
  { method: 'GET', pattern: ['me'], access: 'read', local: true },
  { method: 'GET', pattern: ['accounts', 'search'], access: 'read' },
  { method: 'GET', pattern: ['accounts', ':id'], access: 'read' },
  { method: 'GET', pattern: ['accounts', ':id', 'postings'], access: 'read' },
  { method: 'POST', pattern: ['accounts', ':id', 'reset'], access: 'admin' },
  { method: 'POST', pattern: ['accounts', ':id', 'adjust'], access: 'admin' },
  { method: 'GET', pattern: ['money-flow'], access: 'read' },
  { method: 'GET', pattern: ['money-flow', 'series'], access: 'read' },
  { method: 'GET', pattern: ['clients'], access: 'read' },
  { method: 'POST', pattern: ['clients'], access: 'admin' },
  { method: 'PUT', pattern: ['clients', ':clientId', 'grants'], access: 'admin' },
  { method: 'GET', pattern: ['currencies'], access: 'read' },
  { method: 'POST', pattern: ['currencies'], access: 'admin' },
  { method: 'PUT', pattern: ['currencies', ':code', 'default'], access: 'admin' },
  { method: 'GET', pattern: ['staff-actions'], access: 'read' }
]

export function isWrite(method: string): boolean {
  return method !== 'GET'
}

/** The endpoint of a method and a path below /admin/api, or null. Static segments are tried before parameters. */
export function matchEndpoint(method: string, segments: readonly string[]): Endpoint | null {
  const candidates = ENDPOINTS
    .filter(endpoint => endpoint.method === method && endpoint.pattern.length === segments.length)
    .sort((a, b) => staticCount(b) - staticCount(a))
  for (const endpoint of candidates) {
    const matches = endpoint.pattern.every((part, index) => part.startsWith(':')
      ? segments[index] !== undefined && segments[index] !== ''
      : part === segments[index])
    if (matches) {
      return endpoint
    }
  }
  return null
}

function staticCount(endpoint: Endpoint): number {
  return endpoint.pattern.filter(part => !part.startsWith(':')).length
}

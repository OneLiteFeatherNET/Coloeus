import type { JWTVerifyGetKey } from 'jose'
import type { WebConfig } from './config'
import type { Sealer } from './crypto'
import type { SessionStore } from './store'

/** Structured log line. Fields never carry a token, a cookie, a CSRF token or the session secret. */
export type Log = (event: string, fields?: Record<string, string | number | null>) => void

/** Everything the BFF touches from outside. Tests replace each member with a fake. */
export interface StaffDeps {
  config: WebConfig
  /** Epoch milliseconds. */
  now: () => number
  fetch: typeof fetch
  /** Returns a signal that aborts after the given milliseconds. */
  signal: (milliseconds: number) => AbortSignal
  jwks: JWTVerifyGetKey
  store: SessionStore
  sealer: Sealer
  log: Log
}

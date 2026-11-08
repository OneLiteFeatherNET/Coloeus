import { createRemoteJWKSet } from 'jose'
import { createStorage } from 'unstorage'
import redisDriver from 'unstorage/drivers/redis'
import { loadConfig } from './config'
import { createSealer } from './crypto'
import type { Log, StaffDeps } from './deps'
import { tenantEndpoints } from './oidc'
import { SessionStore, type RawClient } from './store'

// Production wiring: the settings of the environment, Dragonfly through unstorage's redis driver (key base `staff`),
// the tenant key set and the global fetch. One instance per process; replicas share state only through Dragonfly.

const log: Log = (event, fields = {}) => {
  console.info(JSON.stringify({ level: 'info', event, ...fields }))
}

export function createRuntimeDeps(env: Record<string, string | undefined>): StaffDeps {
  const config = loadConfig(env)
  const driver = redisDriver({ url: config.storageUrl, base: 'staff' })
  const storage = createStorage({ driver })
  const raw = (driver as unknown as { getInstance: () => unknown }).getInstance() as RawClient
  return {
    config,
    now: () => Date.now(),
    fetch: globalThis.fetch,
    signal: milliseconds => AbortSignal.timeout(milliseconds),
    jwks: createRemoteJWKSet(new URL(tenantEndpoints(config.tenantId).jwks)),
    store: new SessionStore(storage, raw),
    sealer: createSealer(config.sessionSecret),
    log
  }
}

let runtime: StaffDeps | undefined

/** The process-wide dependencies, created on first use from the environment. */
export function runtimeDeps(): StaffDeps {
  runtime ??= createRuntimeDeps(process.env)
  return runtime
}

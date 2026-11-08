import type { Storage } from 'unstorage'

// Session store of the BFF. Keys are relative to the storage mount (`session:`, `login:`, `sid:`); with the redis
// driver mounted under the base `staff`, Dragonfly holds them as `staff:session:…`, `staff:login:…`, `staff:sid:…`.
//
// Two operations need atomicity across replicas and go through the raw Redis client when the driver exposes one:
// the one-time read of a login state (GETDEL) and the `sid` index (SADD). Without a raw client (tests, memory
// driver) both are emulated on the storage with a read-modify-write, which is correct for one process only.

export const KEY_PREFIX = 'staff:'

/** The subset of the ioredis client that the store uses. */
export interface RawClient {
  getdel: (key: string) => Promise<string | null>
  sadd: (key: string, member: string) => Promise<number>
  smembers: (key: string) => Promise<string[]>
  expire: (key: string, seconds: number) => Promise<number>
  del: (key: string) => Promise<number>
}

export class SessionStore {
  constructor(
    private readonly storage: Storage,
    private readonly raw?: RawClient
  ) {}

  /** Stores a JSON value. unstorage serializes it, so the same value comes back from every driver. */
  async putJson(key: string, value: unknown, ttlSeconds: number): Promise<void> {
    await this.storage.setItem(key, value as never, { ttl: Math.max(1, Math.ceil(ttlSeconds)) })
  }

  async getJson<T>(key: string): Promise<T | null> {
    const value = await this.storage.getItem<T>(key)
    return value === null || value === undefined ? null : value
  }

  async remove(key: string): Promise<void> {
    await this.storage.removeItem(key)
  }

  /** Reads a stored JSON value as text and deletes it in one step, so that it can be used once. */
  async takeOnce(key: string): Promise<string | null> {
    if (this.raw) {
      return this.raw.getdel(KEY_PREFIX + key)
    }
    const value = await this.storage.getItem(key)
    await this.storage.removeItem(key)
    if (value === null || value === undefined) {
      return null
    }
    return typeof value === 'string' ? value : JSON.stringify(value)
  }

  /** Adds a session key to the `sid` index. The index expires with the newest session of that `sid`. */
  async indexSession(sid: string, sessionKey: string, ttlSeconds: number): Promise<void> {
    const ttl = Math.max(1, Math.ceil(ttlSeconds))
    if (this.raw) {
      const setKey = `${KEY_PREFIX}sid:${sid}`
      await this.raw.sadd(setKey, sessionKey)
      await this.raw.expire(setKey, ttl)
      return
    }
    const members = await this.getJson<string[]>(`sid:${sid}`) ?? []
    if (!members.includes(sessionKey)) {
      members.push(sessionKey)
    }
    await this.putJson(`sid:${sid}`, members, ttl)
  }

  /** The session keys of a `sid`, relative to the mount. */
  async sessionKeysOf(sid: string): Promise<string[]> {
    if (this.raw) {
      return this.raw.smembers(`${KEY_PREFIX}sid:${sid}`)
    }
    return await this.getJson<string[]>(`sid:${sid}`) ?? []
  }

  async dropIndex(sid: string): Promise<void> {
    if (this.raw) {
      await this.raw.del(`${KEY_PREFIX}sid:${sid}`)
      return
    }
    await this.storage.removeItem(`sid:${sid}`)
  }
}

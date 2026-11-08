import { describe, expect, it } from 'vitest'
import { createStorage } from 'unstorage'
import { SessionStore, type RawClient } from '../../../server/lib/store'

function memoryStore() {
  const storage = createStorage()
  return { storage, store: new SessionStore(storage) }
}

describe('SessionStore.takeOnce', () => {
  it('returns a stored login state the first time only', async () => {
    const { store } = memoryStore()
    await store.putJson('login:state-1', { nonce: 'n' }, 600)

    expect(await store.takeOnce('login:state-1')).toBe('{"nonce":"n"}')
    expect(await store.takeOnce('login:state-1')).toBeNull()
  })

  it('returns null for a state that was never stored', async () => {
    const { store } = memoryStore()

    expect(await store.takeOnce('login:unknown')).toBeNull()
  })

  it('uses GETDEL of the raw client when the driver exposes one', async () => {
    const calls: string[] = []
    const raw: RawClient = {
      getdel: async (key) => {
        calls.push(`getdel ${key}`)
        return 'stored'
      },
      sadd: async () => 0,
      smembers: async () => [],
      expire: async () => 1,
      del: async () => 0
    }
    const store = new SessionStore(createStorage(), raw)

    expect(await store.takeOnce('login:state-2')).toBe('stored')
    expect(calls).toEqual(['getdel staff:login:state-2'])
  })
})

describe('SessionStore sid index', () => {
  it('lists the session keys of one sid', async () => {
    const { store } = memoryStore()
    await store.indexSession('sid-a', 'session:1', 3600)
    await store.indexSession('sid-a', 'session:2', 3600)
    await store.indexSession('sid-b', 'session:3', 3600)

    expect(await store.sessionKeysOf('sid-a')).toEqual(['session:1', 'session:2'])
  })

  it('does not list a key twice when the same session is indexed again', async () => {
    const { store } = memoryStore()
    await store.indexSession('sid-a', 'session:1', 3600)
    await store.indexSession('sid-a', 'session:1', 3600)

    expect(await store.sessionKeysOf('sid-a')).toEqual(['session:1'])
  })

  it('forgets the index of a sid when it is dropped', async () => {
    const { store } = memoryStore()
    await store.indexSession('sid-a', 'session:1', 3600)
    await store.dropIndex('sid-a')

    expect(await store.sessionKeysOf('sid-a')).toEqual([])
  })

  it('keeps the index in the raw client when one exists', async () => {
    const sets = new Map<string, Set<string>>()
    const raw: RawClient = {
      getdel: async () => null,
      sadd: async (key, member) => {
        const set = sets.get(key) ?? new Set<string>()
        set.add(member)
        sets.set(key, set)
        return 1
      },
      smembers: async key => [...(sets.get(key) ?? [])],
      expire: async () => 1,
      del: async key => (sets.delete(key) ? 1 : 0)
    }
    const store = new SessionStore(createStorage(), raw)

    await store.indexSession('sid-r', 'session:9', 3600)

    expect(await store.sessionKeysOf('sid-r')).toEqual(['session:9'])
    expect([...sets.keys()]).toEqual(['staff:sid:sid-r'])
  })
})

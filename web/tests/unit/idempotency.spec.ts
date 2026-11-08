import { describe, expect, it } from 'vitest'
import { createSubmitKey } from '~/utils/idempotency'

function counterKeys(): () => string {
  let next = 0
  return () => {
    next += 1
    return `key-${next}`
  }
}

describe('submit key', () => {
  it('sends the same key again for a retry of the same request', () => {
    const submit = createSubmitKey(counterKeys())
    const first = submit.forRequest('{"reason":"Dupe"}')
    const retry = submit.forRequest('{"reason":"Dupe"}')
    expect(retry).toBe(first)
  })

  it('sends a new key for a changed request, so a different body is never sent with an old key', () => {
    const submit = createSubmitKey(counterKeys())
    const first = submit.forRequest('{"reason":"Dupe"}')
    const changed = submit.forRequest('{"reason":"Other"}')
    expect(changed).not.toBe(first)
  })

  it('sends a new key after a reset, so the next dialog opening is a new request', () => {
    const submit = createSubmitKey(counterKeys())
    const first = submit.forRequest('{"reason":"Dupe"}')
    submit.reset()
    const next = submit.forRequest('{"reason":"Dupe"}')
    expect(next).not.toBe(first)
  })
})

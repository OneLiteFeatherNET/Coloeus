// A dialog keeps one Idempotency-Key for every retry of the same submit. A changed body is a new request, so
// it gets a new key; reusing the old key for it would be answered with IDEMPOTENCY_CONFLICT.

export interface SubmitKey {
  /** The key for a request with this fingerprint (the serialized body). Retries with the same fingerprint get the same key. */
  forRequest: (fingerprint: string) => string
  /** Forgets the key, for example when the dialog opens or a write succeeded. */
  reset: () => void
}

export function createSubmitKey(newKey: () => string = () => globalThis.crypto.randomUUID()): SubmitKey {
  let key: string | undefined
  let fingerprint: string | undefined
  return {
    forRequest(next: string) {
      if (key === undefined || fingerprint !== next) {
        key = newKey()
        fingerprint = next
      }
      return key
    },
    reset() {
      key = undefined
      fingerprint = undefined
    }
  }
}

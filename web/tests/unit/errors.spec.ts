import { describe, expect, it } from 'vitest'
import { errorMessage, isSessionRequired } from '~/utils/errors'
import { ApiError } from '~/utils/staffApi'

describe('errorMessage', () => {
  it('shows the German text of a nothing-to-reset refusal', () => {
    expect(errorMessage(new ApiError(409, 'NOTHING_TO_RESET'))).toBe('Dieses Konto hat nichts zurückzusetzen.')
  })

  it('shows the German text of an insufficient-funds refusal', () => {
    expect(errorMessage(new ApiError(422, 'INSUFFICIENT_FUNDS'))).toBe('Das Guthaben reicht für diese Abbuchung nicht.')
  })

  it('shows the German text of a scale refusal', () => {
    expect(errorMessage(new ApiError(422, 'SCALE_EXCEEDED'))).toBe('Der Betrag hat zu viele Nachkommastellen.')
  })

  it('falls back to the generic text for a code without a message', () => {
    expect(errorMessage(new ApiError(418, 'SOMETHING_NEW'))).toBe('Die Anfrage ist fehlgeschlagen.')
  })

  it('falls back to the generic text for a failure that is not an API error', () => {
    expect(errorMessage(new Error('boom'))).toBe('Die Anfrage ist fehlgeschlagen.')
  })
})

describe('isSessionRequired', () => {
  it('is true for an expired session', () => {
    expect(isSessionRequired(new ApiError(401, 'SESSION_REQUIRED'))).toBe(true)
  })

  it('is false for a forbidden write', () => {
    expect(isSessionRequired(new ApiError(403, 'FORBIDDEN_ROLE'))).toBe(false)
  })
})

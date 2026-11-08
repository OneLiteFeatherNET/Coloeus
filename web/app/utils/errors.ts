import { de } from '~/i18n/de'
import { ApiError } from '~/utils/staffApi'

/** The German text for a failed call. Unknown codes fall back to the generic text. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return de.errors[error.code] ?? de.errors.UNKNOWN!
  }
  return de.errors.UNKNOWN!
}

export function isSessionRequired(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'SESSION_REQUIRED'
}
